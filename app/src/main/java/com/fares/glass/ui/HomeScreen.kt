package com.fares.glass.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur as blurEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fares.glass.R
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository
import com.fares.glass.data.Weather
import com.fares.glass.sys.NowPlaying
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
private val DockInset = 20.dp
private val DockTop = 18.dp
/** Covers the row and the focus zoom (no names under dock icons). */
private val DockHeight = Metrics.TileH + 36.dp

/** Top padding of the greeting column. */
private val HeaderTop = 30.dp

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
) {
    val apps = repo.apps
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
        }
    }
    // The name keyboard was closed: the remote drives the home screen again.
    LaunchedEffect(editingName) { if (!editingName) focusRequester.requestFocus() }
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

    // Recent apps: a stack of cards, opened with a double press of Home or by holding Back.
    var recentIndex by remember { mutableIntStateOf(0) }
    val recentApps = Recents.list.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
    LaunchedEffect(Recents.open) {
        if (Recents.open) {
            recentIndex = 0
            mediaFocus = -1
            if (state.mode == Mode.Control) cc.close() else state.mode = Mode.Browse
        }
    }

    // The dock sits low enough that the second row of apps stays below the bottom edge of the screen.
    var screenHeightDp by remember { mutableFloatStateOf(540f) }
    val dockDrop = run {
        val rowOneTop = Metrics.TopRowY.value + Metrics.Pitch.value
        val dockBottom = Metrics.TopRowY.value - DockTop.value + DockHeight.value
        val room = screenHeightDp - 12f - dockBottom   // how far the dock can go down and still fit on screen
        val wanted = screenHeightDp - rowOneTop + 4f   // how far it must go to hide the second row
        wanted.coerceIn(0f, room.coerceAtLeast(0f))
    }.dp
    val topRowY = Metrics.TopRowY + dockDrop

    val row = state.row
    val scrollTarget = if (row == 0) 0f else (topRowY + Metrics.Pitch * row - Metrics.GridRowY).value
    val scroll = animateFloatAsState(scrollTarget, spring(dampingRatio = 1f, stiffness = 210f), label = "scroll")
    val inGrid = row > 0 || state.mode == Mode.Control
    // The frosted wallpaper also fades in while the recent apps are showing.
    val blur by animateFloatAsState(if (inGrid || Recents.open) 1f else 0f, tween(450), label = "blur")
    // 0 -> 1 while the recent apps open: drives the blur of the home items.
    val recentsMix by animateFloatAsState(if (Recents.open) 1f else 0f, tween(350), label = "recentsMix")
    // Android 12+: a real blur of the home items. Older TVs: the home items just fade (see below).
    val contentBlur = if (Build.VERSION.SDK_INT >= 31 && recentsMix > 0.01f) {
        Modifier.blurEffect((24f * recentsMix).dp)
    } else {
        Modifier
    }
    val dim by animateFloatAsState(
        if (state.mode == Mode.Control || state.mode == Mode.Menu) 0.3f else 0f, tween(250), label = "dim",
    )
    val focused = apps.getOrNull(state.focus)
    val ambience = if (repo.topShelf && row == 0 && state.mode == Mode.Browse) {
        focused?.let { repo.visuals[it.pkg]?.ambience }
    } else null

    val menu = menuItems()

    fun tileX(i: Int) = Metrics.Margin + (Metrics.TileW + Metrics.Gap) * (i % Metrics.COLS)
    fun tileY(i: Int) = (topRowY + Metrics.Pitch * (i / Metrics.COLS)).value - scrollTarget

    fun launchFocused() {
        val entry = focused ?: return
        val r = with(density) {
            val x = tileX(state.focus).roundToPx()
            val y = tileY(state.focus).dp.roundToPx()
            Rect(x, y, x + Metrics.TileW.roundToPx(), y + Metrics.TileH.roundToPx())
        }
        onLaunch(entry, r)
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
                // The greeting is selected: Down goes back to the top buttons.
                nameFocus -> if (dy > 0) nameFocus = false
                // Up from the top buttons: the greeting (and its name).
                dy < 0 -> nameFocus = true
                // Down from the top buttons: the now playing card if it is showing, otherwise the apps.
                dy > 0 -> if (cardShown) mediaFocus = 1 else state.mode = Mode.Browse
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
                if (entry != null) when (state.menuIndex) {
                    0 -> state.mode = Mode.Jiggle
                    1 -> repo.hide(entry.pkg)
                    2 -> onCommand(Command.AppInfo(entry.pkg))
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
                    onLaunch(entry, Rect(w / 2 - w / 8, h / 2 - h / 8, w / 2 + w / 8, h / 2 + h / 8))
                }
            }
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
        if (Recents.open) return recentsKey(e)
        if (editingName) {
            // The text field and the on-screen keyboard handle the keys; Back cancels.
            if (e.keyCode == AndroidKey.KEYCODE_BACK) {
                if (e.action == AndroidKey.ACTION_UP) editingName = false
                return true
            }
            return false
        }
        if (inputOpen) return inputKey(e)
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
            else -> return false
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                backdrop.screen = it
                screenHeightDp = with(density) { it.height.toDp().value }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(::onKey),
    ) {
        // Background: sharp at the top of the home screen, blurred and dimmed once you move into the grid.
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
        Crossfade(ambience, animationSpec = tween(600), label = "topShelf") { img ->
            if (img != null) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
            }
        }
        if (raining) RainOverlay(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.5f)),
            ),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = blur }.background(Color.Black.copy(alpha = 0.22f)))

        // ---- Everything on the home screen (clock, dock, apps, panels). It blurs while recents is open. ----
        Box(
            Modifier.fillMaxSize().then(contentBlur).graphicsLayer {
                // Android 7-11 can't blur live: the home items just fade out instead.
                alpha = if (Build.VERSION.SDK_INT < 31) 1f - 0.7f * recentsMix else 1f
            },
        ) {

        // Glass dock under the first row; it fades out when you move into the grid.
        val dockAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "dock")
        val headerAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "header")
        if (headerAlpha > 0f) {
            // Lyrics of the song that is playing, on the TV screen of the sky wallpaper.
            if (tvWallpaper && Features.lyrics) {
                TvLyrics(Modifier.fillMaxSize().graphicsLayer { alpha = headerAlpha }, blurAmount = blur)
            }

            // On the sky wallpaper the left column must stay left of the TV and above the dock.
            val tvLeft = (TvScreen.LEFT * 960f).dp
            val columnHeight = maxOf(0.dp, topRowY - DockTop - HeaderTop - 10.dp)

            // Header (greeting, clock, date) with the weather text right next to it,
            // the verse of the moment under the date, and (sky wallpaper) the now playing card under the verse.
            Column(
                Modifier
                    .padding(start = Metrics.Margin, top = HeaderTop)
                    .then(if (tvWallpaper) Modifier.height(columnHeight) else Modifier)
                    .graphicsLayer { alpha = headerAlpha },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The greeting is outlined while it is selected (Up from the top buttons).
                    Box(
                        Modifier.drawBehind {
                            if (nameFocus) {
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(-10.dp.toPx(), -6.dp.toPx()),
                                    size = Size(size.width + 20.dp.toPx(), size.height + 12.dp.toPx()),
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                        },
                    ) {
                        HomeHeader(repo.userName, Modifier)
                    }
                    if (weather != null && Features.weather) {
                        Spacer(Modifier.width(28.dp))
                        WeatherWidget(weather)
                    }
                }
                if (Features.verse) {
                    VerseText(
                        backdrop, Modifier.padding(top = 8.dp),
                        maxWidth = if (tvWallpaper) maxOf(160.dp, tvLeft - Metrics.Margin - 14.dp) else 420.dp,
                    )
                }
                if (tvWallpaper && state.mode != Mode.Control && Features.music) {
                    FitScale(Modifier.weight(1f).padding(top = 10.dp)) {
                        NowPlayingCard(backdrop, mediaFocus, Modifier)
                    }
                }
            }
            if (state.mode != Mode.Control) {
                IconBar(
                    backdrop,
                    if (state.mode == Mode.Top && mediaFocus < 0 && !nameFocus) state.topIndex else -1,
                    Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = Metrics.Margin).graphicsLayer { alpha = headerAlpha },
                )
                // Now playing: visible while a music app is running (playing or paused).
                // With the sky wallpaper it sits under the verse instead (see above).
                if (!tvWallpaper && Features.music) {
                    NowPlayingCard(
                        backdrop, mediaFocus,
                        Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = Metrics.Margin).graphicsLayer { alpha = headerAlpha },
                    )
                }
            }
        }
        if (dockAlpha > 0f) {
            Glass(
                backdrop,
                Modifier
                    .offset { IntOffset((Metrics.Margin - DockInset).roundToPx(), (topRowY - DockTop - scroll.value.dp).roundToPx()) }
                    .size(960.dp - (Metrics.Margin - DockInset) * 2, DockHeight)
                    .graphicsLayer { alpha = dockAlpha },
                shape = RoundedCornerShape(36.dp),
                tint = Color.White.copy(alpha = 0.06f),
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
                    showLabel = i >= Metrics.COLS,
                    nudge = state.nudge,
                    modifier = Modifier
                        .offset { IntOffset(x.dp.roundToPx(), (baseY - scroll.value).dp.roundToPx()) }
                        .graphicsLayer {
                            // Rows scrolling off the top fade out, as on tvOS.
                            val y = baseY - scroll.value
                            alpha = ((y + 60f) / 100f).coerceIn(0f, 1f)
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

        AnimatedVisibility(
            state.mode == Mode.Control, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 24.dp),
            enter = fadeIn(tween(220)) + slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 2 },
        ) {
            cc.Panel(backdrop, wallpaperName, Modifier)
        }

        if (focused != null) {
            val tx = tileX(state.focus).value
            val ty = tileY(state.focus)
            val above = ty - MenuHeight.value - 14f > 16f
            val mx = (tx + Metrics.TileW.value / 2 - MenuWidth.value / 2)
                .coerceIn(Metrics.Margin.value, 960f - Metrics.Margin.value - MenuWidth.value)
            val my = if (above) ty - MenuHeight.value - 14f else ty + Metrics.TileH.value + 40f
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
        // ---- End of the blurred home items. ----

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
        // The name after the greeting.
        if (editingName) {
            NameDialog(backdrop, repo.userName) { name ->
                repo.setName(name)
                editingName = false
            }
        }
        cc.PasswordLayer(backdrop) { focusRequester.requestFocus() }

    }

}
