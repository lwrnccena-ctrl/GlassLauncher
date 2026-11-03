package com.fares.glass.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fares.glass.R
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository
import com.fares.glass.data.Locator
import com.fares.glass.data.Weather
import com.fares.glass.data.WeatherRepository
import com.fares.glass.gfx.Blur
import com.fares.glass.gfx.DepthPictures
import com.fares.glass.gfx.DepthSettings
import com.fares.glass.gfx.GamerRoomWallpaper
import com.fares.glass.sys.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface Command {
    data object Settings : Command
    data object Network : Command
    data object Bluetooth : Command
    data object Apps : Command
    data object NextWallpaper : Command
    data class AppInfo(val pkg: String) : Command
}

private const val LONG_PRESS_MS = 450L
private val MenuHeight = 196.dp
private val MenuWidth = 260.dp
/** Space around the icons inside the dock (sides / above); it follows Dock size in the settings. */
private val DockInset: Dp
    get() = when (DockSettings.dockSize) { 0 -> 12.dp; 2 -> 32.dp; else -> 20.dp }
private val DockTop: Dp
    get() = when (DockSettings.dockSize) { 0 -> 10.dp; 2 -> 28.dp; else -> 18.dp }
/** Covers the row and the focus zoom (no names under dock icons). */
private val DockHeight: Dp
    get() = Metrics.TileH + DockTop * 2 + (if (DockSettings.label == 2) 24.dp else 0.dp)

/** Top padding of the greeting column. */
private val HeaderTop = 30.dp

/** The media tile sits this far to the left of the Control Center panel. */
private val MediaTileGap = 14.dp

/** Time curve of the weather panel opening (the edges add their own ease on top of it). */
private val IslandEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** A white outline around something that is selected (it does not change the layout). */
private fun Modifier.focusOutline(on: Boolean): Modifier = if (!on) this else drawBehind {
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-10.dp.toPx(), -6.dp.toPx()),
        size = Size(size.width + 20.dp.toPx(), size.height + 12.dp.toPx()),
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(2.dp.toPx()),
    )
}

/** Shrinks its content (it never enlarges it) so that it fits the space it is given. */
@Composable
private fun FitScale(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        if (placeable == null || placeable.width == 0 || placeable.height == 0) {
            layout(0, 0) {}
        } else {
            val s = minOf(
                1f,
                constraints.maxWidth.toFloat() / placeable.width,
                constraints.maxHeight.toFloat() / placeable.height,
            ).coerceAtLeast(0f)
            layout((placeable.width * s).roundToInt(), (placeable.height * s).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    repo: AppRepository,
    state: HomeState,
    backdrop: Backdrop,
    wallpaper: ImageBitmap?,
    wallpaperName: String,
    cc: ControlCenter,
    onLaunch: (AppEntry, Rect) -> Unit,
    onCommand: (Command) -> Unit,
    raining: Boolean = false,
    weather: Weather? = null,
    /** True while the sky wallpaper (the one with the TV) is showing: lyrics go on its TV screen. */
    tvWallpaper: Boolean = false,
    /** A city was chosen in the city picker (null = back to the automatic location). */
    onSetLocation: (Locator.Place?) -> Unit = {},
) {
    // The living-room layout (verse column, lyrics on the TV) is not used with the gamer room or the depth wallpaper.
    val tvWallpaper = tvWallpaper && !RoomSettings.on && !DepthSettings.on
    // Simple mode shows the weather under its big clock, so the weather pill at the top is switched off there.
    val weatherOn = Features.weather && !DepthSettings.on
    // With Favorites row on, the apps pinned from the long-press menu come first, so they fill the dock.
    val apps: List<AppEntry> = if (DockSettings.favorites && DockSettings.pinned.isNotEmpty()) {
        val byPkg = repo.apps.associateBy { it.pkg }
        val front = DockSettings.pinned.mapNotNull { byPkg[it] }.take(Metrics.COLS)
        front + repo.apps.filter { a -> front.none { it.pkg == a.pkg } }
    } else repo.apps
    val density = LocalDensity.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    // Selected button on the now playing card: -1 = none, 0 = previous, 1 = play/pause, 2 = next.
    var mediaFocus by remember { mutableIntStateOf(-1) }
    // Top buttons (icons only): 0 Wi-Fi, 1 Bluetooth, 2 TV input, 3 Settings.
    val topCount = 4
    // Up from the top buttons selects the greeting; OK then lets you type the name that follows it.
    var nameFocus by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    // TV input picker (HDMI 1, HDMI 2...) under the top buttons.
    var inputOpen by remember { mutableStateOf(false) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var inputs by remember { mutableStateOf<List<InputSources.Source>>(emptyList()) }
    // Weather: Up to the greeting, then Right selects the weather text; OK opens the weather panel.
    var weatherFocus by remember { mutableStateOf(false) }
    var forecastOpen by remember { mutableStateOf(false) }
    // Selected day in the weather panel; one past the last day is the "Change location" row.
    var forecastDay by remember { mutableIntStateOf(0) }
    // Where the small weather text sits on the screen: the weather panel grows out of it.
    var weatherBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // Where the now playing card sits on the home screen: it flies from here to the Control Center tile.
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // Width of the screen in pixels (the media tile is placed from the right edge).
    var screenWpx by remember { mutableIntStateOf(0) }
    val forecastDays = weather
        ?.takeIf { !it.isPlaceholder && it.place.isNotBlank() && weatherOn }
        ?.days.orEmpty()
    // City picker: type a place, choose it from the matches (or "Detect automatically").
    val scope = rememberCoroutineScope()
    var locOpen by remember { mutableStateOf(false) }
    var locTyping by remember { mutableStateOf(true) }
    var locResults by remember { mutableStateOf<List<Locator.Place>>(emptyList()) }
    var locIndex by remember { mutableIntStateOf(0) }
    var locStatus by remember { mutableStateOf<String?>(null) }
    fun openLocation() {
        locResults = emptyList()
        locStatus = null
        locIndex = 0
        locTyping = true
        locOpen = true
    }
    fun startSearch(query: String) {
        if (query.isBlank()) return
        locTyping = false
        locStatus = "Searching\u2026"
        locResults = emptyList()
        locIndex = 0
        scope.launch {
            val found = Locator.search(query)
            locResults = found
            locStatus = if (found.isEmpty()) "No places found" else null
            locIndex = if (found.isEmpty()) 0 else 1
        }
    }
    // The Control Center switch for the music card (and its remote control).
    val cardShown = NowPlaying.active && Features.music
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Lets the now playing card see which media is playing.
    LaunchedEffect(Unit) { NowPlaying.ensureListener(context) }
    // Leaving the top row also leaves the card.
    LaunchedEffect(state.mode) {
        if (state.mode != Mode.Top) {
            mediaFocus = -1
            nameFocus = false
            weatherFocus = false
        }
    }
    // The name keyboard was closed: the remote drives the home screen again.
    LaunchedEffect(editingName) { if (!editingName) focusRequester.requestFocus() }
    // The city picker is closed, or showing its list: the remote drives it (or the home screen) again.
    LaunchedEffect(locOpen, locTyping) { if (!locOpen || !locTyping) focusRequester.requestFocus() }
    // The card disappeared while it was selected: go back to the apps.
    LaunchedEffect(cardShown) {
        if (!cardShown && mediaFocus >= 0) {
            mediaFocus = -1
            state.mode = Mode.Browse
        }
    }
    // Wi-Fi password entry finished: the remote drives the home screen again.
    LaunchedEffect(cc.typing) { if (!cc.typing) focusRequester.requestFocus() }
    LaunchedEffect(apps.size) {
        if (apps.isNotEmpty() && state.focus > apps.lastIndex) state.focus = apps.lastIndex
    }

    // Recent apps: a cascade of cards, opened with a double press of Home or by holding Back.
    var recentIndex by remember { mutableIntStateOf(0) }
    val recentApps = Recents.list.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
    LaunchedEffect(Recents.open) {
        if (Recents.open) {
            recentIndex = 0
            mediaFocus = -1
            if (state.mode == Mode.Control) cc.close() else state.mode = Mode.Browse
        }
    }

    // Control Center open / close progress (0 = closed, 1 = open). It is only read while drawing
    // (inside graphicsLayer blocks), so animating it never recomposes this big screen.
    val ccOpen = state.mode == Mode.Control
    val ccAnim = animateFloatAsState(
        if (ccOpen) 1f else 0f,
        if (ccOpen) tween(CcOpenMs, easing = LinearEasing) else tween(CcCloseMs, easing = LinearEasing),
        label = "ccReveal",
    )
    val ccShown by remember { derivedStateOf { ccAnim.value > 0.001f } }
    val ccReveal = remember { CcReveal({ ccAnim.value }, { state.mode == Mode.Control }) }

    // Progress of the now playing card turning into the Control Center tile (0 = card, 1 = tile).
    // It is a spring: it overshoots a little (the bounce) and, if the key is pressed again halfway,
    // it keeps its speed and turns around smoothly.
    val mediaSpring = animateFloatAsState(
        if (ccOpen) 1f else 0f,
        GlassSpring.cardSpec(),
        label = "mediaSpring",
    )
    val mediaMorph: () -> Float = { mediaSpring.value }
    // The flying card stays on screen until the spring has completely settled (it can outlast the
    // panel's fast closing animation).
    val mediaShown by remember { derivedStateOf { ccAnim.value > 0.001f || mediaSpring.value != 0f } }
    // The tile is hidden on the wide Personalization page, where the panel would cover it.
    val tilePageAlpha by animateFloatAsState(
        if (cc.page == CcPage.Personalization) 0f else 1f, tween(200), label = "tilePage",
    )
    // Which button of the tile is selected (-1 = the tile is not selected).
    val tileFocus = if (ccOpen && cc.page == CcPage.Main && cc.index == CcMediaTile) cc.mediaSub else -1

    // Blur behind the recent apps AND behind the Control Center.
    // Android 12+: the whole home screen (wallpaper, greeting, clock, weather, dock, apps) is blurred live.
    // Android 7-11 can't do that, so one snapshot of the home screen is taken when recents / Control Center
    // opens, blurred once with Glass's own Blur, and faded in on top.
    val sceneLayer = rememberGraphicsLayer()
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    val blurWanted = Recents.open || state.mode == Mode.Control
    val frozenAlpha by animateFloatAsState(
        if (blurWanted && frozen != null) 1f else 0f, tween(300), label = "frozen",
    )
    LaunchedEffect(blurWanted) {
        if (Build.VERSION.SDK_INT >= 31) return@LaunchedEffect
        if (blurWanted) {
            try {
                val shot = sceneLayer.toImageBitmap()
                frozen = withContext(Dispatchers.Default) {
                    val soft = shot.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                    Blur.frosted(soft, 480, 7f, 1.1f, 1.0f).asImageBitmap()
                }
            } catch (ex: Exception) {
                frozen = null
            }
        } else {
            delay(400)
            frozen = null
        }
    }
    val recentsMix by animateFloatAsState(if (Recents.open) 1f else 0f, tween(350), label = "recentsMix")

    // The dock sits low enough that the second row of apps stays below the bottom edge of the screen.
    var screenHeightDp by remember { mutableFloatStateOf(540f) }
    val dockDrop = run {
        val rowOneTop = Metrics.TopRowY.value + Metrics.Pitch.value
        val dockBottom = Metrics.TopRowY.value - DockTop.value + DockHeight.value
        val room = screenHeightDp - 12f - dockBottom   // how far the dock can go down and still fit on screen
        val wanted = screenHeightDp - rowOneTop + 4f   // how far it must go to hide the second row
        wanted.coerceIn(0f, room.coerceAtLeast(0f))
    }.dp
    // Dock height in the settings: the dock (and the first row) sits higher on the screen.
    val dockLift = when (DockSettings.dockHeight) { 1 -> 28f; 2 -> 56f; else -> 0f }
    val topRowY = Metrics.TopRowY + dockDrop - dockLift.dp

    val row = state.row
    val scrollTarget = if (row == 0) 0f else (topRowY + Metrics.Pitch * row - Metrics.GridRowY).value
    val scroll = animateFloatAsState(scrollTarget, spring(dampingRatio = 1f, stiffness = 210f), label = "scroll")
    val inGrid = row > 0 || state.mode == Mode.Control
    // The background blurs quickly while the Control Center opens, so the panel grows over an already soft wallpaper.
    val blurMs = when {
        state.mode == Mode.Control -> 320
        row == 0 -> 260
        else -> 450
    }
    val blur by animateFloatAsState(if (inGrid) 1f else 0f, tween(blurMs), label = "blur")
    // The lyrics are only shown on the first row (like the greeting and the clock).
    val lyricsAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "lyricsAlpha")
    // The big clock of the depth wallpaper is only shown on the first row too.
    val depthClockAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "depthClock")
    // The rows under the dock stay hidden until you move down (matters when the dock is raised).
    val lowerRows by animateFloatAsState(if (row == 0) 0f else 1f, tween(250), label = "lowerRows")
    val dim by animateFloatAsState(
        if (state.mode == Mode.Control || state.mode == Mode.Menu) 0.3f else 0f, tween(250), label = "dim",
    )

    // Weather panel open / close progress (0 = the small pill, 1 = the whole screen). Like the Control
    // Center, it is only read while drawing, so the animation never recomposes this big screen.
    val islandOpen = forecastOpen && forecastDays.isNotEmpty()
    val islandAnim = animateFloatAsState(
        if (islandOpen) 1f else 0f,
        tween(if (islandOpen) 1600 else 700, easing = IslandEasing),
        label = "island",
    )
    val islandShown by remember { derivedStateOf { islandAnim.value > 0.001f } }

    // Gamer room wallpaper: a picture drawn for the time of day and the weather, and a blurred copy
    // of it for behind the Control Center and the grid. It is redrawn every few minutes.
    val realWeather = weather?.takeIf { !it.isPlaceholder }
    val roomSky = GamerRoomWallpaper.skyFor(realWeather?.code, raining)
    // Real sunrise and sunset of the place (or the usual Philippines times while the weather is not loaded yet).
    val roomSun: Pair<Long, Long> = realWeather?.takeIf { it.sunrise > 0L && it.sunset > 0L }
        ?.let { it.sunrise + WeatherRepository.skewMs(it.zone) to it.sunset + WeatherRepository.skewMs(it.zone) }
        ?: WeatherRepository.fallbackTimes()
    var roomImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var roomFrosted by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(RoomSettings.on, roomSky, roomSun) {
        if (!RoomSettings.on) {
            roomImage = null
            roomFrosted = null
            return@LaunchedEffect
        }
        while (true) {
            val bmp = withContext(Dispatchers.Default) { GamerRoomWallpaper.render(context, roomSky, System.currentTimeMillis(), roomSun.first, roomSun.second) }
            roomImage = bmp.asImageBitmap()
            roomFrosted = withContext(Dispatchers.Default) {
                Blur.frosted(bmp.copy(Bitmap.Config.ARGB_8888, false), 480, 7f, 1.1f, 1.0f).asImageBitmap()
            }
            delay(5 * 60_000L)
        }
    }
    // The room slides sideways with the focus: -1 on the first app of the row, +1 on the last one.
    val roomCols = Metrics.COLS
    val roomFx = ((state.focus % roomCols) / (roomCols - 1).coerceAtLeast(1).toFloat()) * 2f - 1f

    // Depth wallpaper: your own photo (Glass/depth/background.jpg) with the clock behind the foreground
    // cutout (Glass/depth/foreground.png). A blurred copy of the photo is used for the grid and the glass tiles.
    var depthBack by remember { mutableStateOf<ImageBitmap?>(null) }
    var depthFront by remember { mutableStateOf<ImageBitmap?>(null) }
    var depthFrosted by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(DepthSettings.on, DepthSettings.reload) {
        if (!DepthSettings.on) {
            depthBack = null
            depthFront = null
            depthFrosted = null
            return@LaunchedEffect
        }
        val pics = withContext(Dispatchers.Default) { DepthPictures.load(context) }
        depthBack = pics.background?.asImageBitmap()
        depthFront = pics.foreground?.asImageBitmap()
        depthFrosted = pics.background?.let { bmp ->
            withContext(Dispatchers.Default) {
                Blur.frosted(bmp.copy(Bitmap.Config.ARGB_8888, false), 480, 7f, 1.1f, 1.0f).asImageBitmap()
            }
        }
    }
    // The glass tiles blur the depth photo too (MainActivity puts the normal wallpaper there whenever it changes).
    LaunchedEffect(DepthSettings.on, depthFrosted, wallpaper) {
        if (DepthSettings.on && !RoomSettings.on) depthFrosted?.let { backdrop.frosted = it }
    }

    // Auto-hide dock: after a few seconds without a key the dock slides away; any key brings it back.
    var dockHidden by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    LaunchedEffect(activity, DockSettings.autoHide, state.mode, row, Recents.open, forecastOpen) {
        val secs = when (DockSettings.autoHide) { 1 -> 5; 2 -> 10; else -> 0 }
        if (secs == 0 || state.mode != Mode.Browse || row != 0 || Recents.open || forecastOpen) {
            dockHidden = false
            return@LaunchedEffect
        }
        delay(secs * 1000L)
        dockHidden = true
    }
    val hideSlide by animateFloatAsState(
        if (dockHidden) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "dockHide",
    )

    val focused = apps.getOrNull(state.focus)
    val ambience = if (repo.topShelf && row == 0 && state.mode == Mode.Browse) {
        focused?.let { repo.visuals[it.pkg]?.ambience }
    } else null

    // The long-press menu. With pinned apps in the dock the order is fixed by the pins, so Move is left out.
    val canMove = !(DockSettings.favorites && DockSettings.pinned.isNotEmpty())
    val menuKeys = buildList<String> {
        if (canMove) add("move")
        add("hide")
        add("info")
        if (DockSettings.favorites) add("pin")
        add("lock")
    }
    val menu = menuItemsFor(menuKeys, DockSettings.isPinned(focused?.pkg), DockSettings.isLocked(focused?.pkg))

    fun tileX(i: Int) = Metrics.RowStart + (Metrics.TileW + Metrics.Gap) * (i % Metrics.COLS)
    fun tileY(i: Int) = (topRowY + Metrics.Pitch * (i / Metrics.COLS)).value - scrollTarget

    // ---- PIN dialog (app lock) ----
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    val pinDigits = remember { mutableStateListOf(0, 0, 0, 0) }
    var pinPos by remember { mutableIntStateOf(0) }
    var pinFirst by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf("") }
    var pinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun resetPinDigits() {
        for (i in 0..3) pinDigits[i] = 0
        pinPos = 0
    }

    fun openPin(mode: PinMode, message: String, action: () -> Unit) {
        pinMode = mode
        pinAction = action
        pinMessage = message
        pinFirst = ""
        resetPinDigits()
    }

    /** Runs [action] after the right PIN was typed (at once when no PIN has been set). */
    fun requirePin(message: String, action: () -> Unit) {
        if (DockSettings.pin.isBlank()) action() else openPin(PinMode.Enter, message, action)
    }

    fun submitPin() {
        val code = pinDigits.joinToString("")
        when (pinMode) {
            PinMode.Enter -> if (code == DockSettings.pin) {
                val action = pinAction
                pinMode = null
                action?.invoke()
            } else {
                pinMessage = "Wrong PIN, try again"
                resetPinDigits()
            }
            PinMode.SetNew -> {
                pinFirst = code
                pinMode = PinMode.Confirm
                pinMessage = "Enter it once more"
                resetPinDigits()
            }
            PinMode.Confirm -> if (code == pinFirst) {
                DockSettings.savePin(code)
                val action = pinAction
                pinMode = null
                action?.invoke()
            } else {
                pinMode = PinMode.SetNew
                pinMessage = "The PINs did not match, start again"
                resetPinDigits()
            }
            null -> Unit
        }
    }

    fun pinKey(e: AndroidKey): Boolean {
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        if (e.keyCode in AndroidKey.KEYCODE_0..AndroidKey.KEYCODE_9) {
            if (first) {
                pinDigits[pinPos] = e.keyCode - AndroidKey.KEYCODE_0
                if (pinPos < 3) pinPos++ else submitPin()
            }
            return true
        }
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_UP -> if (down) pinDigits[pinPos] = (pinDigits[pinPos] + 1) % 10
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) pinDigits[pinPos] = (pinDigits[pinPos] + 9) % 10
            AndroidKey.KEYCODE_DPAD_RIGHT -> if (first && pinPos < 3) pinPos++
            AndroidKey.KEYCODE_DPAD_LEFT -> if (first && pinPos > 0) pinPos--
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first) { if (pinPos < 3) pinPos++ else submitPin() }
            AndroidKey.KEYCODE_BACK -> if (first) pinMode = null
        }
        return true
    }

    /** Opens an app; a locked one asks for the PIN first. */
    fun launchApp(entry: AppEntry, r: Rect) {
        if (DockSettings.isLocked(entry.pkg)) {
            requirePin("This app is locked") { onLaunch(entry, r) }
        } else {
            onLaunch(entry, r)
        }
    }

    /** Lock / unlock from the long-press menu. Locking the first app asks you to choose a PIN. */
    fun toggleLock(pkg: String) {
        when {
            DockSettings.isLocked(pkg) ->
                requirePin("Enter your PIN to unlock this app") { DockSettings.setLocked(pkg, false) }
            DockSettings.pin.isBlank() ->
                openPin(PinMode.SetNew, "Choose a PIN for locked apps") { DockSettings.setLocked(pkg, true) }
            else -> DockSettings.setLocked(pkg, true)
        }
    }

    fun launchFocused() {
        val entry = focused ?: return
        val r = with(density) {
            val x = tileX(state.focus).roundToPx()
            val y = tileY(state.focus).dp.roundToPx()
            Rect(x, y, x + Metrics.TileW.roundToPx(), y + Metrics.TileH.roundToPx())
        }
        launchApp(entry, r)
    }

    fun move(dx: Int, dy: Int) {
        when (state.mode) {
            Mode.Browse -> {
                if (dy < 0 && row == 0) {
                    // Above the dock: the now playing card if it is showing (play/pause is selected first),
                    // from any app in the row. Up again goes on to the glass buttons at the top right.
                    state.topIndex = topCount - 1
                    state.mode = Mode.Top
                    if (cardShown) mediaFocus = 1
                    return
                }
                HomeState.target(state.focus, dx, dy, apps.size)?.let { state.focus = it }
                state.bump(dx, dy)
            }
            Mode.Top -> when {
                // The weather is selected: Down goes back to the top buttons, Left to the greeting.
                weatherFocus -> when {
                    dy > 0 -> weatherFocus = false
                    dx > 0 -> { weatherFocus = false; state.topIndex = 0 }
                    dx < 0 -> { weatherFocus = false; nameFocus = true }
                }
                // The greeting is selected: Down goes back to the top buttons, Right to the weather.
                nameFocus -> when {
                    dy > 0 -> nameFocus = false
                    dx > 0 && weather != null && weatherOn -> { nameFocus = false; weatherFocus = true }
                }
                // Up from the top buttons: the greeting (and its name). The depth wallpaper has no greeting.
                dy < 0 -> if (!DepthSettings.on) nameFocus = true
                // Down from the top buttons: the now playing card if it is showing, otherwise the apps.
                dy > 0 -> if (cardShown) mediaFocus = 1 else state.mode = Mode.Browse
                // Left from the Wi-Fi button: the weather pill.
                dx < 0 && state.topIndex == 0 && weather != null && weatherOn -> weatherFocus = true
                dx != 0 -> state.topIndex = (state.topIndex + dx).coerceIn(0, topCount - 1)
            }
            Mode.Jiggle -> HomeState.target(state.focus, dx, dy, apps.size)?.let {
                repo.move(state.focus, it)
                state.focus = it
            }
            Mode.Menu -> state.menuIndex = (state.menuIndex + dy).coerceIn(0, menu.lastIndex)
            Mode.Control -> Unit // handled by the Control Center
        }
    }

    fun select() {
        when (state.mode) {
            Mode.Browse -> launchFocused()
            Mode.Top -> when {
                weatherFocus -> {
                    if (forecastDays.isNotEmpty()) {
                        // The weather panel grows out of the weather text (its last row changes the location).
                        forecastDay = 0
                        forecastOpen = true
                    } else {
                        // No forecast (location unavailable): go straight to the city picker.
                        openLocation()
                    }
                }
                nameFocus -> editingName = true
                state.topIndex == 2 -> {
                    // TV input: a short list of the TV's inputs.
                    inputs = InputSources.list(context)
                    inputIndex = 0
                    inputOpen = true
                }
                else -> {
                    // The other buttons open the Control Center straight on the matching page.
                    cc.open(when (state.topIndex) { 0 -> CcPage.Wifi; 1 -> CcPage.Bluetooth; else -> CcPage.Main })
                    state.mode = Mode.Control
                }
            }
            Mode.Jiggle -> {
                repo.saveOrder()
                state.mode = Mode.Browse
            }
            Mode.Menu -> {
                val entry = focused
                state.mode = Mode.Browse
                if (entry != null) when (menuKeys.getOrNull(state.menuIndex)) {
                    "move" -> state.mode = Mode.Jiggle
                    "hide" -> repo.hide(entry.pkg)
                    "info" -> onCommand(Command.AppInfo(entry.pkg))
                    "pin" -> DockSettings.togglePinned(entry.pkg)
                    "lock" -> toggleLock(entry.pkg)
                }
            }
            Mode.Control -> Unit
        }
    }

    fun longPress() {
        if (state.mode == Mode.Browse && focused != null) {
            state.menuIndex = 0
            state.mode = Mode.Menu
        }
    }

    fun back() {
        when (state.mode) {
            Mode.Menu, Mode.Control, Mode.Top -> state.mode = Mode.Browse
            Mode.Jiggle -> { repo.saveOrder(); state.mode = Mode.Browse }
            Mode.Browse -> if (row > 0) state.focus %= Metrics.COLS
        }
    }

    fun menuKey() {
        state.mode = when (state.mode) {
            Mode.Browse, Mode.Top -> { cc.open(CcPage.Main); Mode.Control }
            Mode.Jiggle -> { repo.saveOrder(); Mode.Browse }
            else -> Mode.Browse
        }
    }

    /** Yellow button: opens the Control Center, and closes it again when it is already open. */
    fun yellowKey() {
        when (state.mode) {
            Mode.Control -> {
                // Close it (also while a Control Center sub-page is showing).
                state.mode = Mode.Browse
            }
            Mode.Browse, Mode.Top -> {
                mediaFocus = -1
                cc.open(CcPage.Main)
                state.mode = Mode.Control
            }
            Mode.Jiggle -> { repo.saveOrder(); state.mode = Mode.Browse }
            Mode.Menu -> state.mode = Mode.Browse
        }
    }

    /** Keys while the stack of recent apps is showing. */
    fun recentsKey(e: AndroidKey): Boolean {
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_LEFT -> if (down) recentIndex = (recentIndex - 1).coerceAtLeast(0)
            AndroidKey.KEYCODE_DPAD_RIGHT ->
                if (down) recentIndex = (recentIndex + 1).coerceAtMost((recentApps.size - 1).coerceAtLeast(0))
            // Up closes the app, as swiping a card up does on Apple devices.
            AndroidKey.KEYCODE_DPAD_UP -> if (first) {
                recentApps.getOrNull(recentIndex)?.let { entry ->
                    Recents.remove(entry.pkg)
                    try {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                            .killBackgroundProcesses(entry.pkg)
                    } catch (ex: Exception) {
                        // The app keeps running in the background; it is only removed from the list.
                    }
                    recentIndex = recentIndex.coerceAtMost((recentApps.size - 2).coerceAtLeast(0))
                }
            }
            AndroidKey.KEYCODE_DPAD_DOWN, AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_MENU,
            AndroidKey.KEYCODE_APP_SWITCH -> if (first) Recents.open = false
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first) {
                val entry = recentApps.getOrNull(recentIndex)
                Recents.open = false
                if (entry != null) {
                    // The app opens out of the middle of the screen.
                    val dm = context.resources.displayMetrics
                    val w = dm.widthPixels
                    val h = dm.heightPixels
                    launchApp(entry, Rect(w / 2 - w / 8, h / 2 - h / 8, w / 2 + w / 8, h / 2 + h / 8))
                }
            }
        }
        return true
    }

    /** Keys while the weather panel is showing: Up / Down pick a day, OK on the last row changes the location. */
    fun forecastKey(e: AndroidKey): Boolean {
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_UP -> if (down) forecastDay = (forecastDay - 1).coerceAtLeast(0)
            // The last row is "Change location".
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) forecastDay = (forecastDay + 1).coerceAtMost(forecastDays.size)
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first && forecastDay >= forecastDays.size) {
                forecastOpen = false
                openLocation()
            }
            AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_DPAD_RIGHT,
            AndroidKey.KEYCODE_MENU, AndroidKey.KEYCODE_PROG_BLUE -> if (first) forecastOpen = false
        }
        return true
    }

    /** Keys while the city picker is showing. */
    fun locationKey(e: AndroidKey): Boolean {
        if (locTyping) {
            // The text field and the on-screen keyboard handle the keys; Back closes the picker.
            if (e.keyCode == AndroidKey.KEYCODE_BACK) {
                if (e.action == AndroidKey.ACTION_UP) locOpen = false
                return true
            }
            return false
        }
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_UP -> if (down) locIndex = (locIndex - 1).coerceAtLeast(0)
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) locIndex = (locIndex + 1).coerceAtMost(locResults.size)
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first) {
                // Row 0 is "Detect automatically"; the others are the matches.
                val chosen = locResults.getOrNull(locIndex - 1)
                locOpen = false
                onSetLocation(chosen)
            }
            AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_MENU ->
                if (first) locTyping = true
        }
        return true
    }

    /** Keys while the list of TV inputs is showing. */
    fun inputKey(e: AndroidKey): Boolean {
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_UP -> if (down) inputIndex = (inputIndex - 1).coerceAtLeast(0)
            AndroidKey.KEYCODE_DPAD_DOWN ->
                if (down) inputIndex = (inputIndex + 1).coerceAtMost((inputs.size - 1).coerceAtLeast(0))
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first) {
                val source = inputs.getOrNull(inputIndex)
                inputOpen = false
                if (source != null) InputSources.open(context, source)
            }
            AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_DPAD_RIGHT, AndroidKey.KEYCODE_BACK,
            AndroidKey.KEYCODE_MENU -> if (first) inputOpen = false
        }
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        val e = event.nativeKeyEvent
        activity++
        if (dockHidden) {
            // The first key only brings the dock back.
            dockHidden = false
            return true
        }
        if (pinMode != null) return pinKey(e)
        if (Recents.open) return recentsKey(e)
        if (editingName) {
            // The text field and the on-screen keyboard handle the keys; Back cancels.
            if (e.keyCode == AndroidKey.KEYCODE_BACK) {
                if (e.action == AndroidKey.ACTION_UP) editingName = false
                return true
            }
            return false
        }
        if (locOpen) return locationKey(e)
        if (inputOpen) return inputKey(e)
        if (forecastOpen && forecastDays.isNotEmpty()) return forecastKey(e)

        // Yellow button: toggles the Control Center. This sits before the Control Center's own key
        // handler, otherwise that handler would swallow the key while the panel is open.
        if (e.keyCode == AndroidKey.KEYCODE_PROG_YELLOW) {
            // Not while the Wi-Fi password is being typed.
            if (cc.typing) return false
            if (e.action == AndroidKey.ACTION_DOWN && e.repeatCount == 0) yellowKey()
            return true
        }

        if (state.mode == Mode.Control) return cc.onKey(e)
        val down = e.action == AndroidKey.ACTION_DOWN

        // A button on the now playing card is selected: the remote drives the card.
        if (mediaFocus >= 0) {
            var handled = true
            when (e.keyCode) {
                AndroidKey.KEYCODE_DPAD_LEFT -> if (down) mediaFocus = (mediaFocus - 1).coerceAtLeast(0)
                AndroidKey.KEYCODE_DPAD_RIGHT -> if (down) mediaFocus = (mediaFocus + 1).coerceAtMost(2)
                // Up goes back to the top buttons (the mode stays Top).
                AndroidKey.KEYCODE_DPAD_UP -> if (down) mediaFocus = -1
                // Down goes on to the apps.
                AndroidKey.KEYCODE_DPAD_DOWN -> if (down) {
                    mediaFocus = -1
                    state.mode = Mode.Browse
                }
                AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
                AndroidKey.KEYCODE_BUTTON_A -> if (down && e.repeatCount == 0) {
                    when (mediaFocus) {
                        0 -> NowPlaying.previous()
                        1 -> NowPlaying.togglePlayPause()
                        else -> NowPlaying.next()
                    }
                }
                AndroidKey.KEYCODE_BACK -> if (down && e.repeatCount == 0) {
                    mediaFocus = -1
                    state.mode = Mode.Browse
                }
                else -> handled = false
            }
            if (handled) return true
        }

        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> {
                if (down) {
                    if (e.repeatCount == 0) {
                        state.centerDownAt = e.eventTime
                        state.longFired = false
                        state.pressed = state.mode == Mode.Browse
                    } else if (!state.longFired && state.centerDownAt >= 0 && e.eventTime - state.centerDownAt > LONG_PRESS_MS) {
                        state.longFired = true
                        state.pressed = false
                        longPress()
                    }
                } else {
                    state.pressed = false
                    // A release without a press (OK held in the app we just left) is ignored.
                    if (state.centerDownAt >= 0 && !state.longFired) select()
                    state.centerDownAt = -1
                }
                return true
            }
            AndroidKey.KEYCODE_DPAD_LEFT -> if (down) move(-1, 0)
            AndroidKey.KEYCODE_DPAD_RIGHT -> if (down) move(1, 0)
            AndroidKey.KEYCODE_DPAD_UP -> if (down) move(0, -1)
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) move(0, 1)
            AndroidKey.KEYCODE_BACK -> if (down) {
                if (e.repeatCount == 0) {
                    back()
                } else if (state.mode == Mode.Browse && e.eventTime - e.downTime > 700) {
                    // Holding Back on the home screen opens the recent apps.
                    Recents.open = true
                }
            }
            AndroidKey.KEYCODE_MENU -> if (down && e.repeatCount == 0) menuKey()
            AndroidKey.KEYCODE_APP_SWITCH -> if (down && e.repeatCount == 0) Recents.open = true
            AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE -> if (down) {
                if (state.mode == Mode.Jiggle) {
                    state.menuIndex = 0
                    state.mode = Mode.Menu
                } else if (!NowPlaying.togglePlayPause()) {
                    return false
                }
            }
            AndroidKey.KEYCODE_MEDIA_NEXT -> if (down && !NowPlaying.next()) return false
            AndroidKey.KEYCODE_MEDIA_PREVIOUS -> if (down && !NowPlaying.previous()) return false
            // Blue button: opens the weather panel (from the home screen and the top row).
            AndroidKey.KEYCODE_PROG_BLUE -> {
                if (forecastDays.isEmpty() || (state.mode != Mode.Browse && state.mode != Mode.Top)) return false
                if (down && e.repeatCount == 0) {
                    forecastDay = 0
                    forecastOpen = true
                }
            }
            else -> return false
        }
        return true
    }

    // Control Center > Dock & icons > App lock: removing the PIN and every lock needs the PIN first.
    LaunchedEffect(DockSettings.resetLocksRequested) {
        if (DockSettings.resetLocksRequested) {
            DockSettings.consumeResetRequest()
            requirePin("Enter your PIN to remove all app locks") { DockSettings.clearLocks() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                backdrop.screen = it
                screenWpx = it.width
                screenHeightDp = with(density) { it.height.toDp().value }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(::onKey),
    ) {
        // ---- The whole home screen (wallpaper, clock, dock, apps, panels) is one "scene". ----
        // It is recorded so a snapshot can be taken, and it is blurred while the recent apps or the
        // Control Center are open. It also grows a little while the weather panel opens over it.
        // The Control Center panel itself is drawn OUTSIDE this scene, so it stays sharp.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + 0.04f * islandAnim.value
                    scaleX = s
                    scaleY = s
                    if (Build.VERSION.SDK_INT >= 31) {
                        // Live blur of everything in the scene (Android 12+).
                        val mix = maxOf(recentsMix, (ccAnim.value * 1.6f).coerceAtMost(1f))
                        val px = 24.dp.toPx() * mix
                        renderEffect = if (px > 0.5f) BlurEffect(px, px) else null
                    }
                }
                .drawWithContent {
                    sceneLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(sceneLayer)
                },
        ) {
            // Background: sharp at the top of the home screen, blurred and dimmed once you move into the grid.
            if (DepthSettings.on && !RoomSettings.on) {
                // The depth wallpaper: your photo, then the big clock (with the weather under it), then the foreground cutout on top of the clock.
                DepthBackground(depthBack, Modifier.fillMaxSize())
                depthFrosted?.let {
                    Image(
                        it, null, Modifier.fillMaxSize().graphicsLayer { alpha = blur },
                        contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
                    )
                }
                DepthClock(
                    Modifier.fillMaxSize().graphicsLayer { alpha = depthClockAlpha },
                    weather = realWeather?.let { w ->
                        @Composable { WeatherPill(backdrop, w, false, Modifier, visible = true) }
                    },
                )
                depthFront?.let {
                    Image(
                        it, null, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - blur },
                        contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
                    )
                }
            } else if (RoomSettings.on) {
                // The gamer room. It glides sideways as the focus moves along the dock.
                GamerRoomBackground(roomImage, roomFx, Modifier.fillMaxSize())
                roomFrosted?.let {
                    GamerRoomBackground(it, roomFx, Modifier.fillMaxSize().graphicsLayer { alpha = blur })
                }
            } else {
                Crossfade(wallpaper, animationSpec = tween(700), label = "wallpaper") { img ->
                    if (img != null) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
                    }
                }
                backdrop.frosted?.let {
                    Image(
                        it, null, Modifier.fillMaxSize().graphicsLayer { alpha = blur },
                        contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
                    )
                }
            }
            Crossfade(ambience, animationSpec = tween(600), label = "topShelf") { img ->
                if (img != null) {
                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
                }
            }
            // The gamer room already shows the rain in its window, so the full-screen rain is not drawn on top of it.
            if (raining && !RoomSettings.on) RainOverlay(Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.5f)),
                ),
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blur }.background(Color.Black.copy(alpha = 0.22f)))

            // Glass dock under the first row; it fades out when you move into the grid.
            val dockAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "dock")
            val headerAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "header")
            if (headerAlpha > 0f) {
                // On the sky wallpaper the left column must stay left of the TV and above the dock.
                val tvLeft = (TvScreen.LEFT * 960f).dp
                val columnHeight = maxOf(0.dp, topRowY - DockTop - HeaderTop - 10.dp)

                // Header (greeting, clock, date) with the weather text right next to it,
                // the verse of the moment under the date, and (sky wallpaper) the now playing card under the verse.
                // The depth wallpaper is the simple mode: it has its own big clock instead.
                if (!DepthSettings.on) {
                    Column(
                        Modifier
                            .padding(start = Metrics.Margin, top = HeaderTop)
                            .then(if (tvWallpaper) Modifier.height(columnHeight) else Modifier)
                            .graphicsLayer { alpha = headerAlpha },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The greeting is outlined while it is selected (Up from the top buttons).
                            // The clock under it follows the style chosen in Control Center > Clock.
                            Box(Modifier.focusOutline(nameFocus)) {
                                HomeHeader(repo.userName, Modifier, repo.clockStyle, Features.clock)
                            }
                        }
                        if (Features.verse) {
                            VerseText(
                                backdrop, Modifier.padding(top = 8.dp),
                                maxWidth = if (tvWallpaper) maxOf(160.dp, tvLeft - Metrics.Margin - 14.dp) else 420.dp,
                            )
                        }
                        if (tvWallpaper && Features.music) {
                            // The card stays in the layout while the Control Center is open (just invisible),
                            // because the flying copy of it takes over from here and lands back here.
                            FitScale(Modifier.weight(1f).padding(top = 10.dp)) {
                                NowPlayingCard(
                                    backdrop, mediaFocus,
                                    Modifier
                                        .graphicsLayer { alpha = if (mediaShown) 0f else 1f }
                                        .onGloballyPositioned { if (!mediaShown) cardBounds = it.boundsInRoot() },
                                )
                            }
                        }
                    }
                }
                // The icon bar always stays composed, so its icons can shrink away when the Control Center
                // opens and grow back when it closes.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = Metrics.Margin).graphicsLayer { alpha = headerAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (weather != null && weatherOn) {
                        // The weather pill: selected with Left from the Wi-Fi button. It shrinks away with the icons
                        // while the Control Center opens, and hides while the weather panel is open (the panel
                        // draws it itself). Simple mode has no pill here: its weather sits under the big clock.
                        WeatherPill(
                            backdrop, weather, weatherFocus,
                            Modifier
                                .graphicsLayer { alpha = if (islandAnim.value > 0.001f) 0f else 1f }
                                .onGloballyPositioned { if (!islandShown) weatherBounds = it.boundsInRoot() },
                            visible = state.mode != Mode.Control,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    IconBar(
                        backdrop,
                        if (state.mode == Mode.Top && mediaFocus < 0 && !nameFocus && !weatherFocus) state.topIndex else -1,
                        Modifier,
                        visible = state.mode != Mode.Control,
                    )
                }
                // Now playing: visible while a music app is running (playing or paused).
                // With the sky wallpaper it sits under the verse instead (see above).
                if (!tvWallpaper && Features.music) {
                    NowPlayingCard(
                        backdrop, mediaFocus,
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 84.dp, end = Metrics.Margin)
                            .graphicsLayer { alpha = headerAlpha * (if (mediaShown) 0f else 1f) }
                            .onGloballyPositioned { if (!mediaShown) cardBounds = it.boundsInRoot() },
                    )
                }
            }
            if (DockSettings.dockOn && dockAlpha > 0f) {
                Glass(
                    backdrop,
                    Modifier
                        .offset {
                            IntOffset(
                                (Metrics.RowStart - DockInset).roundToPx(),
                                (topRowY - DockTop - scroll.value.dp).roundToPx() + (hideSlide * 240.dp.toPx()).roundToInt(),
                            )
                        }
                        .size(Metrics.RowWidth + DockInset * 2, DockHeight)
                        .graphicsLayer { alpha = dockAlpha },
                    shape = RoundedCornerShape(36.dp),
                    tint = Color.White.copy(alpha = when (DockSettings.glass) { 0 -> 0.02f; 2 -> 0.18f; else -> 0.06f }),
                    source = ambience,
                )
            }

            // Grid: only rows near the selection are composed.
            val visibleRows = (row - 2)..(row + 3)
            apps.forEachIndexed { i, entry ->
                if (i / Metrics.COLS in visibleRows) key(entry.pkg) {
                    // When an app is moved or hidden, the others slide to their new place.
                    val slide = spring<Float>(dampingRatio = 0.85f, stiffness = 320f)
                    val x by animateFloatAsState(tileX(i).value, slide, label = "x")
                    val baseY by animateFloatAsState((topRowY + Metrics.Pitch * (i / Metrics.COLS)).value, slide, label = "y")
                    AppTile(
                        entry = entry,
                        visual = repo.visuals[entry.pkg],
                        focused = i == state.focus && state.mode != Mode.Control && state.mode != Mode.Top,
                        pressed = i == state.focus && state.pressed,
                        jiggling = state.mode == Mode.Jiggle && i == state.focus,
                        showLabel = when (DockSettings.label) { 0 -> false; 1 -> i >= Metrics.COLS; else -> true },
                        labelAlways = DockSettings.label == 2,
                        locked = DockSettings.isLocked(entry.pkg),
                        nudge = state.nudge,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x.dp.roundToPx(),
                                    (baseY - scroll.value).dp.roundToPx() +
                                        (if (i < Metrics.COLS) (hideSlide * 240.dp.toPx()).roundToInt() else 0),
                                )
                            }
                            .graphicsLayer {
                                // Rows scrolling off the top fade out, as on tvOS.
                                val y = baseY - scroll.value
                                alpha = ((y + 60f) / 100f).coerceIn(0f, 1f) * (if (i >= Metrics.COLS) lowerRows else 1f)
                            },
                    )
                }
            }

            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))

            AnimatedVisibility(
                state.mode == Mode.Jiggle, Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                HintPill(backdrop, stringResource(R.string.hint_move, focused?.label.orEmpty()), Modifier)
            }

            if (focused != null) {
                val tx = tileX(state.focus).value
                val ty = tileY(state.focus)
                val menuHeight = 46f + 50f * menu.size
                val above = ty - menuHeight - 14f > 16f
                val mx = (tx + Metrics.TileW.value / 2 - MenuWidth.value / 2)
                    .coerceIn(Metrics.Margin.value, 960f - Metrics.Margin.value - MenuWidth.value)
                val my = if (above) ty - menuHeight - 14f else ty + Metrics.TileH.value + 40f
                AnimatedVisibility(
                    state.mode == Mode.Menu, Modifier.offset(mx.dp, my.dp),
                    enter = fadeIn(tween(180)) + scaleIn(
                        spring(dampingRatio = 0.75f, stiffness = 600f), 0.9f, TransformOrigin(0.5f, if (above) 1f else 0f),
                    ),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f),
                ) {
                    ContextMenu(backdrop, focused.label, menu, state.menuIndex, Modifier)
                }
            }
        }
        // ---- End of the scene. ----

        // Android 7-11: the blurred snapshot of the scene, faded in.
        frozen?.let {
            Image(
                it, null,
                Modifier.fillMaxSize().graphicsLayer { alpha = frozenAlpha },
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low,
            )
        }

        // Lyrics of the song that is playing, on the TV screen of the sky wallpaper. They are drawn outside
        // the blurred scene (the scene is one recorded layer, which can hide them) and blur themselves.
        if (lyricsAlpha > 0f && tvWallpaper && Features.lyrics) {
            TvLyrics(
                Modifier.fillMaxSize().graphicsLayer { alpha = lyricsAlpha },
                blurAmount = maxOf(blur, recentsMix),
            )
        }

        // The now playing card flies to the left of the Control Center and turns into the square tile
        // (and back when it closes). It is drawn outside the blurred scene, so it stays sharp.
        if (mediaShown && Features.music && screenWpx > 0) {
            val tileRect = with(density) {
                val w = MediaTileW.toPx()
                val right = screenWpx - PanelEnd.toPx() - PanelWidth.toPx() - MediaTileGap.toPx()
                // Level with the Wi-Fi row at the top and the Miracast row at the bottom (measured from the
                // panel itself). Before the panel has been laid out, a close guess is used for one frame.
                val slot = cc.mediaSlot()
                val top = slot?.first ?: (PanelTop + 8.dp + 58.dp).toPx()
                val bottom = slot?.second ?: (top + MediaTileH.toPx())
                androidx.compose.ui.geometry.Rect(right - w, top, right, bottom)
            }
            MediaMorph(
                backdrop = backdrop,
                progress = mediaMorph,
                from = if (cardShown && row == 0) cardBounds else null,
                to = tileRect,
                alpha = tilePageAlpha,
                focus = tileFocus,
            )
        }

        // Control Center: the panel grows out of the top-right corner, then its tiles spread out
        // one after another. It sits above the blurred scene, so it stays sharp. It stays composed
        // until the (faster) closing animation has finished.
        if (ccShown) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = PanelTop, end = PanelEnd)) {
                cc.Panel(backdrop, wallpaperName, ccReveal, Modifier)
            }
        }

        // Recent apps: a cascade of cards (double press of Home, or hold Back).
        val recentsAlpha by animateFloatAsState(if (Recents.open) 1f else 0f, tween(220), label = "recents")
        if (recentsAlpha > 0.01f) {
            RecentCards(
                items = recentApps, selected = recentIndex, alpha = recentsAlpha, itemKey = { it.pkg },
                label = { it.label },
            ) { entry ->
                AppTile(
                    entry = entry,
                    visual = repo.visuals[entry.pkg],
                    focused = false,
                    pressed = false,
                    jiggling = false,
                    showLabel = true,
                    nudge = state.nudge,
                    modifier = Modifier,
                )
            }
        }
        // TV inputs (HDMI 1, HDMI 2...) under the top buttons.
        if (inputOpen) {
            InputPicker(
                backdrop, inputs.map { it.label }, inputIndex,
                Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = Metrics.Margin),
            )
        }
        // The weather panel: it grows out of the small weather text and fills the screen.
        // It stays composed until the closing animation has finished.
        if (islandShown && weather != null && forecastDays.isNotEmpty()) {
            WeatherIsland(
                weather, forecastDays, forecastDay, weatherBounds,
                progress = { islandAnim.value },
            )
        }
        // The city picker.
        if (locOpen) {
            LocationDialog(backdrop, locTyping, locResults.map { it.label }, locIndex, locStatus) { query ->
                startSearch(query)
            }
        }
        // The name after the greeting.
        if (editingName) {
            NameDialog(backdrop, repo.userName) { name ->
                repo.setName(name)
                editingName = false
            }
        }
        // The PIN dialog (app lock). It is drawn above everything, Control Center included.
        if (pinMode != null) {
            PinDialog(
                backdrop,
                when (pinMode) {
                    PinMode.SetNew -> "Set a PIN"
                    PinMode.Confirm -> "Confirm your PIN"
                    else -> "Enter PIN"
                },
                pinMessage, pinDigits, pinPos,
            )
        }
        cc.PasswordLayer(backdrop) { focusRequester.requestFocus() }

    }

}
