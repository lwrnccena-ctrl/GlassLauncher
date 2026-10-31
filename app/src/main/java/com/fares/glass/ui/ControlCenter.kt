package com.fares.glass.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent as AndroidKey
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.R
import com.fares.glass.data.AppRepository
import com.fares.glass.gfx.CustomIcons
import com.fares.glass.gfx.Wallpapers
import com.fares.glass.sys.AdbController
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.BtKind
import com.fares.glass.sys.SettingsShortcuts
import com.fares.glass.sys.SettingsShortcuts.Section
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.WifiController
import com.fares.glass.sys.WifiNet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class CcPage { Main, Wifi, Bluetooth, Adb, Widgets, Clock, Icons, Wallpapers }

/** Narrow, tall sheet, like the tvOS Control Center. */
val PanelWidth = 300.dp
val PanelHeight = 516.dp

/** Where the panel sits on the screen (HomeScreen places it with these, the animation needs them too). */
val PanelTop = 12.dp
val PanelEnd = 24.dp

// ---- Open / close animation: the panel and its tiles grow out of the top-right corner ----

/** Opening takes this long in total (panel + the last tile to arrive). Closing is much faster. */
const val CcOpenMs = 720
const val CcCloseMs = 260

private const val PanelDelayMs = 30f
private const val PanelGrowMs = 460f
private const val PanelFadeDelayMs = 40f
private const val PanelFadeMs = 240f
private const val TileDelayMs = 60f
private const val TileStaggerMs = 26f
private const val TileGrowMs = 420f
private const val TileFadeMs = 220f
private const val StartScale = 0.15f
private const val PanelStartScale = 0.3f

/** Slight settle at the end, like the second preview. */
private val GrowEase = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1.12f)
private val PanelEase = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1.08f)
private val FadeEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Progress of the open / close animation: 0 -> 1 when opening, 1 -> 0 when closing.
 * It is read only while drawing, so the animation never recomposes the screen.
 */
class CcReveal(val progress: () -> Float, val opening: () -> Boolean)

/** Local progress (0..1, eased) of one piece of the timeline. [t] is the overall progress. */
private fun stage(t: Float, delayMs: Float, durMs: Float, ease: Easing): Float =
    ease.transform(((t * CcOpenMs - delayMs) / durMs).coerceIn(0f, 1f))

/** The content area of the panel: the top-right corner of this box is the point everything grows out of. */
private class CornerHost {
    var coords by mutableStateOf<LayoutCoordinates?>(null)
}

private class OwnCoords {
    var value by mutableStateOf<LayoutCoordinates?>(null)
}

/**
 * True when there is no blurred wallpaper behind the Control Center (for example the Top Shelf
 * video is showing). Then the big panel is not drawn and every tile gets a milky frosted fill.
 */
private val LocalClear = compositionLocalOf { false }

/** Resting fill of a tile: the normal faint white, or a milky white when there is no panel behind it. */
private fun restFill(clear: Boolean, normal: Float): Color = White.copy(alpha = if (clear) 0.28f else normal)

/** Thin light rim around a tile so it stands out over a video. Fades out when the tile is focused. */
private fun Modifier.glassEdge(clear: Boolean, focus: Float, shape: Shape): Modifier =
    if (clear) border(1.dp, White.copy(alpha = 0.45f * (1f - focus)), shape) else this

/** Soft dark shadow behind text so it stays readable over a bright video. */
private fun TextStyle.shade(on: Boolean): TextStyle =
    if (on) copy(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 4f)) else this

/**
 * Makes this element grow out of the panel's top-right corner (scale + fade), later for elements
 * that are farther along [order]. Only scale and alpha are animated, so it stays smooth on a TV.
 */
@Composable
private fun Modifier.revealFromCorner(host: CornerHost, reveal: CcReveal, order: Int): Modifier {
    val own = remember { OwnCoords() }
    return this
        // This sits outside the graphicsLayer below, so its position ignores the animation.
        .onGloballyPositioned { own.value = it }
        .graphicsLayer {
            val t = reveal.progress()
            val scale: Float
            val fade: Float
            if (reveal.opening()) {
                val delay = TileDelayMs + order * TileStaggerMs
                scale = StartScale + (1f - StartScale) * stage(t, delay, TileGrowMs, GrowEase)
                fade = stage(t, delay, TileFadeMs, FadeEase)
            } else {
                scale = StartScale + (1f - StartScale) * FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
                fade = t
            }
            // Pivot = the panel's top-right corner, expressed in this element's own size.
            val h = host.coords
            val c = own.value
            if (h != null && c != null && h.isAttached && c.isAttached && c.size.width > 0 && c.size.height > 0) {
                val p = h.localPositionOf(c, Offset.Zero)
                transformOrigin = TransformOrigin(
                    (h.size.width - p.x) / c.size.width,
                    -p.y / c.size.height,
                )
            }
            scaleX = scale
            scaleY = scale
            alpha = fade.coerceIn(0f, 1f)
        }
}

private val Ink = Color(0xFF111114)
private val White = Color.White
private val WifiBlue = Color(0xFF0A84FF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Purple = Color(0xFFBF5AF2)

/**
 * Selection numbers on the main page (the numbers 0-5 match the order of the list in tiles()):
 *
 *   Wi-Fi       | Bluetooth
 *   Icon pack   | brightness + volume (two vertical sliders, two rows tall)
 *   Hidden apps | (same sliders)
 *   Wallpaper   | Animations
 *   Widgets tile | Clock tile (two half-width icon-only tiles)
 *   round shortcuts
 *
 * ADB_BTN is the round ADB button at the top right, next to the clock.
 */
private const val WIFI = 0
private const val ANIMATIONS = 1
private const val ICONS = 2
private const val HIDDEN = 3
private const val BLUETOOTH = 4
private const val WALLPAPER = 5
private const val BRIGHTNESS = 6
private const val VOLUME = 7
private const val WIDGETS = 8
private const val FIRST_SHORTCUT = 9
private const val ADB_BTN = 100
private const val CLOCK_BTN = 101

private val SunYellow = Color(0xFFFFD60A)

/** One row of a detail page (Wi-Fi, Bluetooth, ADB, Icon pack). */
private sealed interface Line {
    val focusable: Boolean get() = true
    /** Stable identity: focus follows the item even when the list changes (device found, scan…). */
    val key: String
    data class Toggle(val title: String, val on: Boolean) : Line { override val key get() = "toggle" }
    data class Header(val title: String) : Line { override val focusable get() = false; override val key get() = "header:$title" }
    data class Network(val net: WifiNet) : Line { override val key get() = "net:${net.ssid}" }
    data class Device(val address: String, val name: String, val subtitle: String, val icon: ImageVector, val bonded: Boolean, val connected: Boolean) : Line {
        override val key get() = "dev:$address"
    }
    data class Action(val title: String, val icon: ImageVector, val id: String = "action") : Line { override val key get() = "action:$id" }
    /** An ADB permission row: shows a check once it is granted. */
    data class Cmd(val id: String, val title: String, val subtitle: String, val done: Boolean) : Line { override val key get() = "cmd:$id" }
    data class Link(val title: String, val section: Section) : Line { override val key get() = "link" }
    data class Note(val text: String) : Line { override val focusable get() = false; override val key get() = "note:$text" }
    /** An icon pack choice. pkg = null means "Default" (the apps' own banners and logos). */
    data class Pack(val pkg: String?, val title: String, val subtitle: String, val selected: Boolean) : Line {
        override val key get() = "pack:${pkg ?: "default"}"
    }
    /** A wallpaper choice. index matches AppRepository.wallpaperIndex (the last one is "Auto"). */
    data class Wall(val index: Int, val title: String, val selected: Boolean, val subtitle: String = "") : Line {
        override val key get() = "wall:$index"
    }
}

private class Tile(val icon: ImageVector, val title: String, val status: String, val active: Boolean, val accent: Color)

/** One tile of the Widgets page (second control center). */
private class WidgetItem(val icon: ImageVector, val title: String, val on: Boolean)

private val Section.icon: ImageVector
    get() = when (this) {
        Section.Display -> Icons.Rounded.Tv
        Section.Devices -> Icons.Rounded.SettingsRemote
        Section.Apps -> Icons.Rounded.Apps
        Section.Accessibility -> Icons.Rounded.Accessibility
        Section.About -> Icons.Rounded.Info
        Section.Network -> Icons.Rounded.Wifi
        Section.All -> Icons.Rounded.Settings
    }

/**
 * tvOS-style Control Center: a grid of modules, Widgets and Clock tiles that open extra pages, round
 * shortcuts, and Wi-Fi / Bluetooth / ADB / Widgets / Clock / Icon pack pages that slide inside the panel.
 * All remote navigation for it lives here.
 */
class ControlCenter(
    private val context: Context,
    private val repo: AppRepository,
    val wifi: WifiController,
    val bt: BluetoothController,
    val sys: SystemInfo,
    private val settings: SettingsShortcuts,
    private val onOpen: (Intent) -> Unit,
    /** Asks the user for missing runtime permissions (location, and Bluetooth on recent Android). */
    private val onRequestPermissions: (Array<String>) -> Unit,
    private val onNextWallpaper: () -> Unit,
    private val onClose: () -> Unit,
) {
    init {
        // Reads the saved verse / music / lyrics switches.
        Features.load(context)
    }

    /** Brightness and volume for the two sliders. */
    private val levels = Levels(context)

    var page by mutableStateOf(CcPage.Main)
    var index by mutableIntStateOf(0)
    /** Selected tile on the Widgets page: 0 verse, 1 music, 2 lyrics, 3 weather. */
    var widgetIndex by mutableIntStateOf(0)
    /** Selected style on the Clock page (see ClockStyle). */
    var clockIndex by mutableIntStateOf(0)
    /** Selected item on a detail page, identified by its key. */
    var rowKey by mutableStateOf("toggle")
    /** Network whose password is being typed (the on-screen keyboard is open). */
    var passwordFor by mutableStateOf<WifiNet?>(null)
    var password by mutableStateOf("")
    private var forgetArmed: String? = null
    /** Bumped to re-read memory, storage and animations. */
    var tick by mutableIntStateOf(0)

    // ---- Icon pack state ----
    /** The icon pack in use (package name), or null for the default. MainActivity loads it before this class is created. */
    private var iconPack by mutableStateOf(CustomIcons.selectedPack())
    /** Installed icon packs (package -> label), read when the Icon pack page opens. */
    private var packs: Map<String, String> = emptyMap()

    // ---- Wallpaper page state ----
    /** Names of all wallpapers, read when the Wallpaper page opens. The last entry is "Auto". */
    private var wallpaperNames: List<String> = emptyList()

    // ---- ADB state ----
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var adbStatus by mutableStateOf("")
    var adbConnected by mutableStateOf(false)
    private var adbBusy = false
    private var adbTick by mutableIntStateOf(0)
    /** 0 = not typing, 1 = typing the pairing port, 2 = typing the 6-digit code, 3 = typing the TV's IP address. */
    var pairStep by mutableIntStateOf(0)
    var pairText by mutableStateOf("")
    private var pairPort = 0

    val typing get() = passwordFor != null || pairStep != 0

    /** Round shortcuts: sections this TV really lets Glass open (up to 4), plus "All settings". */
    private val shortcuts: List<Section> by lazy {
        listOf(Section.Display, Section.Devices, Section.Apps, Section.Accessibility, Section.About)
            .filter(settings::available).take(4) + Section.All
    }

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun open(section: Section) {
        val intent = settings.intent(section) ?: settings.intent(Section.All) ?: return
        onOpen(intent)
    }

    private fun askPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter { sys.missing(it) }
        if (wanted.isNotEmpty()) onRequestPermissions(wanted.toTypedArray())
    }

    /** After the user answers the permission requests. */
    fun onPermissionsResult() {
        wifi.refresh()
        wifi.scan()
        bt.refresh()
    }

    fun open(target: CcPage) {
        page = target
        index = when (target) {
            CcPage.Wifi -> 0
            CcPage.Bluetooth -> BLUETOOTH
            CcPage.Main -> 0
            CcPage.Adb -> 0
            CcPage.Widgets -> 0
            CcPage.Clock -> 0
            CcPage.Icons -> 0
            CcPage.Wallpapers -> 0
        }
        widgetIndex = 0
        clockIndex = repo.clockStyle
        rowKey = "toggle"
        wifi.start()
        bt.start()
        tick++
        levels.refresh()
    }

    fun close() {
        wifi.stop()
        bt.stop()
        passwordFor = null
        pairStep = 0
        pairText = ""
        onClose()
    }

    // ---- Icon pack ----

    private fun openIcons() {
        packs = CustomIcons.listPacks(context)
        rowKey = "pack:${iconPack ?: "default"}"
        page = CcPage.Icons
    }

    /** Saves the choice, loads the pack, and redraws every tile with it. */
    private fun setIconPack(pkg: String?) {
        iconPack = pkg
        context.getSharedPreferences("glass_icons", Context.MODE_PRIVATE)
            .edit().putString("icon_pack", pkg).apply()
        scope.launch {
            withContext(Dispatchers.Default) { CustomIcons.selectPack(context, pkg) }
            repo.rebuildTiles()
        }
    }

    // ---- Wallpaper list ----

    private fun openWallpapers() {
        val count = Wallpapers.count(context)
        wallpaperNames = List(count + 1) { i ->
            if (i == count) "Auto (sky & weather)"
            else runCatching { Wallpapers.name(context, i) }.getOrDefault("Wallpaper ${i + 1}")
        }
        rowKey = "wall:${repo.wallpaperIndex % (count + 1)}"
        page = CcPage.Wallpapers
    }

    /** Picks one wallpaper. MainActivity only knows "next", so step back one and press "next". */
    private fun setWallpaper(i: Int) {
        val n = Wallpapers.count(context) + 1
        if (repo.wallpaperIndex % n == i) return
        repo.setWallpaper((i - 1 + n) % n)
        onNextWallpaper()
    }

    private fun iconPackLabel(pkg: String?): String {
        if (pkg == null) return "Default"
        return packs[pkg] ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    // ---- ADB ----

    private fun adbSecure() =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private fun adbListener() =
        (Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: "")
            .contains(context.packageName)

    private fun adbLocation() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun openAdb() {
        page = CcPage.Adb
        rowKey = "action:connect"
        adbTick++
    }

    private fun adbConnect(host: String? = null) {
        if (adbBusy) return
        adbBusy = true
        adbStatus = "Connecting… (accept the prompt on the TV)"
        scope.launch {
            val r = AdbController.connect(context, host)
            adbConnected = r.ok
            adbStatus = if (r.ok) "Connected" else r.output
            adbBusy = false
            adbTick++
        }
    }

    private fun adbRun(command: String) {
        if (adbBusy) return
        adbBusy = true
        adbStatus = "Running…"
        scope.launch {
            val r = AdbController.run(context, command)
            adbConnected = adbConnected || r.ok
            adbStatus = if (r.ok) "Done" else r.output
            adbBusy = false
            adbTick++
        }
    }

    /** Called when OK / Done is pressed in the text field (IP address, pairing port or code). */
    fun submitPair() {
        if (pairStep == 3) {
            val ip = pairText.trim()
            pairText = ""
            pairStep = 0
            if (ip.isNotEmpty()) adbConnect(ip) else adbStatus = "Type the TV's IP address."
            return
        }
        val text = pairText.filter(Char::isDigit)
        pairText = ""
        if (pairStep == 1) {
            pairPort = text.toIntOrNull() ?: 0
            pairStep = if (pairPort > 0) 2 else 0
            return
        }
        pairStep = 0
        if (pairPort <= 0 || text.length != 6) {
            adbStatus = "Enter the port, then the 6-digit code."
            return
        }
        if (adbBusy) return
        adbBusy = true
        adbStatus = "Pairing…"
        scope.launch {
            val r = AdbController.pair(context, pairPort, text)
            adbConnected = r.ok
            adbStatus = if (r.ok) "Paired and connected" else r.output
            adbBusy = false
            adbTick++
        }
    }

    // ---- Main page ----

    private fun tiles(wallpaperName: String): List<Tile> {
        @Suppress("UNUSED_EXPRESSION") tick
        val anim = sys.animationScale()
        return listOf(
            Tile(if (wifi.enabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, "Wi-Fi",
                if (!wifi.enabled) str(R.string.cc_off) else wifi.current ?: str(R.string.cc_not_connected), wifi.enabled && wifi.current != null, WifiBlue),
            Tile(Icons.Rounded.Animation, str(R.string.cc_animations),
                if (!sys.canWriteSettings()) str(R.string.cc_anim_adb) else when (anim) {
                    1f -> str(R.string.cc_anim_normal)
                    0f -> str(R.string.cc_anim_off)
                    else -> str(R.string.cc_anim_fast, anim)
                },
                anim < 1f, Orange),
            Tile(Icons.Rounded.Palette, "Icon pack", iconPackLabel(iconPack), iconPack != null, Purple),
            Tile(Icons.Rounded.Visibility, str(R.string.cc_hidden_apps),
                if (repo.hiddenCount == 0) str(R.string.cc_hidden_none) else str(R.string.cc_hidden_count, repo.hiddenCount),
                repo.hiddenCount > 0, Green),
            Tile(if (bt.enabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, "Bluetooth",
                when {
                    !bt.available -> str(R.string.cc_unavailable)
                    !bt.enabled -> str(R.string.cc_off)
                    else -> bt.paired.count { it.connected }.let {
                        if (it == 0) str(R.string.cc_no_devices) else context.resources.getQuantityString(R.plurals.cc_connected_count, it, it)
                    }
                }, bt.enabled, WifiBlue),
            Tile(Icons.Rounded.Wallpaper, str(R.string.cc_wallpaper), wallpaperName, false, Purple),
        )
    }

    private fun selectMain() {
        when (index) {
            WIFI -> { page = CcPage.Wifi; rowKey = "toggle"; askPermissions(); wifi.scan() }
            ANIMATIONS -> { sys.cycleAnimations(); tick++ }
            ICONS -> openIcons()
            HIDDEN -> repo.unhideAll()
            BLUETOOTH -> { page = CcPage.Bluetooth; rowKey = "toggle"; askPermissions() }
            WALLPAPER -> openWallpapers()
            BRIGHTNESS -> Unit // Up / Down change it
            VOLUME -> levels.toggleMute()
            WIDGETS -> { page = CcPage.Widgets; widgetIndex = 0 }
            CLOCK_BTN -> { page = CcPage.Clock; clockIndex = repo.clockStyle }
            ADB_BTN -> openAdb()
            else -> open(shortcuts[index - FIRST_SHORTCUT])
        }
    }

    private fun moveMain(dx: Int, dy: Int) {
        val i = index
        // The ADB button sits above the first row of tiles.
        if (i == ADB_BTN) {
            if (dy > 0) index = BLUETOOTH else if (dx < 0) index = WIFI
            return
        }
        // The two sliders are vertical now: Up / Down change the value, Left / Right move the selection.
        if (dy != 0 && i == BRIGHTNESS) { levels.adjustBrightness(-dy); return }
        if (dy != 0 && i == VOLUME) { levels.adjustVolume(-dy); return }
        index = when (i) {
            WIFI -> when {
                dx > 0 -> BLUETOOTH
                dy > 0 -> ICONS
                dy < 0 -> ADB_BTN
                else -> i
            }
            BLUETOOTH -> when {
                dx < 0 -> WIFI
                dy > 0 -> BRIGHTNESS
                dy < 0 -> ADB_BTN
                else -> i
            }
            ICONS -> when {
                dx > 0 -> BRIGHTNESS
                dy < 0 -> WIFI
                dy > 0 -> HIDDEN
                else -> i
            }
            HIDDEN -> when {
                dx > 0 -> BRIGHTNESS
                dy < 0 -> ICONS
                dy > 0 -> WALLPAPER
                else -> i
            }
            BRIGHTNESS -> when {
                dx < 0 -> ICONS
                dx > 0 -> VOLUME
                else -> i
            }
            VOLUME -> if (dx < 0) BRIGHTNESS else i
            WALLPAPER -> when {
                dx > 0 -> ANIMATIONS
                dy < 0 -> HIDDEN
                dy > 0 -> WIDGETS
                else -> i
            }
            ANIMATIONS -> when {
                dx < 0 -> WALLPAPER
                dy < 0 -> VOLUME
                dy > 0 -> CLOCK_BTN
                else -> i
            }
            WIDGETS -> when {
                dx > 0 -> CLOCK_BTN
                dy < 0 -> WALLPAPER
                dy > 0 -> FIRST_SHORTCUT + (shortcuts.size / 2).coerceAtMost(shortcuts.lastIndex)
                else -> i
            }
            CLOCK_BTN -> when {
                dx < 0 -> WIDGETS
                dy < 0 -> ANIMATIONS
                dy > 0 -> FIRST_SHORTCUT + shortcuts.lastIndex
                else -> i
            }
            else -> when {
                dx < 0 && i > FIRST_SHORTCUT -> i - 1
                dx > 0 && i < FIRST_SHORTCUT + shortcuts.lastIndex -> i + 1
                dy < 0 -> if (i - FIRST_SHORTCUT > shortcuts.lastIndex / 2) CLOCK_BTN else WIDGETS
                else -> i
            }
        }
    }

    // ---- Widgets page (second control center) ----

    private fun widgetItems() = listOf(
        WidgetItem(Icons.Rounded.MenuBook, "Verse", Features.verse),
        WidgetItem(Icons.Rounded.MusicNote, "Music", Features.music),
        WidgetItem(Icons.Rounded.Subtitles, "Lyrics", Features.lyrics),
        WidgetItem(Icons.Rounded.WbSunny, "Weather", Features.weather),
    )

    private fun selectWidget() {
        when (widgetIndex) {
            0 -> Features.toggleVerse()
            1 -> Features.toggleMusic()
            2 -> Features.toggleLyrics()
            3 -> Features.toggleWeather()
        }
    }

    /** 2x2 grid. Left from the left column goes back to the main page. */
    private fun moveWidgets(dx: Int, dy: Int) {
        val col = widgetIndex % 2
        when {
            dx < 0 -> if (col == 0) page = CcPage.Main else widgetIndex -= 1
            dx > 0 && col == 0 -> widgetIndex += 1
            dy > 0 && widgetIndex < 2 -> widgetIndex += 2
            dy < 0 && widgetIndex >= 2 -> widgetIndex -= 2
        }
    }

    // ---- Clock page (second control center: the clock styles) ----

    private fun selectClock() = repo.changeClockStyle(clockIndex)

    /** 2-column grid (the last row has one style). Left from the left column goes back to the main page. */
    private fun moveClock(dx: Int, dy: Int) {
        val col = clockIndex % 2
        when {
            dx < 0 -> if (col == 0) page = CcPage.Main else clockIndex -= 1
            dx > 0 -> if (col == 0 && clockIndex + 1 < ClockStyle.COUNT) clockIndex += 1
            dy > 0 -> if (clockIndex + 2 < ClockStyle.COUNT) clockIndex += 2
            dy < 0 -> if (clockIndex >= 2) clockIndex -= 2
        }
    }

    private fun clockName(style: Int): String = str(
        when (style) {
            ClockStyle.STACKED -> R.string.clock_stacked
            ClockStyle.ANALOG -> R.string.clock_analog
            ClockStyle.PILL -> R.string.clock_pill
            ClockStyle.FLIP -> R.string.clock_flip
            else -> R.string.clock_classic
        },
    )

    // ---- Detail pages ----

    private fun rows(): List<Line> = when (page) {
        CcPage.Main -> emptyList()
        CcPage.Widgets -> emptyList()
        CcPage.Clock -> emptyList()
        CcPage.Wallpapers -> buildList {
            val current = repo.wallpaperIndex % wallpaperNames.size.coerceAtLeast(1)
            val autoIndex = wallpaperNames.lastIndex
            // Still wallpapers: the generated ones and your own photo. Dynamic: the sky that follows time and weather.
            add(Line.Header("Still wallpaper"))
            for (i in 0 until autoIndex) add(Line.Wall(i, wallpaperNames[i], i == current))
            add(Line.Header("Dynamic"))
            if (autoIndex >= 0) add(Line.Wall(autoIndex, wallpaperNames[autoIndex], autoIndex == current, "Follows the time of day and weather"))
        }
        CcPage.Icons -> buildList {
            add(Line.Pack(null, "Default", "Each app's own banner or logo", iconPack == null))
            packs.forEach { (pkg, label) -> add(Line.Pack(pkg, label, pkg, iconPack == pkg)) }
            add(
                Line.Note(
                    if (packs.isEmpty()) "No icon packs installed. You can also put PNG files named <package>.png in /sdcard/Glass/icons/, then pick Default to refresh."
                    else "You can also put PNG files named <package>.png in /sdcard/Glass/icons/.",
                ),
            )
        }
        CcPage.Wifi -> buildList {
            add(Line.Toggle("Wi-Fi", wifi.enabled))
            if (wifi.enabled) {
                add(Line.Header(str(R.string.wifi_networks)))
                wifi.networks.forEach { add(Line.Network(it)) }
                if (wifi.networks.isEmpty()) {
                    add(Line.Note(str(if (wifi.searching) R.string.wifi_searching else R.string.wifi_none_found)))
                }
                add(Line.Action(str(R.string.wifi_rescan), Icons.Rounded.Search))
            }
            wifi.status?.let { add(Line.Note(it)) }
            if (settings.available(Section.Network)) add(Line.Link(str(R.string.wifi_system_settings), Section.Network))
        }
        CcPage.Bluetooth -> buildList {
            add(Line.Toggle("Bluetooth", bt.enabled))
            if (bt.enabled) {
                add(Line.Header(str(R.string.bt_my_devices)))
                bt.paired.forEach {
                    add(Line.Device(it.address, it.name, str(if (it.connected) R.string.bt_connected_kind else R.string.bt_not_connected_kind, str(it.kind.label)),
                        iconFor(it.kind), true, it.connected))
                }
                if (bt.paired.isEmpty()) add(Line.Note(str(R.string.bt_none_paired)))
                add(Line.Header(str(R.string.bt_other_devices)))
                bt.found.forEach { add(Line.Device(it.address, it.name, str(R.string.bt_ok_to_pair, str(it.kind.label)), iconFor(it.kind), false, false)) }
                add(Line.Action(str(if (bt.discovering) R.string.bt_searching else R.string.bt_search), Icons.Rounded.Search))
            }
            bt.status?.let { add(Line.Note(it)) }
            if (settings.available(Section.Devices)) add(Line.Link(str(R.string.bt_system_settings), Section.Devices))
        }
        CcPage.Adb -> buildList {
            @Suppress("UNUSED_EXPRESSION") adbTick
            val secure = adbSecure()
            val listener = adbListener()
            val location = adbLocation()
            add(Line.Action(if (adbConnected) "ADB connected" else "Connect", Icons.Rounded.Terminal, "connect"))
            add(Line.Header("Permissions"))
            add(Line.Cmd("secure", "Secure settings", if (secure) "Granted" else "OK to grant", secure))
            add(Line.Cmd("listener", "Media listener", if (listener) "Allowed" else "OK to allow", listener))
            add(Line.Cmd("location", "Location", if (location) "Granted" else "OK to grant", location))
            if (adbStatus.isNotEmpty()) add(Line.Note(adbStatus))
        }
    }

    private fun iconFor(kind: BtKind) = when (kind) {
        BtKind.Audio -> Icons.Rounded.Headphones
        BtKind.Remote -> Icons.Rounded.SettingsRemote
        BtKind.Gamepad -> Icons.Rounded.SportsEsports
        BtKind.Phone -> Icons.Rounded.Smartphone
        BtKind.Computer -> Icons.Rounded.Computer
        BtKind.Other -> Icons.Rounded.Devices
    }

    private fun focusableRows(list: List<Line>) = list.indices.filter { list[it].focusable }

    /** Index of the selected item; if it has left the list, the first selectable one. */
    private fun rowIndex(list: List<Line>): Int =
        list.indexOfFirst { it.key == rowKey }.takeIf { it >= 0 } ?: focusableRows(list).firstOrNull() ?: 0

    private fun moveDetail(dy: Int) {
        val list = rows()
        val stops = focusableRows(list)
        if (stops.isEmpty()) return
        val pos = stops.indexOf(rowIndex(list)).coerceAtLeast(0)
        rowKey = list[stops[(pos + dy).coerceIn(0, stops.lastIndex)]].key
    }

    private fun selectDetail() {
        val list = rows()
        val r = list.getOrNull(rowIndex(list)) ?: return
        when (r) {
            is Line.Toggle -> if (page == CcPage.Wifi) {
                // Android 10+ doesn't let apps turn Wi-Fi on or off: open the system screen.
                if (!wifi.turn(!r.on)) open(Section.Network)
            } else {
                // Android 13+: turn on through the standard system dialog, turn off in Settings.
                if (!bt.turn(!r.on)) {
                    if (!r.on) onOpen(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else open(Section.Devices)
                }
            }
            is Line.Network -> when {
                r.net.connected -> Unit
                !wifi.canConnect -> open(Section.Network) // Android 10+: connect from the system screen
                wifi.needsPassword(r.net) -> { password = ""; passwordFor = r.net }
                else -> wifi.connect(r.net)
            }
            is Line.Device -> when {
                !r.bonded -> bt.pair(r.address)
                bt.paired.firstOrNull { it.address == r.address }?.protected == true ->
                    bt.status = str(R.string.bt_protected)
                forgetArmed == r.address -> { forgetArmed = null; bt.forget(r.address) }
                else -> { forgetArmed = r.address; bt.status = str(R.string.bt_forget_confirm, r.name) }
            }
            is Line.Action -> when (page) {
                CcPage.Wifi -> wifi.scan()
                CcPage.Bluetooth -> bt.discover()
                CcPage.Adb -> when (r.id) {
                    "connect" -> { pairText = AdbController.lastHost ?: AdbController.localIp() ?: ""; pairStep = 3 }
                }
                else -> Unit
            }
            is Line.Cmd -> when (r.id) {
                "secure" -> adbRun(AdbController.CMD_WRITE_SECURE)
                "listener" -> adbRun(AdbController.CMD_MEDIA_LISTENER)
                "location" -> adbRun(AdbController.CMD_LOCATION)
            }
            is Line.Link -> open(r.section)
            is Line.Pack -> setIconPack(r.pkg)
            is Line.Wall -> setWallpaper(r.index)
            else -> Unit
        }
    }

    fun submitPassword() {
        val net = passwordFor ?: return
        passwordFor = null
        if (password.isNotEmpty()) wifi.connect(net, password)
        password = ""
    }

    fun cancelPassword() {
        passwordFor = null
        password = ""
        pairStep = 0
        pairText = ""
    }

    /** Keys while the Control Center is open. Returns true if the key is consumed. */
    fun onKey(e: AndroidKey): Boolean {
        if (typing) {
            if (e.keyCode == AndroidKey.KEYCODE_BACK) {
                if (e.action == AndroidKey.ACTION_UP) cancelPassword()
                return true
            }
            return false // the text field and the on-screen keyboard handle the rest
        }
        if (e.action != AndroidKey.ACTION_DOWN) return true
        val (dx, dy) = when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_LEFT -> -1 to 0
            AndroidKey.KEYCODE_DPAD_RIGHT -> 1 to 0
            AndroidKey.KEYCODE_DPAD_UP -> 0 to -1
            AndroidKey.KEYCODE_DPAD_DOWN -> 0 to 1
            else -> 0 to 0
        }
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER ->
                if (e.repeatCount == 0) {
                    when (page) {
                        CcPage.Main -> selectMain()
                        CcPage.Widgets -> selectWidget()
                        CcPage.Clock -> selectClock()
                        else -> selectDetail()
                    }
                }
            AndroidKey.KEYCODE_BACK -> if (e.repeatCount == 0) {
                if (page == CcPage.Main) close() else { page = CcPage.Main; forgetArmed = null }
            }
            AndroidKey.KEYCODE_MENU -> if (e.repeatCount == 0) close()
            else -> if (dx != 0 || dy != 0) {
                when (page) {
                    CcPage.Main -> moveMain(dx, dy)
                    CcPage.Widgets -> moveWidgets(dx, dy)
                    CcPage.Clock -> moveClock(dx, dy)
                    else -> if (dx < 0) { page = CcPage.Main; forgetArmed = null } else if (dy != 0) moveDetail(dy)
                }
            }
        }
        return true
    }

    // ---- Rendering ----

    /**
     * The panel. [reveal] drives the open / close animation: the whole panel grows out of its
     * top-right corner (with the background already blurring behind it), then the tiles spread
     * out one after another. Closing is the same in reverse, but faster.
     *
     * When there is no blurred wallpaper behind it (the Top Shelf video is showing), the big
     * panel is not drawn at all and the tiles get a milky frosted fill instead.
     */
    @Composable
    fun Panel(backdrop: Backdrop, wallpaperName: String, reveal: CcReveal, modifier: Modifier) {
        val density = LocalDensity.current
        val host = remember { CornerHost() }
        val clear = backdrop.frosted == null
        // Final top-left corner of the panel on the screen, so the frosted glass stays lined up while it scales.
        val finalOrigin = with(density) {
            Offset(backdrop.screen.width - (PanelWidth + PanelEnd).toPx(), PanelTop.toPx())
        }
        CompositionLocalProvider(LocalClear provides clear) {
            Glass(
                backdrop,
                modifier
                    .size(PanelWidth, PanelHeight)
                    .graphicsLayer {
                        val t = reveal.progress()
                        val grow: Float
                        val fade: Float
                        if (reveal.opening()) {
                            grow = stage(t, PanelDelayMs, PanelGrowMs, PanelEase)
                            fade = stage(t, PanelFadeDelayMs, PanelFadeMs, FadeEase)
                        } else {
                            grow = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
                            fade = t
                        }
                        val s = PanelStartScale + (1f - PanelStartScale) * grow
                        scaleX = s
                        scaleY = s
                        alpha = fade.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(1f, 0f)
                    },
                RoundedCornerShape(36.dp),
                tint = White.copy(alpha = 0.08f),
                fixedOrigin = finalOrigin,
                showPanel = !clear,
            ) {
                AnimatedContent(
                    page,
                    transitionSpec = {
                        val forward = targetState != CcPage.Main
                        (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
                    },
                    label = "ccPage",
                ) { p ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .onGloballyPositioned { host.coords = it },
                    ) {
                        when (p) {
                            CcPage.Main -> MainPage(wallpaperName, host, reveal)
                            CcPage.Widgets -> WidgetsPage()
                            CcPage.Clock -> ClockPage()
                            else -> DetailPage(p)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MainPage(wallpaperName: String, host: CornerHost, reveal: CcReveal) {
        val clear = LocalClear.current
        val (time, date) = rememberClock(rememberNow())
        val tiles = tiles(wallpaperName)
        Column(Modifier.fillMaxSize()) {
            // Clock on the left, round ADB button on the right.
            Row(
                Modifier.fillMaxWidth().revealFromCorner(host, reveal, 0),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(time, style = txt(White, 28.sp, FontWeight.Bold).shade(clear))
                    BasicText(date, style = txt(White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium).shade(clear))
                }
                RoundButton(Icons.Rounded.Terminal, index == ADB_BTN)
            }
            Spacer(Modifier.height(8.dp))

            // Row 1: Wi-Fi | Bluetooth
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleTile(tiles[WIFI], index == WIFI, Modifier.weight(1f).revealFromCorner(host, reveal, 2))
                ModuleTile(tiles[BLUETOOTH], index == BLUETOOTH, Modifier.weight(1f).revealFromCorner(host, reveal, 1))
            }
            Spacer(Modifier.height(8.dp))

            // Rows 2-3: Icon pack / Hidden apps on the left, two vertical sliders on the right.
            Row(Modifier.fillMaxWidth().height(TileHeight * 2 + 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleTile(tiles[ICONS], index == ICONS, Modifier.fillMaxWidth().revealFromCorner(host, reveal, 4))
                    ModuleTile(tiles[HIDDEN], index == HIDDEN, Modifier.fillMaxWidth().revealFromCorner(host, reveal, 5))
                }
                // Up / Down change the level, OK on the volume mutes.
                Row(
                    Modifier.weight(1f).fillMaxHeight().revealFromCorner(host, reveal, 3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VerticalLevelSlider(
                        Icons.Rounded.BrightnessHigh, SunYellow, levels.brightness,
                        if (levels.canSetBrightness) null else "No access", index == BRIGHTNESS,
                        Modifier.weight(1f),
                    )
                    VerticalLevelSlider(
                        if (levels.volume == 0) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        WifiBlue, levels.volume, null, index == VOLUME,
                        Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Row 4: Wallpaper | Animations
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleTile(tiles[WALLPAPER], index == WALLPAPER, Modifier.weight(1f).revealFromCorner(host, reveal, 7))
                ModuleTile(tiles[ANIMATIONS], index == ANIMATIONS, Modifier.weight(1f).revealFromCorner(host, reveal, 6))
            }
            Spacer(Modifier.height(8.dp))

            // Row 5: Widgets | Clock. Same size and look as the tiles above (icon, title, status).
            val widgetsOn = listOf(Features.verse, Features.music, Features.lyrics, Features.weather).count { it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleTile(
                    Tile(Icons.Rounded.Dashboard, "Widgets", "$widgetsOn/4 on", widgetsOn > 0, Green),
                    index == WIDGETS, Modifier.weight(1f).revealFromCorner(host, reveal, 8),
                )
                ModuleTile(
                    Tile(Icons.Rounded.AccessTime, str(R.string.cc_clock), clockName(repo.clockStyle), true, WifiBlue),
                    index == CLOCK_BTN, Modifier.weight(1f).revealFromCorner(host, reveal, 9),
                )
            }

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().revealFromCorner(host, reveal, 10),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                shortcuts.forEachIndexed { k, s -> RoundButton(s.icon, index == FIRST_SHORTCUT + k) }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                when {
                    index == ADB_BTN -> "ADB"
                    index == WIDGETS -> "Widgets"
                    index == CLOCK_BTN -> str(R.string.cc_clock)
                    index >= FIRST_SHORTCUT -> str(shortcuts[index - FIRST_SHORTCUT].title)
                    else -> " "
                },
                Modifier.fillMaxWidth(),
                style = txt(White.copy(alpha = 0.85f), 13.sp, FontWeight.SemiBold)
                    .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    .shade(clear),
            )
        }
    }

    @Composable
    private fun focusAmount(focused: Boolean) =
        animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 650f), label = "focus").value

    @Composable
    private fun ModuleTile(t: Tile, focused: Boolean, modifier: Modifier) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        val fg = lerp(White, Ink, f)
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier
                .height(TileHeight)
                .graphicsLayer { scaleX = 1f + 0.06f * f; scaleY = 1f + 0.06f * f }
                .background(lerp(restFill(clear, 0.10f), White.copy(alpha = 0.95f), f), shape)
                .glassEdge(clear, f, shape)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(
                    if (t.active) t.accent else lerp(White.copy(alpha = 0.16f), Ink.copy(alpha = 0.08f), f),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(t.icon), null, Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(if (t.active) White else fg))
            }
            Column {
                BasicText(t.title, style = txt(fg, 13.sp, FontWeight.SemiBold), maxLines = 1)
                BasicText(t.status, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    /** Icon-only tile. It fills the width it is given (the full row, or half of it next to another tile). */
    @Composable
    private fun WideIconTile(icon: ImageVector, focused: Boolean, modifier: Modifier = Modifier) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        val shape = RoundedCornerShape(24.dp)
        Box(
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer { scaleX = 1f + 0.04f * f; scaleY = 1f + 0.04f * f }
                .background(lerp(restFill(clear, 0.10f), White.copy(alpha = 0.95f), f), shape)
                .glassEdge(clear, f, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(rememberVectorPainter(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(lerp(White, Ink, f)))
        }
    }

    /**
     * Vertical pill slider (brightness, volume): the white part rises from the bottom, the percent
     * sits at the top and the icon at the bottom. Up / Down change it.
     */
    @Composable
    private fun VerticalLevelSlider(
        icon: ImageVector, tint: Color, percent: Int, note: String?, focused: Boolean, modifier: Modifier,
    ) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        val fill by animateFloatAsState(percent / 100f, tween(120), label = "levelFill")
        val shape = RoundedCornerShape(24.dp)
        val edge = maxOf(0.9f * f, if (clear) 0.45f else 0f)
        Box(
            modifier
                .fillMaxHeight()
                .graphicsLayer { scaleX = 1f + 0.04f * f; scaleY = 1f + 0.03f * f }
                .clip(shape)
                .background(lerp(restFill(clear, 0.12f), White.copy(alpha = 0.34f), f))
                .border(if (f > 0.5f) 2.dp else 1.dp, White.copy(alpha = edge), shape),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(fill.coerceIn(0f, 1f))
                    .background(lerp(White.copy(alpha = 0.78f), White, f)),
            )
            BasicText(
                note ?: "$percent%",
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                style = txt(White, 11.sp, FontWeight.SemiBold)
                    .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    .shade(clear),
                maxLines = 1,
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) { Icon(icon, tint, 18) }
        }
    }

    @Composable
    private fun RoundButton(icon: ImageVector, focused: Boolean) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = 1f + 0.1f * f; scaleY = 1f + 0.1f * f }
                .clip(CircleShape)
                .background(lerp(restFill(clear, 0.12f), White.copy(alpha = 0.95f), f))
                .glassEdge(clear, f, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(rememberVectorPainter(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(lerp(White, Ink, f)))
        }
    }

    // ---- Widgets page (second control center) ----

    @Composable
    private fun WidgetsPage() {
        val clear = LocalClear.current
        val items = widgetItems()
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(rememberVectorPainter(Icons.Rounded.ChevronLeft), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(White.copy(alpha = 0.7f)))
                BasicText("Widgets", style = txt(White, 24.sp, FontWeight.Bold).shade(clear))
            }
            Spacer(Modifier.height(12.dp))
            for (r in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until 2) {
                        val i = r * 2 + c
                        WidgetTile(items[i], widgetIndex == i, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    /** Big on/off tile: the icon circle turns green when the widget is on, grey when it is off. */
    @Composable
    private fun WidgetTile(w: WidgetItem, focused: Boolean, modifier: Modifier) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        val fg = lerp(White, Ink, f)
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier
                .height(112.dp)
                .graphicsLayer { scaleX = 1f + 0.05f * f; scaleY = 1f + 0.05f * f }
                .background(lerp(restFill(clear, 0.10f), White.copy(alpha = 0.95f), f), shape)
                .glassEdge(clear, f, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(
                    if (w.on) Green else lerp(White.copy(alpha = 0.16f), Ink.copy(alpha = 0.08f), f),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(w.icon), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (w.on) White else fg))
            }
            Column {
                BasicText(w.title, style = txt(fg, 15.sp, FontWeight.SemiBold), maxLines = 1)
                BasicText(if (w.on) "On" else "Off", style = txt(fg.copy(alpha = 0.6f), 12.sp, FontWeight.Medium), maxLines = 1)
            }
        }
    }

    // ---- Clock page (second control center: the clock styles) ----

    @Composable
    private fun ClockPage() {
        val clear = LocalClear.current
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(rememberVectorPainter(Icons.Rounded.ChevronLeft), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(White.copy(alpha = 0.7f)))
                BasicText(str(R.string.cc_clock), style = txt(White, 24.sp, FontWeight.Bold).shade(clear))
            }
            Spacer(Modifier.height(12.dp))
            for (r in 0 until 3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until 2) {
                        val i = r * 2 + c
                        if (i < ClockStyle.COUNT) {
                            ClockTile(i, clockIndex == i, repo.clockStyle == i, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    /** One clock style: a small preview and its name; a green check marks the style that is in use. */
    @Composable
    private fun ClockTile(style: Int, focused: Boolean, selected: Boolean, modifier: Modifier) {
        val clear = LocalClear.current
        val f = focusAmount(focused)
        val fg = lerp(White, Ink, f)
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier
                .height(120.dp)
                .graphicsLayer { scaleX = 1f + 0.05f * f; scaleY = 1f + 0.05f * f }
                .background(lerp(restFill(clear, 0.10f), White.copy(alpha = 0.95f), f), shape)
                .glassEdge(clear, f, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.CenterStart) {
                    ClockMini(style, fg)
                }
                if (selected) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(Green),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, White, 14) }
                }
            }
            BasicText(clockName(style), style = txt(fg, 15.sp, FontWeight.SemiBold), maxLines = 1)
        }
    }

    @Composable
    private fun DetailPage(p: CcPage) {
        val clear = LocalClear.current
        val list = rows()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(rememberVectorPainter(Icons.Rounded.ChevronLeft), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(White.copy(alpha = 0.7f)))
                BasicText(
                    when (p) { CcPage.Wifi -> "Wi-Fi"; CcPage.Adb -> "ADB"; CcPage.Icons -> "Icon pack"; CcPage.Wallpapers -> "Wallpaper"; else -> "Bluetooth" },
                    style = txt(White, 24.sp, FontWeight.Bold).shade(clear),
                )
            }
            Spacer(Modifier.height(12.dp))
            // Sliding window: the selected row stays visible.
            val window = 10
            val current = rowIndex(list)
            val start = (current - 3).coerceIn(0, (list.size - window).coerceAtLeast(0))
            list.drop(start).take(window).forEachIndexed { k, r -> DetailRow(r, start + k == current) }
        }
    }

    @Composable
    private fun DetailRow(r: Line, focused: Boolean) {
        val clear = LocalClear.current
        when (r) {
            is Line.Header -> BasicText(r.title.uppercase(Locale.getDefault()), Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                style = txt(White.copy(alpha = 0.55f), 11.sp, FontWeight.SemiBold).shade(clear))
            is Line.Note -> BasicText(r.text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = txt(White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium).shade(clear), maxLines = 4)
            else -> {
                val f = focusAmount(focused)
                val fg = lerp(White, Ink, f)
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .graphicsLayer { scaleX = 1f + 0.025f * f; scaleY = 1f + 0.025f * f }
                        .background(lerp(restFill(clear, 0.08f), White.copy(alpha = 0.95f), f), shape)
                        .glassEdge(clear, f, shape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (r) {
                        is Line.Toggle -> {
                            BasicText(r.title, Modifier.weight(1f), style = txt(fg, 14.sp, FontWeight.SemiBold))
                            Switch(r.on)
                        }
                        is Line.Network -> {
                            SignalBars(r.net.level, fg)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.net.ssid, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = when {
                                    r.net.connected -> wifi.ip?.let { str(R.string.wifi_connected_ip, it) } ?: str(R.string.wifi_connected)
                                    r.net.saved -> str(R.string.wifi_saved)
                                    else -> null
                                }
                                sub?.let { BasicText(it, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium)) }
                            }
                            if (r.net.secure) Icon(Icons.Rounded.Lock, fg.copy(alpha = 0.6f), 15)
                            if (r.net.connected) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, if (f > 0.5f) Ink else WifiBlue, 18) }
                        }
                        is Line.Device -> {
                            Icon(r.icon, if (r.connected && f < 0.5f) WifiBlue else fg, 20)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.name, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(r.subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
                            }
                        }
                        is Line.Action -> {
                            Icon(r.icon, fg, 18)
                            Spacer(Modifier.width(10.dp))
                            BasicText(r.title, style = txt(fg, 14.sp, FontWeight.SemiBold))
                        }
                        is Line.Cmd -> {
                            Icon(Icons.Rounded.Terminal, fg, 18)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
                                BasicText(r.subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
                            }
                            if (r.done) Icon(Icons.Rounded.Check, if (f > 0.5f) Ink else Green, 18)
                        }
                        is Line.Pack -> {
                            Icon(Icons.Rounded.Palette, fg, 20)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(r.subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (r.selected) Icon(Icons.Rounded.Check, if (f > 0.5f) Ink else Green, 18)
                        }
                        is Line.Wall -> {
                            Icon(Icons.Rounded.Wallpaper, fg, 20)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.subtitle.isNotEmpty()) {
                                    BasicText(r.subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (r.selected) Icon(Icons.Rounded.Check, if (f > 0.5f) Ink else Green, 18)
                        }
                        is Line.Link -> {
                            BasicText(r.title, Modifier.weight(1f), style = txt(fg, 13.sp, FontWeight.Medium), maxLines = 1)
                            Icon(Icons.Rounded.OpenInNew, fg.copy(alpha = 0.6f), 16)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: ImageVector, tint: Color, size: Int) =
        Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))

    @Composable
    private fun Switch(on: Boolean) {
        val k by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "switch")
        Canvas(Modifier.size(42.dp, 25.dp)) {
            drawRoundRect(lerp(Color(0xFF787880).copy(alpha = 0.45f), Green, k), cornerRadius = CornerRadius(size.height / 2))
            val d = size.height - 4.dp.toPx()
            drawCircle(White, d / 2, Offset(2.dp.toPx() + d / 2 + (size.width - d - 4.dp.toPx()) * k, size.height / 2))
        }
    }

    @Composable
    private fun SignalBars(level: Int, color: Color) {
        Canvas(Modifier.size(18.dp, 14.dp)) {
            val w = size.width / 4.6f
            for (b in 0 until 4) {
                val h = size.height * (b + 1) / 4f
                drawRoundRect(
                    if (b < level.coerceAtLeast(1)) color else color.copy(alpha = 0.25f),
                    topLeft = Offset(b * w * 1.2f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 3),
                )
            }
        }
    }

    /**
     * Text entry, centered on screen (place it at the root). Used for the Wi-Fi password and,
     * for ADB, the TV's IP address (step 3), the pairing port or the 6-digit code.
     */
    @Composable
    fun PasswordLayer(backdrop: Backdrop, onTypingDone: () -> Unit) {
        val pairing = pairStep != 0
        val net = passwordFor
        if (net == null && !pairing) return
        val field = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(net, pairStep) {
            field.requestFocus()
            keyboard?.show()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Glass(backdrop, Modifier.width(440.dp), RoundedCornerShape(32.dp), tint = White.copy(alpha = 0.10f)) {
                Column(Modifier.padding(24.dp)) {
                    BasicText(
                        when {
                            pairStep == 3 -> "TV IP address"
                            pairing -> if (pairStep == 1) "Pairing port" else "Pairing code"
                            else -> str(R.string.wifi_password)
                        },
                        style = txt(White, 22.sp, FontWeight.Bold),
                    )
                    BasicText(
                        when {
                            pairStep == 3 -> "Type this TV's IP, then press Done"
                            pairing -> if (pairStep == 1) "Type the port shown next to the IP address" else "Type the 6-digit code"
                            else -> str(R.string.wifi_password_for, net?.ssid ?: "")
                        },
                        style = txt(White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium),
                    )
                    Spacer(Modifier.height(16.dp))
                    BasicTextField(
                        value = if (pairing) pairText else password,
                        onValueChange = { if (pairing) pairText = it else password = it },
                        singleLine = true,
                        textStyle = txt(Ink, 18.sp, FontWeight.Medium),
                        cursorBrush = SolidColor(WifiBlue),
                        visualTransformation = if (pairing) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when {
                                pairStep == 3 -> KeyboardType.Uri
                                pairing -> KeyboardType.Number
                                else -> KeyboardType.Password
                            },
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            if (pairing) submitPair() else submitPassword()
                            onTypingDone()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .background(White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        when {
                            pairStep == 3 -> "The first time, accept the USB debugging prompt on the TV."
                            pairing -> "Keep the pairing screen on the TV in mind: the code stops working if it closes."
                            else -> str(R.string.wifi_password_hint)
                        },
                        style = txt(White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
                    )
                }
            }
        }
    }
}

/** Height of one module tile; the two-row slider block is twice this plus the 8dp gap. */
private val TileHeight = 64.dp
