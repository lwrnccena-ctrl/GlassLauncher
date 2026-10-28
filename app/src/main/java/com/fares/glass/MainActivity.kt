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

class MainActivity : ComponentActivity() {

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
        readLocation(intent)
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
                    )
                }
            }
        }
        loadWallpaper()

        // Refresh the weather every 10 minutes. If a request fails (for example the Wi-Fi is not up yet
        // after boot), try again after 30 seconds instead of waiting the full 10 minutes.
        lifecycleScope.launch {
            while (true) {
                val ok = refreshWeather()
                delay(if (ok) 10 * 60_000L else 30_000L)
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
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onPause() {
        super.onPause()
        if (home.mode == Mode.Control) cc.close()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("name")?.let(repo::setName)
        if (readLocation(intent)) lifecycleScope.launch { refreshWeather() }
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
        val (fallbackRise, fallbackSet) = WeatherRepository.fallbackTimes()
        val phase = SkyWallpaper.phase(
            System.currentTimeMillis(), w?.sunrise ?: fallbackRise, w?.sunset ?: fallbackSet,
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
        val result = WeatherRepository.fetch(lat, lon)
        if (result != null) {
            weather = result
            weatherState.value = result
        }
        if (isAutoSelected()) applySky(force = false)
        return result != null
    }

    private fun loadLocation() {
        val prefs = getSharedPreferences("glass_location", Context.MODE_PRIVATE)
        prefs.getString("lat", null)?.toDoubleOrNull()?.let { lat = it }
        prefs.getString("lon", null)?.toDoubleOrNull()?.let { lon = it }
    }

    /** Reads --es lat / --es lon from an intent. Returns true if the location changed. */
    private fun readLocation(i: Intent): Boolean {
        val newLat = i.getStringExtra("lat")?.toDoubleOrNull() ?: return false
        val newLon = i.getStringExtra("lon")?.toDoubleOrNull() ?: return false
        lat = newLat
        lon = newLon
        getSharedPreferences("glass_location", Context.MODE_PRIVATE).edit()
            .putString("lat", newLat.toString())
            .putString("lon", newLon.toString())
            .apply()
        return true
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
