package com.fares.glass.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Asks Android for a location based on the Wi-Fi networks around (the "network" location provider).
 * It can be accurate to a few hundred metres, much better than the internet address. It needs the
 * location permission (adb shell pm grant com.fares.glass android.permission.ACCESS_FINE_LOCATION),
 * Location switched on in the TV's settings, and a TV that has a location service (Google's).
 */
object WifiLocator {

    class Fix(val lat: Double, val lon: Double)

    @SuppressLint("MissingPermission")
    suspend fun find(context: Context): Fix? {
        val app = context.applicationContext
        val granted =
            app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.i("Glass", "Wi-Fi location: no location permission")
            return null
        }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = LocationManager.NETWORK_PROVIDER
        try {
            if (provider !in lm.allProviders) {
                Log.i("Glass", "Wi-Fi location: this TV has no network location provider")
                return null
            }
            if (!lm.isProviderEnabled(provider)) {
                Log.i("Glass", "Wi-Fi location: Location is switched off in the TV's settings")
                return null
            }
            // A recent answer is fine.
            lm.getLastKnownLocation(provider)?.let {
                if (System.currentTimeMillis() - it.time < 24 * 60 * 60_000L) return Fix(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            Log.w("Glass", "Wi-Fi location failed", e)
            return null
        }
        return withTimeoutOrNull(15_000L) { newFix(lm, provider, app) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun newFix(lm: LocationManager, provider: String, app: Context): Fix? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, app.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc?.let { Fix(it.latitude, it.longitude) })
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(Fix(location.latitude, location.longitude))
                        }
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                Log.w("Glass", "Wi-Fi location failed", e)
                if (cont.isActive) cont.resume(null)
            }
        }
}
