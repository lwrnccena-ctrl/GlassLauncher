package com.fares.glass

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository
import com.fares.glass.data.Locator
import com.fares.glass.data.WifiLocator
import com.fares.glass.data.Weather
import com.fares.glass.data.WeatherRepository
import com.fares.glass.gfx.Blur
import com.fares.glass.gfx.SkyWallpaper
import com.fares.glass.gfx.Wallpapers
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.SettingsShortcuts
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.WifiController
import com.fares.glass.ui.Backdrop
import com.fares.glass.ui.Command
import com.fares.glass.ui.ControlCenter
import com.fares.glass.ui.HomeScreen
import com.fares.glass.ui.HomeState
import com.fares.glass.ui.Mode
import com.fares.glass.ui.Recents
import com.fares.glass.ui.Typo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    companion object {
        /** True while the launcher is on screen. The overlay service uses it to stay out of the way. */
        @Volatile var isForeground = false
    }

    private lateinit var repo: AppRepository
    private val home = HomeState()
    private val backdrop = Backdrop()
    private val wallpaper = mutableStateOf<ImageBitmap?>(null)
    private val wallpaperName = mutableStateOf("")
    private val raining = mutableStateOf(false)
    // True while the "Auto" sky wallpaper (the one with the TV) is selected: lyrics go on its TV screen.
    private val skyWallpaper = mutableStateOf(false)
    // Starts as a placeholder so the text shows "--°C" until the first real answer arrives.
    private val weatherState = mutableStateOf<Weather?>(Weather.Placeholder)
    private lateinit var cc: ControlCenter
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cc.onPermissionsResult()
    }

    // Weather and location (defaults: Haligue Silangan; override with --es lat / --es lon over ADB).
    private var weather: Weather? = null
    private var lat = WeatherRepository.DEFAULT_LAT
    private var lon = WeatherRepository.DEFAULT_LON
    // The location is found automatically: first from the Wi-Fi networks around (data/WifiLocator.kt), then from
    // the TV's internet address (data/Locator.kt). A city chosen by hand in the city picker wins over both.
    // detectedAt = when it was last found (0 = not found yet).
    private var detectedAt = 0L
    private var manualLocation = false
    // Name and time zone of the detected place.
    private var city = ""
    private var zoneId = "Asia/Manila"
    private var skyKey = ""
    // When Home was last pressed: two presses close together open the recent apps.
    private var lastHomeAt = 0L

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(repo::invalidate)
            lifecycleScope.launch { repo.refresh() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        Typo.load(this)
        Recents.load(this)
        intent.getStringExtra("name")?.let(repo::setName)
        loadLocation()
        cc = ControlCenter(
            this, repo, WifiController(this), BluetoothController(this), SystemInfo(this), SettingsShortcuts(this),
            onOpen = ::startSafely,
            onRequestPermissions = { permissions.launch(it) },
            onNextWallpaper = { run(Command.NextWallpaper) },
            onClose = { home.mode = Mode.Browse },
        )
        // A home screen never closes on Back.
        onBackPressedDispatcher.addCallback(this) {}
        registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        setContent {
            // The layout is designed for a 960 dp wide screen (a 1080p Fire TV). TVs report all kinds of
            // densities, so scale it to fill the screen whatever the resolution and dpi.
            val width = resources.displayMetrics.widthPixels
            val font = LocalDensity.current.fontScale
            CompositionLocalProvider(LocalDensity provides Density(width / 960f, font)) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        repo, home, backdrop, wallpaper.value, wallpaperName.value, cc,
                        onLaunch = ::launch, onCommand = ::run,
                        raining = raining.value,
                        weather = weatherState.value,
                        tvWallpaper = skyWallpaper.value,
                        onSetLocation = ::setManualLocation,
                    )
                }
            }
        }
        loadWallpaper()

        // Looks on GitHub for a newer version of Glass and offers to install it.
        UpdateChecker.check(this)

        // Refresh the weather every 10 minutes. If a request fails (for example the Wi-Fi is not up yet
        // after boot), try again after 30 seconds instead of waiting the full 10 minutes.
        lifecycleScope.launch {
            while (true) {
                detectLocation()
                val ok = refreshWeather()
                // Without internet try again soon; if only the location is missing, wait a little longer.
                delay(if (!ok) 30_000L else if (detectedAt == 0L) 5 * 60_000L else 10 * 60_000L)
            }
        }

        // Check the time of day every minute, so the sky wallpaper switches close to the exact moment
        // (the weather itself is only refreshed every 10 minutes).
        lifecycleScope.launch {
            while (true) {
                delay(60_000L)
                if (isAutoSelected()) applySky(force = false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        // Closes the overlay over other apps when the launcher comes back (for example Home pressed).
        GlassOverlayService.dismiss()
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
        if (home.mode == Mode.Control) cc.close()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("name")?.let(repo::setName)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // A second press of Home shortly after the first opens the recent apps. Presses closer than
            // 150 ms are ignored, in case the TV delivers one press twice.
            val now = SystemClock.elapsedRealtime()
            val gap = now - lastHomeAt
            if (gap in 150L..900L && !Recents.open) {
                Recents.open = true
            } else if (gap >= 150L) {
                Recents.open = false
                home.goHome()
            }
            if (gap >= 150L) lastHomeAt = now
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    // ---- Wallpaper ----

    /** The last entry in the wallpaper list is "Auto": sky by time of day, plus rain. */
    private fun isAutoSelected(): Boolean {
        val count = Wallpapers.count(this)
        return repo.wallpaperIndex % (count + 1) == count
    }

    private fun loadWallpaper() {
        skyWallpaper.value = isAutoSelected()
        if (isAutoSelected()) {
            applySky(force = true)
            return
        }
        raining.value = false
        skyKey = ""
        val index = repo.wallpaperIndex % (Wallpapers.count(this) + 1)
        lifecycleScope.launch {
            val (sharp, frosted) = withContext(Dispatchers.Default) {
                val src = Wallpapers.load(this@MainActivity, index)
                // ~480 px wide with sigma 10: like a 40 px blur at 1080p, and very cheap.
                src to Blur.frosted(src, 480, 10f, 1.45f, 1.0f)
            }
            wallpaper.value = sharp.asImageBitmap()
            backdrop.frosted = frosted.asImageBitmap()
            wallpaperName.value = Wallpapers.name(this@MainActivity, index)
        }
    }

    private fun applySky(force: Boolean) {
        val w = weather
        val now = System.currentTimeMillis()
        // SkyWallpaper counts the day in Manila time. Moving every time by the difference between Manila
        // and the TV's location makes it see the local day, wherever the TV is (0 in the Philippines).
        val skew = WeatherRepository.skewMs(w?.zone ?: zoneId, now)
        val shiftedNow = now + skew
        val (fallbackRise, fallbackSet) = WeatherRepository.fallbackTimes(shiftedNow)
        val phase = SkyWallpaper.phase(
            shiftedNow, w?.sunrise?.plus(skew) ?: fallbackRise, w?.sunset?.plus(skew) ?: fallbackSet,
        )
        val rain = w?.isRaining == true
        wallpaperName.value = "Auto (sky & weather)"

        val key = "$phase-$rain"
        if (!force && key == skyKey) return
        skyKey = key
        raining.value = rain

        lifecycleScope.launch {
            val (sharp, frosted) = withContext(Dispatchers.Default) {
                val src = SkyWallpaper.render(phase, rain)
                src to Blur.frosted(src, 480, 10f, 1.45f, 1.0f)
            }
            if (!isAutoSelected()) return@launch // the user switched wallpaper meanwhile
            wallpaper.value = sharp.asImageBitmap()
            backdrop.frosted = frosted.asImageBitmap()
        }
    }

    // ---- Weather ----

    /** Returns true if the weather was fetched successfully. */
    private suspend fun refreshWeather(): Boolean {
        val result = WeatherRepository.fetch(lat, lon)?.copy(place = placeName())
        if (result != null) {
            weather = result
            weatherState.value = result
            zoneId = result.zone
        }
        if (isAutoSelected()) applySky(force = false)
        return result != null
    }

    /**
     * The name shown above the weather: the detected city. Empty = unknown, and the widget then says
     * "Location unavailable".
     */
    private suspend fun placeName(): String {
        // Not found yet: the weather is for the default place, so no name is shown.
        if (detectedAt == 0L) return ""
        if (city.isNotBlank()) return city
        // The internet address gave coordinates but no city name: look the name up from the coordinates.
        val name = Locator.cityFor(lat, lon).orEmpty()
        if (name.isNotBlank()) {
            city = name
            getSharedPreferences("glass_location", Context.MODE_PRIVATE).edit().putString("auto_city", name).apply()
        }
        return name
    }

    /** Takes the last place that was found or chosen (saved on the TV), so the weather is right straight after start-up. */
    private fun loadLocation() {
        val prefs = getSharedPreferences("glass_location", Context.MODE_PRIVATE)
        manualLocation = prefs.getBoolean("manual", false)
        val k = if (manualLocation) "manual" else "auto"
        prefs.getString("${k}_lat", null)?.toDoubleOrNull()?.let { lat = it }
        prefs.getString("${k}_lon", null)?.toDoubleOrNull()?.let { lon = it }
        detectedAt = if (manualLocation) 1L else prefs.getLong("auto_at", 0L)
        city = prefs.getString("${k}_city", "").orEmpty()
        zoneId = prefs.getString("${k}_zone", "Asia/Manila") ?: "Asia/Manila"
    }

    /**
     * Finds the TV's location (at most every 6 hours unless forced): first Android's Wi-Fi location,
     * which is the most exact, then the TV's internet address. Does nothing when a city was chosen by hand.
     */
    private suspend fun detectLocation(force: Boolean = false) {
        if (manualLocation) return
        val now = System.currentTimeMillis()
        if (!force && detectedAt != 0L && now - detectedAt < 6 * 60 * 60_000L) return
        val fix = WifiLocator.find(this)
        val place = if (fix != null) {
            Locator.Place(fix.lat, fix.lon, Locator.cityFor(fix.lat, fix.lon).orEmpty(), TimeZone.getDefault().id)
        } else {
            Locator.detect()
        } ?: return
        lat = place.lat
        lon = place.lon
        detectedAt = now
        city = place.city
        zoneId = place.zone
        getSharedPreferences("glass_location", Context.MODE_PRIVATE).edit()
            .putString("auto_lat", place.lat.toString())
            .putString("auto_lon", place.lon.toString())
            .putString("auto_city", place.city)
            .putString("auto_zone", place.zone)
            .putLong("auto_at", now)
            .apply()
    }

    /** A city was chosen in the city picker (null = go back to the automatic location). */
    private fun setManualLocation(place: Locator.Place?) {
        val prefs = getSharedPreferences("glass_location", Context.MODE_PRIVATE)
        if (place == null) {
            manualLocation = false
            detectedAt = 0L
            city = ""
            prefs.edit().putBoolean("manual", false).apply()
        } else {
            manualLocation = true
            lat = place.lat
            lon = place.lon
            city = place.city
            zoneId = place.zone
            detectedAt = 1L
            prefs.edit()
                .putBoolean("manual", true)
                .putString("manual_lat", place.lat.toString())
                .putString("manual_lon", place.lon.toString())
                .putString("manual_city", place.city)
                .putString("manual_zone", place.zone)
                .apply()
        }
        lifecycleScope.launch {
            detectLocation(force = true)
            refreshWeather()
        }
    }

    // ---- Launching ----

    /** from = where the system animation starts; null = no system animation (the launcher drew its own). */
    private fun launch(entry: AppEntry, from: Rect?) {
        if (entry.pkg != packageName) Recents.push(entry.pkg)
        val options = if (from != null) {
            ActivityOptions.makeScaleUpAnimation(window.decorView, from.left, from.top, from.width(), from.height())
        } else {
            ActivityOptions.makeCustomAnimation(this, 0, 0)
        }
        try {
            startActivity(entry.launchIntent(), options.toBundle())
        } catch (e: Exception) {
            Log.w("Glass", "Can't launch ${entry.pkg}", e)
            packageManager.getLaunchIntentForPackage(entry.pkg)?.let { startSafely(it) }
        }
    }

    private fun run(command: Command) {
        when (command) {
            Command.Settings -> startSafely(Intent(Settings.ACTION_SETTINGS))
            Command.Network -> startSafely(Intent(Settings.ACTION_WIFI_SETTINGS))
            Command.Bluetooth -> startSafely(Intent("android.settings.CONTROLLERS_SETTINGS"))
            Command.Apps -> startSafely(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            is Command.AppInfo -> startSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", command.pkg, null)),
            )
            Command.NextWallpaper -> {
                repo.setWallpaper((repo.wallpaperIndex + 1) % (Wallpapers.count(this) + 1))
                loadWallpaper()
            }
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("Glass", "No activity for $intent", e)
            if (intent.action != Settings.ACTION_SETTINGS) startSafely(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
