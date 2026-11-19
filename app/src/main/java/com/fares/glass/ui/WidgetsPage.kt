package com.fares.glass.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.Weather
import com.fares.glass.data.WeatherRepository
import com.fares.glass.sys.MemoryBoost
import com.fares.glass.sys.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import com.fares.glass.data.LyricLine
import com.fares.glass.data.LyricsRepository

/**
 * The motion of the widgets page. HomeScreen uses it too, so both pages move as one strip:
 * the page slides in from the left edge while the home screen is pushed out to the right, and
 * both are blurred while they move. The blur is strongest in the middle of the slide and is
 * gone once the page has settled (the same as swiping to the widgets page on an iPhone).
 */
object WidgetsMotion {
    const val DurationMs = 550
    val Easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
    val MaxBlur = 22.dp

    /** 0 = at rest (page hidden or shown), 1 = the middle of the slide. [p] is the animation progress, 0..1. */
    fun blurMix(p: Float): Float = (4f * p * (1f - p)).coerceIn(0f, 1f)
}

/**
 * The entrance of the tiles after the page opens. One value (0..1, [TotalMs] long) drives everything:
 * each tile rises, fades and settles with a small bounce, one after another, and then the inside of
 * each tile comes alive (the calendar days pop in as a wave, the sun arc draws itself and the sun
 * travels along it, the screen time bar grows, the numbers fade up).
 */
object WidgetsEnter {
    const val TotalMs = 1700
    const val StartMs = 140f
    const val StaggerMs = 90f
    const val TileMs = 650f

    /** 0..1 progress of a piece that starts [slot] steps after the first tile (+ [extraMs]) and lasts [durMs]. */
    fun local(raw: Float, slot: Float, durMs: Float = TileMs, extraMs: Float = 0f): Float =
        ((raw * TotalMs - (StartMs + slot * StaggerMs + extraMs)) / durMs).coerceIn(0f, 1f)

    /** Ease out with a small overshoot (the pop at the end). */
    fun back(t: Float): Float {
        val c1 = 1.1f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Plain ease out. */
    fun out(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }
}

private val CardColor = Color(0xFF1C1C1E)
private val Orange = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF453A)
private val SunYellow = Color(0xFFFFC93C)
private val UsageColors = listOf(Color(0xFF378ADD), Color(0xFFD85A30), Color(0xFFD4537E), Color(0xFF1D9E75))
private val UsageOther = Color(0xFF5F5E5A)

/** A dark rounded widget card. A selected one gets a white outline and grows a little. */
private fun Modifier.widgetCard(shape: Shape, focused: Boolean = false): Modifier = this
    .graphicsLayer {
        val s = if (focused) 1.02f else 1f
        scaleX = s
        scaleY = s
    }
    .background(CardColor.copy(alpha = 0.58f), shape)
    .border(
        if (focused) 3.dp else 1.dp,
        if (focused) Color.White else Color.White.copy(alpha = 0.14f),
        shape,
    )

/** A tile rises from below, fades in and grows to full size with a small bounce, in its turn ([slot] 0, 1, 2, 3). */
private fun Modifier.enterTile(raw: () -> Float, slot: Int): Modifier = graphicsLayer {
    val l = WidgetsEnter.local(raw(), slot.toFloat())
    val e = WidgetsEnter.back(l)
    alpha = WidgetsEnter.out((l * 1.8f).coerceAtMost(1f))
    translationY = (1f - e) * 44.dp.toPx()
    val sc = 0.9f + 0.1f * e
    scaleX = sc
    scaleY = sc
}

/** The widget open full screen (-1 = none). HomeScreen reads it, so Back closes an open card before the page. */
object WidgetsExpansion {
    var widget by mutableIntStateOf(-1)
}

/** Where each widget sits on the screen. It is filled in while the page is laid out, and read when a card opens. */
private class WidgetSlots {
    val coords = arrayOfNulls<LayoutCoordinates>(6)
    var container: LayoutCoordinates? = null
}

/** How long OK must be held to open a card full screen. */
private const val WIDGET_LONG_PRESS_MS = 450L

/**
 * The widgets page (landscape, Android TV). The first screen has the weather card and the month calendar on top,
 * and the sun card and screen time under them. Below that come Boost RAM and the now playing vinyl.
 * Up / Down move the focus, and the page glides down to show the row that has it.
 * A short OK does the card's own action. Holding OK opens the card full screen, and Back closes it again.
 * HomeScreen shows the page when Left is pressed on the first app and slides it in from the left (see [WidgetsMotion]).
 *
 * [progress] is read only while drawing (0 = hidden, 1 = shown), so animating it never recomposes the screen.
 * Animate it with tween(WidgetsMotion.DurationMs, easing = WidgetsMotion.Easing).
 */
@Composable
fun WidgetsPage(
    backdrop: Backdrop,
    weather: Weather?,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current

    // The entrance of the tiles (the same as before).
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(WidgetsEnter.TotalMs, easing = LinearEasing)) }
    val raw: () -> Float = { enter.value }

    // Focus: 0 weather, 1 calendar, 2 sun, 3 screen time, 4 boost RAM, 5 vinyl.
    // The widgets sit in rows of two: 0-1, 2-3, 4-5.
    var focus by remember { mutableIntStateOf(0) }
    // On the vinyl: 0 = previous, 1 = play / pause, 2 = next.
    var vinylButton by remember { mutableIntStateOf(1) }
    // OK presses on the small Boost RAM card and on the big one. They are counted apart, so one press runs one boost.
    var boostPress by remember { mutableIntStateOf(0) }
    var expandedBoostPress by remember { mutableIntStateOf(0) }
    // The widget in the full-screen view. It stays set while the card shrinks back.
    var shownWidget by remember { mutableIntStateOf(-1) }
    // Where the full-screen card starts (and where it lands when it closes): the small card's place.
    var fromRect by remember { mutableStateOf<Rect?>(null) }
    // OK timing, to tell a short press from a long one.
    var centerDownAt by remember { mutableLongStateOf(-1L) }
    var longFired by remember { mutableStateOf(false) }
    val slots = remember { WidgetSlots() }

    val scroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    DisposableEffect(Unit) { onDispose { WidgetsExpansion.widget = -1 } }

    // 0 = small card, 1 = full screen. A spring, so the card settles with a small bounce.
    val expandP by animateFloatAsState(
        if (WidgetsExpansion.widget >= 0) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "widgetExpand",
    )
    val overlayShown by remember { derivedStateOf { WidgetsExpansion.widget >= 0 || expandP > 0.002f } }

    // Where a small card is, in the page's own coordinates (null if it is not laid out yet).
    fun cardRect(i: Int): Rect? {
        val card = slots.coords[i] ?: return null
        val box = slots.container ?: return null
        if (!card.isAttached || !box.isAttached) return null
        val at = box.localPositionOf(card, Offset.Zero)
        return Rect(at, Size(card.size.width.toFloat(), card.size.height.toFloat()))
    }

    fun openCard(i: Int) {
        fromRect = cardRect(i)
        shownWidget = i
        WidgetsExpansion.widget = i
    }

    fun closeCard() {
        fromRect = cardRect(shownWidget)
        WidgetsExpansion.widget = -1
    }

    // Left / Right move across the row. While a card is open they only move between the vinyl buttons.
    fun moveSideways(dx: Int) {
        if (WidgetsExpansion.widget >= 0) {
            if (focus == 5) vinylButton = (vinylButton + dx).coerceIn(0, 2)
            return
        }
        when {
            focus == 5 && dx > 0 -> {
                if (vinylButton < 2) vinylButton++
            }
            focus == 5 && dx < 0 -> {
                if (vinylButton > 0) vinylButton-- else focus = 4
            }
            dx > 0 && focus % 2 == 0 -> {
                focus++
                if (focus == 5) vinylButton = 1
            }
            dx < 0 && focus % 2 == 1 -> {
                focus--
            }
        }
    }

    // Up / Down move between the rows. Nothing moves while a card is open.
    fun moveUpDown(dy: Int) {
        if (WidgetsExpansion.widget >= 0) return
        if (dy > 0 && focus <= 3) focus += 2
        if (dy < 0 && focus >= 2) focus -= 2
    }

    // A short OK: the card's own action.
    fun shortPress() {
        when (focus) {
            // Screen time: turns on Usage access when it is still off.
            3 -> {
                if (!hasUsageAccess(ctx)) openUsageAccessSettings(ctx)
            }
            4 -> {
                if (WidgetsExpansion.widget >= 0) expandedBoostPress++ else boostPress++
            }
            5 -> when (vinylButton) {
                0 -> NowPlaying.previous()
                1 -> NowPlaying.togglePlayPause()
                else -> NowPlaying.next()
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress()
                // One strip with the home screen: the page enters from the left edge (-1 screen width -> 0).
                translationX = (p - 1f) * size.width
                // Blurred while it moves, sharp once it has settled (Android 12+).
                if (Build.VERSION.SDK_INT >= 31) {
                    val px = WidgetsMotion.MaxBlur.toPx() * WidgetsMotion.blurMix(p)
                    renderEffect = if (px > 0.5f) BlurEffect(px, px) else null
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                val n = e.nativeKeyEvent
                val down = e.type == KeyEventType.KeyDown
                val up = e.type == KeyEventType.KeyUp
                if (!down && !up) return@onKeyEvent false
                val expanded = WidgetsExpansion.widget >= 0
                when (e.key) {
                    Key.Back -> {
                        // Back only closes an open card. Otherwise HomeScreen closes the page.
                        if (!expanded) return@onKeyEvent false
                        if (down && n.repeatCount == 0) closeCard()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (down) {
                            if (n.repeatCount == 0) {
                                centerDownAt = n.eventTime
                                longFired = false
                            } else if (!longFired && !expanded && centerDownAt >= 0L &&
                                n.eventTime - centerDownAt > WIDGET_LONG_PRESS_MS
                            ) {
                                // Held: the focused card opens full screen.
                                longFired = true
                                openCard(focus)
                            }
                        } else {
                            if (!longFired && centerDownAt >= 0L) shortPress()
                            centerDownAt = -1L
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (down) moveSideways(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        if (down) moveSideways(1)
                        true
                    }
                    Key.DirectionUp -> {
                        if (down) moveUpDown(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        if (down) moveUpDown(1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        // The page brings its own background (the soft wallpaper, dimmed), so it slides in as a whole page.
        backdrop.frosted?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { slots.container = it }) {
            val gap = 14.dp
            // The first screen keeps its old proportions: row 1 = 150 parts, row 2 = 112 parts.
            val rowsH = maxHeight - 72.dp - gap
            val row1H = rowsH * 150f / 262f
            val row2H = rowsH * 112f / 262f
            val row3H = row2H

            // The page glides down until the bottom of the focused row is on screen.
            val rowBottom = when (focus / 2) {
                0 -> row1H
                1 -> row1H + gap + row2H
                else -> row1H + gap + row2H + gap + row3H
            }
            val target = (rowBottom + 72.dp - maxHeight).coerceAtLeast(0.dp)
            val density = LocalDensity.current
            LaunchedEffect(target) {
                scroll.animateScrollTo(
                    with(density) { target.roundToPx() },
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = Metrics.Margin, end = Metrics.Margin, top = 36.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(Modifier.fillMaxWidth().height(row1H), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    WidgetFocusFrame(focus == 0, 0, slots, Modifier.weight(1f).fillMaxHeight()) {
                        WeatherCard(backdrop, weather, false, raw, Modifier.fillMaxSize())
                    }
                    WidgetFocusFrame(focus == 1, 1, slots, Modifier.weight(1f).fillMaxHeight()) {
                        CalendarCard(raw, Modifier.fillMaxSize())
                    }
                }
                Row(Modifier.fillMaxWidth().height(row2H), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    WidgetFocusFrame(focus == 2, 2, slots, Modifier.weight(1f).fillMaxHeight()) {
                        SunCard(weather, raw, Modifier.fillMaxSize())
                    }
                    WidgetFocusFrame(focus == 3, 3, slots, Modifier.weight(1f).fillMaxHeight()) {
                        ScreenTimeCard(raw, Modifier.fillMaxSize())
                    }
                }
                // These two are below the first screen, so they show up when the page glides down.
                Row(Modifier.fillMaxWidth().height(row3H), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    WidgetFocusFrame(focus == 4, 4, slots, Modifier.weight(1f).fillMaxHeight().riseInWidget(raw, 4f)) {
                        BoostRamCard(boostPress, Modifier.fillMaxSize())
                    }
                    WidgetFocusFrame(focus == 5, 5, slots, Modifier.weight(1f).fillMaxHeight().riseInWidget(raw, 5f)) {
                        VinylCard(rememberNowPlayingTrack(), if (focus == 5) vinylButton else -1, Modifier.fillMaxSize())
                    }
                }
            }

            // A round "back to top" button, like the one in the reference. It shows once the page has moved.
            val showTop by remember { derivedStateOf { scroll.value > 40 } }
            AnimatedVisibility(
                visible = showTop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 40.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        rememberVectorPainter(Icons.Rounded.KeyboardArrowUp),
                        null,
                        Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }
            }

            // A soft fade under the top edge, so the cards slide away gently.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
            )

            BasicText(
                "Press Back to return home",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                style = txt(Color.White.copy(alpha = 0.55f), 12.sp, FontWeight.Medium),
            )

            // The full-screen card: it grows out of the small card, and shrinks back into it.
            if (overlayShown) {
                ExpandedOverlay(
                    backdrop = backdrop,
                    progress = { expandP },
                    from = fromRect,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (shownWidget) {
                        0 -> WeatherExpandedView(weather, Modifier.fillMaxSize())
                        1 -> YearCalendarView(Modifier.fillMaxSize())
                        2 -> SunExpandedView(weather, Modifier.fillMaxSize())
                        3 -> ScreenTimeExpandedView(Modifier.fillMaxSize())
                        4 -> BoostRamCard(expandedBoostPress, Modifier.fillMaxSize(), large = true)
                        5 -> VinylPlayerView(rememberNowPlayingTrack(), vinylButton, Modifier.fillMaxSize())
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Puts the white focus outline around a widget, and remembers where it is. The widget grows a little when it has the focus. */
@Composable
private fun WidgetFocusFrame(
    focused: Boolean,
    slot: Int,
    slots: WidgetSlots,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "widgetFocus")
    Box(
        modifier
            .onGloballyPositioned { slots.coords[slot] = it }
            .graphicsLayer {
                scaleX = 1f + 0.02f * f
                scaleY = 1f + 0.02f * f
            },
    ) {
        content()
        Box(Modifier.matchParentSize().border(3.dp, Color.White.copy(alpha = f), RoundedCornerShape(30.dp)))
    }
}

/** The new widgets rise in once when the page opens, like the others. */
private fun Modifier.riseInWidget(enter: () -> Float, slot: Float): Modifier = graphicsLayer {
    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), slot))
    alpha = l
    translationY = (1f - l) * 44.dp.toPx()
}

/**
 * Shows [content] full screen. It grows out of [from] (the small card's place) as [progress] goes 0 -> 1,
 * and shrinks back into it as it goes 1 -> 0. The content is laid out at full size and then scaled,
 * so it is never squeezed.
 */
@Composable
private fun ExpandedOverlay(
    backdrop: Backdrop,
    progress: () -> Float,
    from: Rect?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.graphicsLayer {
            val p = progress()
            val fullW = size.width
            val fullH = size.height
            val r = from ?: Rect(fullW * 0.3f, fullH * 0.3f, fullW * 0.7f, fullH * 0.7f)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = mix(r.width / fullW, 1f, p)
            scaleY = mix(r.height / fullH, 1f, p)
            translationX = mix(r.left, 0f, p)
            translationY = mix(r.top, 0f, p)
            alpha = (p * 2.5f).coerceIn(0f, 1f)
            clip = true
            shape = RoundedCornerShape(((1f - p) * 28f).dp)
        },
    ) {
        backdrop.frosted?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        content()
    }
}

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t


// ---- Weather: a living sky behind the text (sun, moon, stars, clouds, rain, snow, fog, lightning) ----

private enum class WxScene { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

/**
 * The scene for a weather code. This expects the usual WMO weather codes (0 clear, 2 partly cloudy, 3 overcast,
 * 45 fog, 51+ drizzle, 61+ rain, 71+ snow, 95+ thunderstorm). If your Weather.code uses other numbers, change it here.
 */
private fun sceneOf(code: Int?): WxScene = when (code) {
    null -> WxScene.PartlyCloudy
    0, 1 -> WxScene.Clear
    2 -> WxScene.PartlyCloudy
    3 -> WxScene.Cloudy
    45, 48 -> WxScene.Fog
    51, 53, 55, 56, 57, 61, 80 -> WxScene.Drizzle
    63, 65, 66, 67, 81, 82 -> WxScene.Rain
    71, 73, 75, 77, 85, 86 -> WxScene.Snow
    95, 96, 99 -> WxScene.Storm
    else -> WxScene.PartlyCloudy
}

private fun sceneColors(sky: WxScene, night: Boolean): Pair<Color, Color> {
    val day = when (sky) {
        WxScene.Clear -> Color(0xFF2B6FD1) to Color(0xFF74B8F4)
        WxScene.PartlyCloudy -> Color(0xFF3F7FCF) to Color(0xFF8CC0EA)
        WxScene.Cloudy -> Color(0xFF6E7F92) to Color(0xFFA3AFBC)
        WxScene.Fog -> Color(0xFF8D99A5) to Color(0xFFBAC3CB)
        WxScene.Drizzle -> Color(0xFF55657A) to Color(0xFF7D8DA0)
        WxScene.Rain -> Color(0xFF3E4C5E) to Color(0xFF66768A)
        WxScene.Snow -> Color(0xFF7F91A8) to Color(0xFFBDC9D7)
        WxScene.Storm -> Color(0xFF262C38) to Color(0xFF4A5364)
    }
    if (!night) return day
    return lerp(day.first, Color(0xFF0A1230), 0.78f) to lerp(day.second, Color(0xFF1C2D52), 0.72f)
}

/** One cloud: where it starts (fractions of the tile), its size (fraction of the width), drift speed (dp/s) and opacity. */
private class WxCloud(val x: Float, val y: Float, val size: Float, val speed: Float, val alpha: Float)

private fun wxCloudsOf(sky: WxScene): List<WxCloud> = when (sky) {
    WxScene.Clear -> emptyList()
    WxScene.PartlyCloudy -> listOf(
        WxCloud(0.15f, 0.40f, 0.20f, 5f, 0.95f), WxCloud(0.62f, 0.58f, 0.24f, 3.5f, 0.90f),
        WxCloud(0.95f, 0.30f, 0.14f, 7f, 0.80f),
    )
    WxScene.Cloudy, WxScene.Snow -> listOf(
        WxCloud(0.10f, 0.30f, 0.22f, 4f, 0.95f), WxCloud(0.45f, 0.22f, 0.26f, 3f, 0.95f),
        WxCloud(0.80f, 0.38f, 0.22f, 5f, 0.90f), WxCloud(0.25f, 0.58f, 0.24f, 3.5f, 0.90f),
        WxCloud(0.65f, 0.66f, 0.26f, 4.5f, 0.90f), WxCloud(1.00f, 0.50f, 0.20f, 6f, 0.85f),
    )
    WxScene.Drizzle, WxScene.Rain, WxScene.Storm -> listOf(
        WxCloud(0.12f, 0.16f, 0.26f, 4f, 0.95f), WxCloud(0.45f, 0.12f, 0.30f, 3f, 0.95f),
        WxCloud(0.80f, 0.18f, 0.26f, 5f, 0.95f), WxCloud(0.30f, 0.34f, 0.24f, 3.5f, 0.90f),
        WxCloud(0.65f, 0.30f, 0.28f, 4f, 0.90f),
    )
    WxScene.Fog -> emptyList()
}

private fun wxCloudColors(sky: WxScene, night: Boolean): Pair<Color, Color> {
    val day = when (sky) {
        WxScene.Clear, WxScene.PartlyCloudy -> Color(0xFFFFFFFF) to Color(0xFFD5E3F0)
        WxScene.Cloudy -> Color(0xFFE3E8ED) to Color(0xFFB4BEC8)
        WxScene.Snow -> Color(0xFFEEF2F6) to Color(0xFFC9D3DD)
        WxScene.Drizzle, WxScene.Rain -> Color(0xFF8E9BAA) to Color(0xFF6B788A)
        WxScene.Storm -> Color(0xFF5C6676) to Color(0xFF414A59)
        WxScene.Fog -> Color(0xFFFFFFFF) to Color(0xFFFFFFFF)
    }
    if (!night) return day
    val navy = Color(0xFF1B2745)
    return lerp(day.first, navy, 0.7f) to lerp(day.second, navy, 0.7f)
}

/** A small repeatable random number (0..1) for the [i]th piece of something ([k] picks which property). */
private fun wxHash(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private fun DrawScope.wxCloud(cx: Float, cy: Float, s: Float, lit: Color, shade: Color) {
    // The soft underside first, then the lit top.
    for (pass in 0..1) {
        val c = if (pass == 0) shade else lit
        val dy = if (pass == 0) s * 0.08f else 0f
        drawRoundRect(c, Offset(cx - s, cy - s * 0.15f + dy), Size(s * 2f, s * 0.5f), CornerRadius(s * 0.25f))
        drawCircle(c, s * 0.42f, Offset(cx - s * 0.45f, cy - s * 0.20f + dy))
        drawCircle(c, s * 0.58f, Offset(cx + s * 0.08f, cy - s * 0.42f + dy))
        drawCircle(c, s * 0.38f, Offset(cx + s * 0.60f, cy - s * 0.18f + dy))
    }
}

/**
 * The weather as a moving picture. Everything is drawn from one clock, so it never recomposes: the sun slowly turns its
 * rays, the moon and stars twinkle, clouds drift, rain streaks fall, snow sways down, fog drifts and a storm flashes.
 * On entrance the sun / moon rises into place and the weather fades in.
 */
@Composable
private fun WeatherScene(sky: WxScene, night: Boolean, enter: () -> Float, modifier: Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    // Runs at about 30 frames a second: the scene is soft, so it looks the same and it is easy on a TV's GPU.
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        var last = 0L
        while (true) withFrameNanos { now ->
            if (now - last >= 33_000_000L) {
                last = now
                time = (now - t0) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier) {
        val t = time
        val e = WidgetsEnter.out(WidgetsEnter.local(enter(), 0f, 900f, 100f))
        val w = size.width
        val h = size.height
        val (top, bottom) = sceneColors(sky, night)
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val clearish = sky == WxScene.Clear || sky == WxScene.PartlyCloudy

        // Stars (clear nights).
        if (night && clearish) {
            for (i in 0 until 26) {
                val tw = 0.5f + 0.5f * sin(t * (0.8f + wxHash(i, 4) * 1.6f) + wxHash(i, 5) * 6.28f)
                drawCircle(
                    Color.White.copy(alpha = (0.35f + 0.65f * tw) * e),
                    (0.6f + wxHash(i, 3) * 1.1f).dp.toPx(),
                    Offset(wxHash(i, 1) * w, wxHash(i, 2) * h * 0.62f),
                )
            }
        }

        // Sun (day) or crescent moon (night).
        if (clearish) {
            val c = Offset(w * 0.80f, h * 0.24f + (1f - e) * h * 0.30f)
            if (!night) {
                val r = h * 0.11f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x99FFE9A0), Color.Transparent), center = c, radius = r * 4.2f),
                    r * 4.2f, c,
                )
                rotate(degrees = t * 5f, pivot = c) {
                    for (k in 0 until 12) {
                        val a = Math.toRadians(k * 30.0).toFloat()
                        drawLine(
                            Color(0x33FFF4C8),
                            Offset(c.x + cos(a) * r * 1.5f, c.y + sin(a) * r * 1.5f),
                            Offset(c.x + cos(a) * r * 2.7f, c.y + sin(a) * r * 2.7f),
                            strokeWidth = r * 0.18f, cap = StrokeCap.Round,
                        )
                    }
                }
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFF7D6), Color(0xFFFFD45C)), center = c, radius = r),
                    r, c,
                )
            } else {
                val r = h * 0.09f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x40DCE6FF), Color.Transparent), center = c, radius = r * 3.6f),
                    r * 3.6f, c,
                )
                val full = Path().apply { addOval(Rect(center = c, radius = r)) }
                val cut = Path().apply { addOval(Rect(center = c + Offset(r * 0.45f, -r * 0.18f), radius = r * 0.88f)) }
                drawPath(Path.combine(PathOperation.Difference, full, cut), Color(0xFFF1F3FA))
            }
        }

        // Clouds drift slowly to the right and come back on the left.
        val (lit, shade) = wxCloudColors(sky, night)
        for (spec in wxCloudsOf(sky)) {
            val s = spec.size * w
            val span = w + s * 4f
            val x = (spec.x * w + t * spec.speed.dp.toPx()) % span - s * 2f
            val a = spec.alpha * e
            wxCloud(x, spec.y * h, s, lit.copy(alpha = a), shade.copy(alpha = a))
        }

        // Fog: wide soft bands that drift sideways.
        if (sky == WxScene.Fog) {
            drawRect(Color.White.copy(alpha = 0.10f * e))
            for (i in 0 until 4) {
                val xo = sin(t * 0.18f + i * 1.7f) * w * 0.08f
                drawRoundRect(
                    Color.White.copy(alpha = 0.16f * e),
                    Offset(-w * 0.1f + xo, h * (0.25f + 0.17f * i)), Size(w * 1.2f, h * 0.12f), CornerRadius(h * 0.06f),
                )
            }
        }

        // Rain: slanted streaks falling at slightly different speeds.
        if (sky == WxScene.Drizzle || sky == WxScene.Rain || sky == WxScene.Storm) {
            val n = when (sky) { WxScene.Drizzle -> 36; WxScene.Rain -> 70; else -> 90 }
            val len = (if (sky == WxScene.Drizzle) 9f else 16f).dp.toPx()
            val slant = 0.22f
            val calm = if (sky == WxScene.Drizzle) 0.6f else 1f
            for (i in 0 until n) {
                val frac = (t * (0.9f + wxHash(i, 1) * 0.7f) * calm + wxHash(i, 2)) % 1f
                val y = frac * (h + len) - len
                val x = wxHash(i, 3) * (w + slant * h) - slant * y
                drawLine(
                    Color(0xFFDDE8F5).copy(alpha = (0.30f + 0.35f * wxHash(i, 4)) * e),
                    Offset(x, y), Offset(x - slant * len, y + len),
                    strokeWidth = 1.3f.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }

        // Thunderstorm: a flash and a bolt every few seconds.
        if (sky == WxScene.Storm) {
            val cyc = t % 7f
            if (cyc < 0.35f) {
                val f = 1f - cyc / 0.35f
                drawRect(Color.White.copy(alpha = 0.45f * f * (if ((cyc * 20f).toInt() % 2 == 0) 1f else 0.6f)))
                if (cyc < 0.2f) {
                    val bolt = Path().apply {
                        moveTo(w * 0.32f, h * 0.18f)
                        lineTo(w * 0.28f, h * 0.38f)
                        lineTo(w * 0.34f, h * 0.40f)
                        lineTo(w * 0.26f, h * 0.70f)
                    }
                    drawPath(bolt, Color.White.copy(alpha = f), style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }

        // Snow: flakes sway as they fall.
        if (sky == WxScene.Snow) {
            for (i in 0 until 48) {
                val r = (1.1f + wxHash(i, 1) * 2.2f).dp.toPx()
                val frac = (t * (0.06f + wxHash(i, 2) * 0.12f) + wxHash(i, 3)) % 1f
                val y = frac * (h + 2f * r) - r
                val x = wxHash(i, 4) * w + sin(t * (0.8f + wxHash(i, 5)) + wxHash(i, 6) * 6.28f) * 10.dp.toPx()
                drawCircle(Color.White.copy(alpha = (0.6f + 0.4f * wxHash(i, 7)) * e), r, Offset(x, y))
            }
        }

        // A soft dark fade at the bottom keeps the white text easy to read on any sky.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.36f)), startY = h * 0.40f, endY = h))
    }
}

// ---- iOS style weather icons (drawn with gradients, so they stay sharp at any size) ----

/** A cloud outline made of one round-bottomed body and three puffs, centred on [cx],[cy] and [w] wide. */
private fun cloudPath(cx: Float, cy: Float, w: Float): Path {
    val s = w / 2f
    var p = Path().apply {
        addRoundRect(RoundRect(cx - s, cy - 0.15f * s, cx + s, cy + 0.35f * s, CornerRadius(0.25f * s)))
    }
    for ((dx, dy, r) in listOf(Triple(-0.45f, -0.20f, 0.42f), Triple(0.08f, -0.42f, 0.58f), Triple(0.60f, -0.18f, 0.38f))) {
        val puff = Path().apply { addOval(Rect(Offset(cx + dx * s, cy + dy * s), r * s)) }
        p = Path.combine(PathOperation.Union, p, puff)
    }
    return p
}

/** A glossy cloud: a soft shadow under it, then a body that goes from bright at the top to a cool grey at the bottom. */
private fun DrawScope.iosCloud(cx: Float, cy: Float, w: Float, top: Color, bottom: Color) {
    val s = w / 2f
    val path = cloudPath(cx, cy, w)
    drawPath(cloudPath(cx, cy + s * 0.07f, w), Color(0x22000000))
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = cy - s, endY = cy + s * 0.38f))
}

private val CloudLight = Color(0xFFFFFFFF) to Color(0xFFC5D6EC)
private val CloudGrey = Color(0xFFE9EEF4) to Color(0xFFAAB7C6)
private val CloudDark = Color(0xFFAEBBCB) to Color(0xFF657386)

private fun DrawScope.iosSun(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFB02E), Color.Transparent), center = c, radius = r * 2.1f), r * 2.1f, c)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0).toFloat()
        drawLine(
            Color(0xFFFFC53D),
            Offset(c.x + cos(a) * r * 1.32f, c.y + sin(a) * r * 1.32f),
            Offset(c.x + cos(a) * r * 1.68f, c.y + sin(a) * r * 1.68f),
            strokeWidth = r * 0.2f, cap = StrokeCap.Round,
        )
    }
    val hi = Offset(c.x - r * 0.25f, c.y - r * 0.3f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFEE9A), Color(0xFFFFB02E)), center = hi, radius = r * 1.5f), r, c)
}

private fun DrawScope.iosMoon(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x44FFF2B8), Color.Transparent), center = c, radius = r * 2.0f), r * 2.0f, c)
    val full = Path().apply { addOval(Rect(c, r)) }
    val cut = Path().apply { addOval(Rect(c + Offset(r * 0.5f, -r * 0.2f), r * 0.88f)) }
    drawPath(
        Path.combine(PathOperation.Difference, full, cut),
        Brush.linearGradient(listOf(Color(0xFFFFF6CF), Color(0xFFF2D472)), start = c - Offset(r, r), end = c + Offset(r, r)),
    )
}

/** One weather icon in the style of the iPhone weather app. [u] = the icon's size; everything is placed as a fraction of it. */
private fun DrawScope.iosIcon(scene: WxScene, night: Boolean) {
    val u = size.minDimension
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val line = u * 0.045f
    when (scene) {
        WxScene.Clear -> if (night) iosMoon(o(0.5f, 0.5f), u * 0.25f) else iosSun(o(0.5f, 0.5f), u * 0.21f)
        WxScene.PartlyCloudy -> {
            if (night) iosMoon(o(0.62f, 0.36f), u * 0.18f) else iosSun(o(0.64f, 0.36f), u * 0.15f)
            iosCloud(u * 0.44f, u * 0.62f, u * 0.74f, CloudLight.first, CloudLight.second)
        }
        WxScene.Cloudy -> {
            iosCloud(u * 0.66f, u * 0.40f, u * 0.50f, CloudGrey.first, CloudGrey.second)
            iosCloud(u * 0.44f, u * 0.62f, u * 0.74f, CloudLight.first, CloudLight.second)
        }
        WxScene.Drizzle, WxScene.Rain -> {
            iosCloud(u * 0.50f, u * 0.42f, u * 0.78f, CloudDark.first, CloudDark.second)
            val xs = if (scene == WxScene.Rain) listOf(0.34f, 0.52f, 0.70f) else listOf(0.42f, 0.62f)
            xs.forEachIndexed { i, x ->
                val y = 0.68f + 0.04f * (i % 2)
                drawLine(Color(0xFF5AC8FA), o(x, y), o(x - 0.05f, y + 0.15f), strokeWidth = u * 0.055f, cap = StrokeCap.Round)
            }
        }
        WxScene.Storm -> {
            iosCloud(u * 0.50f, u * 0.40f, u * 0.78f, CloudDark.first, CloudDark.second)
            val bolt = Path().apply {
                moveTo(u * 0.55f, u * 0.58f)
                lineTo(u * 0.40f, u * 0.81f)
                lineTo(u * 0.51f, u * 0.81f)
                lineTo(u * 0.45f, u * 0.98f)
                lineTo(u * 0.67f, u * 0.72f)
                lineTo(u * 0.55f, u * 0.72f)
                lineTo(u * 0.62f, u * 0.58f)
                close()
            }
            drawPath(bolt, Brush.verticalGradient(listOf(Color(0xFFFFE45C), Color(0xFFFFB400)), startY = u * 0.58f, endY = u * 0.98f))
        }
        WxScene.Snow -> {
            iosCloud(u * 0.50f, u * 0.40f, u * 0.78f, CloudGrey.first, CloudGrey.second)
            for ((x, y) in listOf(0.34f to 0.76f, 0.52f to 0.86f, 0.70f to 0.74f)) {
                for (k in 0 until 3) {
                    val a = Math.toRadians(k * 60.0).toFloat()
                    drawLine(
                        Color.White, o(x, y) - Offset(cos(a), sin(a)) * (u * 0.05f), o(x, y) + Offset(cos(a), sin(a)) * (u * 0.05f),
                        strokeWidth = line * 0.7f, cap = StrokeCap.Round,
                    )
                }
            }
        }
        WxScene.Fog -> {
            iosCloud(u * 0.50f, u * 0.36f, u * 0.70f, CloudGrey.first, CloudGrey.second)
            for ((i, xs) in listOf(0.22f to 0.78f, 0.30f to 0.70f, 0.24f to 0.74f).withIndex()) {
                drawLine(Color(0xFFDCE4EE), o(xs.first, 0.63f + i * 0.11f), o(xs.second, 0.63f + i * 0.11f), strokeWidth = u * 0.055f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun WxIcon(scene: WxScene, night: Boolean, modifier: Modifier) {
    Canvas(modifier) { iosIcon(scene, night) }
}

private fun sceneName(scene: WxScene, night: Boolean): String = when (scene) {
    WxScene.Clear -> if (night) "Clear" else "Sunny"
    WxScene.PartlyCloudy -> "Partly cloudy"
    WxScene.Cloudy -> "Cloudy"
    WxScene.Fog -> "Fog"
    WxScene.Drizzle -> "Drizzle"
    WxScene.Rain -> "Rain"
    WxScene.Snow -> "Snow"
    WxScene.Storm -> "Thunderstorm"
}

// ---- Weather tile data (read from Weather, with sensible fallbacks) ----

private class WxDay(val label: String, val scene: WxScene, val hi: String?, val lo: String?)
private class WxNow(val temp: String?, val text: String, val hiLo: String?)

/** The next days of the forecast (up to 5), for the strip at the bottom of the tile. Empty when Weather has none. */
private fun wxDays(w: Weather?, fallback: WxScene): List<WxDay> {
    if (w == null) return emptyList()
    val list: List<Any?> = (w.days as? List<*>) ?: emptyList<Any?>()
    val cal = Calendar.getInstance()
    return list.take(5).mapIndexed { i, d ->
        val c = (d.peek("code", "weatherCode", "wmo", "condition", "icon") as? Number)?.toInt()
        val label = if (i == 0) "Today" else {
            val day = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            SimpleDateFormat("EEE", Locale.getDefault()).format(day.time)
        }
        WxDay(
            label,
            if (c != null) sceneOf(c) else fallback,
            degrees(d.peek("max", "high", "tempMax", "hi", "maxTemp", "temperatureMax", "tMax", "tempHigh")),
            degrees(d.peek("min", "low", "tempMin", "lo", "minTemp", "temperatureMin", "tMin", "tempLow")),
        )
    }
}

private fun wxNow(w: Weather?, scene: WxScene, night: Boolean, days: List<WxDay>): WxNow {
    val text = (w.peek("description", "desc", "text", "condition", "conditionText", "summary", "label") as? String)
        ?.takeIf { it.isNotBlank() } ?: sceneName(scene, night)
    val today = days.firstOrNull()
    val hiLo = if (today?.hi != null && today.lo != null) "H:${today.hi}  L:${today.lo}" else null
    val temp = w.peek("temp", "temperature", "tempC", "currentTemp", "current", "now")
        ?: w.fuzzy(listOf("temp"), listOf("max", "min", "high", "low", "feel", "app"))
    return WxNow(degrees(temp), text, hiLo)
}

@Composable
private fun WeatherCard(backdrop: Backdrop, weather: Weather?, focused: Boolean, enter: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    // Night = outside the sunrise..sunset of the place (the same times the sun card uses).
    val sun = remember(weather) { sunTimes(weather) }
    val night = System.currentTimeMillis() !in sun.first..sun.second
    val live = weather?.takeIf { !it.isPlaceholder }
    val scene = sceneOf(live?.code)
    val days = remember(live) { wxDays(live, scene) }
    val now = remember(live, night) { wxNow(live, scene, night, days) }
    Box(modifier.enterTile(enter, 0).widgetCard(shape, focused)) {
        WeatherScene(scene, night, enter, Modifier.matchParentSize().clip(shape))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (weather != null && weather.place.isNotBlank()) {
                        BasicText(weather.place, style = txt(Color.White.copy(alpha = 0.92f), 15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        BasicText("Weather", style = txt(Orange, 15.sp, FontWeight.SemiBold))
                    }
                    when {
                        now.temp != null -> {
                            BasicText(now.temp, style = txt(Color.White, 58.sp, FontWeight.Light))
                            BasicText(now.text, style = txt(Color.White, 16.sp, FontWeight.Medium))
                            if (now.hiLo != null) {
                                BasicText(now.hiLo, style = txt(Color.White.copy(alpha = 0.8f), 13.sp, FontWeight.Medium))
                            }
                        }
                        weather != null -> {
                            // The temperature could not be read from Weather: use the same weather text as the home screen.
                            Spacer(Modifier.height(18.dp))
                            Box(Modifier.graphicsLayer { scaleX = 1.7f; scaleY = 1.7f; transformOrigin = TransformOrigin(0f, 0.5f) }) {
                                WeatherPill(backdrop, weather, false, Modifier, visible = true, plain = true)
                            }
                        }
                        else -> BasicText("Weather isn't available yet", style = txt(Color.White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium))
                    }
                }
                WxIcon(
                    scene, night,
                    Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            // The icon pops in a moment after its card.
                            val l = WidgetsEnter.local(enter(), 0f, 500f, 250f)
                            val sc = WidgetsEnter.back(l)
                            scaleX = sc
                            scaleY = sc
                            alpha = l
                        },
                )
            }
            Spacer(Modifier.weight(1f))
            if (days.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.22f)))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    days.forEachIndexed { i, d ->
                        Column(
                            Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    // The forecast fades up one day after another.
                                    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 0f, 400f, 450f + i * 70f))
                                    alpha = l
                                    translationY = (1f - l) * 10.dp.toPx()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(d.label, style = txt(Color.White.copy(alpha = 0.78f), 12.sp, FontWeight.Medium))
                            Spacer(Modifier.height(3.dp))
                            WxIcon(d.scene, false, Modifier.size(30.dp))
                            Spacer(Modifier.height(3.dp))
                            if (d.hi != null) BasicText(d.hi, style = txt(Color.White, 14.sp, FontWeight.SemiBold))
                            if (d.lo != null) BasicText(d.lo, style = txt(Color.White.copy(alpha = 0.65f), 13.sp, FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

// ---- Month calendar (Monday first), the same size as the weather card ----

@Composable
private fun CalendarCard(enter: () -> Float, modifier: Modifier) {
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    // The grid starts on the Monday on or before the 1st, and always has 6 rows.
    val start = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    start.add(Calendar.DAY_OF_MONTH, -offset)
    val monthName = SimpleDateFormat("LLLL", Locale.getDefault()).format(now.time).uppercase()
    val year = now.get(Calendar.YEAR).toString()
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(modifier.enterTile(enter, 1).widgetCard(RoundedCornerShape(30.dp)).padding(18.dp)) {
        // Like the iPhone Calendar widget: the month in red capitals, the year small and grey.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(monthName, style = txt(IosRed, 15.sp, FontWeight.Bold).copy(letterSpacing = 0.8.sp))
            Spacer(Modifier.weight(1f))
            BasicText(year, style = txt(Color.White.copy(alpha = 0.5f), 13.sp, FontWeight.Medium))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            names.forEach {
                Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                    BasicText(it, style = txt(Color.White.copy(alpha = 0.5f), 12.sp, FontWeight.SemiBold))
                }
            }
        }
        // The 6 rows share the rest of the card's height.
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, r * 7 + c) }
                    val inMonth = d.get(Calendar.MONTH) == month
                    val isToday = inMonth && d.get(Calendar.DAY_OF_MONTH) == today
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // The days pop in as a wave from the top left corner.
                                val l = WidgetsEnter.local(enter(), 1f, 350f, 200f + (r + c) * 35f)
                                alpha = WidgetsEnter.out(l)
                                val sc = 0.5f + 0.5f * WidgetsEnter.back(l)
                                scaleX = sc
                                scaleY = sc
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isToday) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .graphicsLayer {
                                        // Today's red circle pops in last.
                                        val l = WidgetsEnter.local(enter(), 1f, 420f, 900f)
                                        val sc = WidgetsEnter.back(l)
                                        scaleX = sc
                                        scaleY = sc
                                        alpha = l
                                    }
                                    .background(IosRed, RoundedCornerShape(50)),
                            )
                        }
                        BasicText(
                            d.get(Calendar.DAY_OF_MONTH).toString(),
                            style = txt(
                                Color.White.copy(alpha = if (inMonth) 1f else 0.25f), 14.sp,
                                if (isToday) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ---- Sun: sunrise, sunset and the weather readings (under the weather card) ----

/**
 * Reads a value out of the Weather object (or one of its forecast days) by trying a few common property names,
 * so this file does not depend on how Weather names things. Returns null when none of the names exist.
 */
private fun Any?.peek(vararg names: String): Any? {
    if (this == null) return null
    for (n in names) {
        try {
            val cap = n.replaceFirstChar { it.uppercase() }
            val m = javaClass.methods.firstOrNull {
                it.parameterCount == 0 && (it.name == "get$cap" || it.name == n || it.name == "is$cap")
            }
            val v = if (m != null) m.invoke(this) else javaClass.getDeclaredField(n).also { it.isAccessible = true }.get(this)
            if (v != null) return v
        } catch (_: Throwable) {
            // Not there under this name: try the next one.
        }
    }
    return null
}

/**
 * A second try when the exact names are not there: looks through every field of the object for one whose name
 * contains one of [keys] (and none of [avoid]) and holds a number, so "wind_speed_10m" or "relativeHumidity" are found too.
 */
private fun Any?.fuzzy(keys: List<String>, avoid: List<String> = emptyList()): Any? {
    if (this == null) return null
    var c: Class<*>? = javaClass
    while (c != null && c != Any::class.java) {
        for (f in c.declaredFields) {
            val n = f.name.lowercase()
            if (f.isSynthetic || keys.none { n.contains(it) } || avoid.any { n.contains(it) }) continue
            try {
                f.isAccessible = true
                val v = f.get(this)
                if (v.asNumber() != null) return v
            } catch (_: Throwable) {
                // Not readable: try the next field.
            }
        }
        c = c.superclass
    }
    return null
}

private fun Any?.asNumber(): Double? = when (this) {
    is Number -> toDouble()
    is String -> filter { it.isDigit() || it == '-' || it == '.' }.toDoubleOrNull()
    else -> null
}

private fun degrees(v: Any?): String? = v.asNumber()?.let { "${it.roundToInt()}\u00B0" }

/** The four readings on the right of the sun card, as (label, value). A reading that Weather does not have shows "--". */
private fun weatherStats(w: Weather?): List<Pair<String, String>> {
    fun reading(unit: String, key: String, avoid: List<String>, vararg names: String): String =
        (w.peek(*names) ?: w.fuzzy(listOf(key), avoid))?.asNumber()?.let { "${it.roundToInt()}$unit" } ?: "--"
    return listOf(
        "Wind" to reading(" km/h", "wind", listOf("dir", "deg", "gust"), "windSpeed", "wind", "windKph", "windKmh", "wind_speed"),
        "Humidity" to reading("%", "humid", emptyList(), "humidity", "relativeHumidity", "humid"),
        "Temp" to reading("\u00B0C", "temp", listOf("max", "min", "high", "low", "feel", "app"), "temp", "temperature", "tempC", "currentTemp"),
        "Pressure" to reading(" hPa", "press", emptyList(), "pressure", "pressureHpa", "pressureMb", "surfacePressure"),
    )
}

/** Sunrise and sunset of the place (or the usual times while the weather is not loaded), the same as HomeScreen uses. */
private fun sunTimes(w: Weather?): Pair<Long, Long> =
    w?.takeIf { !it.isPlaceholder && it.sunrise > 0L && it.sunset > 0L }
        ?.let { it.sunrise + WeatherRepository.skewMs(it.zone) to it.sunset + WeatherRepository.skewMs(it.zone) }
        ?: WeatherRepository.fallbackTimes()

/** The small "sunset" glyph (half a sun on the horizon) in the card's title. */
@Composable
private fun SunGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val orange = Color(0xFFFF9F0A)
        drawArc(orange, 180f, 180f, true, topLeft = Offset(w * 0.2f, h * 0.75f - w * 0.3f), size = Size(w * 0.6f, w * 0.6f))
        drawLine(orange, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = h * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
private fun SunStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        BasicText(label, style = txt(Color.White.copy(alpha = 0.55f), 11.sp, FontWeight.Medium))
        BasicText(value, style = txt(Color.White, 14.sp, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun SunCard(weather: Weather?, enter: () -> Float, modifier: Modifier) {
    val sun = remember(weather) { sunTimes(weather) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    // Where the sun is on its way from sunrise to sunset (null at night: no sun is drawn).
    val t: Float? = if (sun.second > sun.first && now in sun.first..sun.second) {
        (now - sun.first).toFloat() / (sun.second - sun.first).toFloat()
    } else null
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    // Like the iPhone: the card features the next event (sunset by day, sunrise at night) and mentions the other one.
    val sunsetNext = t != null
    val stats = weatherStats(weather)
    Row(
        modifier.enterTile(enter, 2).widgetCard(RoundedCornerShape(30.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 2f, 400f, 450f))
                    alpha = l
                    translationY = (1f - l) * 8.dp.toPx()
                },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunGlyph(Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                BasicText(
                    if (sunsetNext) "SUNSET" else "SUNRISE",
                    style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.SemiBold).copy(letterSpacing = 0.8.sp),
                )
            }
            Column {
                BasicText(
                    clock.format(Date(if (sunsetNext) sun.second else sun.first)),
                    style = txt(Color.White, 40.sp, FontWeight.Light),
                )
                BasicText(
                    (if (sunsetNext) "Sunrise: " else "Sunset: ") + clock.format(Date(if (sunsetNext) sun.first else sun.second)),
                    style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.Medium),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1.2f).fillMaxHeight()) {
            SunArc(t, enter, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.graphicsLayer {
                    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 2f, 400f, 600f))
                    alpha = l
                    translationY = (1f - l) * 8.dp.toPx()
                },
            ) {
                Row(Modifier.fillMaxWidth()) {
                    SunStat(stats[0].first, stats[0].second, Modifier.weight(1f))
                    SunStat(stats[1].first, stats[1].second, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    SunStat(stats[2].first, stats[2].second, Modifier.weight(1f))
                    SunStat(stats[3].first, stats[3].second, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The sun's path over a horizon line, like the iPhone Weather widget. [t] = 0..1 is how far the sun is along it
 * (null = night, no sun). On entrance the path draws itself, then the sun travels along it.
 */
@Composable
private fun SunArc(t: Float?, enter: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val raw = enter()
        val drawn = WidgetsEnter.local(raw, 2f, 700f, 150f)                          // how much of the path is drawn
        val travel = WidgetsEnter.out(WidgetsEnter.local(raw, 2f, 800f, 450f))      // how far the sun has travelled
        val pi = Math.PI.toFloat()
        val x0 = 4.dp.toPx()
        val x1 = size.width - 4.dp.toPx()
        val horizon = size.height * 0.74f
        val amp = size.height * 0.62f
        fun px(u: Float) = x0 + (x1 - x0) * u
        fun py(u: Float) = horizon - amp * sin(pi * u)
        val steps = 60
        // The horizon.
        drawLine(Color.White.copy(alpha = 0.30f * drawn), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
        // The whole path (soft), drawn from the left as far as [drawn].
        if (drawn > 0f) {
            val n = (steps * drawn).toInt().coerceAtLeast(1)
            val all = Path().apply {
                moveTo(px(0f), py(0f))
                for (i in 1..n) lineTo(px(drawn * i / n), py(drawn * i / n))
            }
            drawPath(all, Color.White.copy(alpha = 0.35f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        // The part the sun has already travelled (bright) and the sun itself.
        val tt = t?.let { it * travel }
        if (tt != null && tt > 0f) {
            val n = (steps * tt).toInt().coerceAtLeast(1)
            val done = Path().apply {
                moveTo(px(0f), py(0f))
                for (i in 1..n) lineTo(px(tt * i / n), py(tt * i / n))
            }
            drawPath(done, Color.White.copy(alpha = 0.95f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (tt != null) {
            val c = Offset(px(tt), py(tt))
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFC53D), Color.Transparent), center = c, radius = 14.dp.toPx()), 14.dp.toPx(), c)
            drawCircle(SunYellow.copy(alpha = drawn), 5.dp.toPx(), c)
        }
    }
}

// ---- Screen time (under the calendar) ----

private class WidgetAppUse(val label: String, val ms: Long)
private class WidgetScreenTime(val granted: Boolean, val totalMs: Long, val top: List<WidgetAppUse>)

private fun formatDuration(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

@Suppress("DEPRECATION")
internal fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Opens the TV's "Usage access" settings (on the page of this app when the TV allows it).
 * Returns false when the TV has no such screen: then use the adb command that the screen time card shows.
 */
internal fun openUsageAccessSettings(context: Context): Boolean {
    val forThisApp = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val general = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (intent in listOf(forThisApp, general)) {
        try {
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            // This screen does not exist on this TV: try the next one.
        }
    }
    return false
}

/** Today's foreground time per app (needs "Usage access" for this app in the TV's settings). */
private fun loadScreenTime(context: Context): WidgetScreenTime {
    if (!hasUsageAccess(context)) return WidgetScreenTime(false, 0L, emptyList())
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val used = usm.queryAndAggregateUsageStats(dayStart, System.currentTimeMillis()).values
        .filter { it.totalTimeInForeground >= 60_000L && it.packageName != context.packageName }
        .sortedByDescending { it.totalTimeInForeground }
    val pm = context.packageManager
    val top = used.take(4).map { s ->
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString() }
            .getOrDefault(s.packageName)
        WidgetAppUse(label, s.totalTimeInForeground)
    }
    return WidgetScreenTime(true, used.sumOf { it.totalTimeInForeground }, top)
}

/** The small Screen Time glyph (a white hourglass on indigo) in the card's title. */
@Composable
private fun HourglassGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF5E5CE6), cornerRadius = CornerRadius(w * 0.28f))
        val top = Path().apply {
            moveTo(w * 0.30f, h * 0.24f); lineTo(w * 0.70f, h * 0.24f); lineTo(w * 0.5f, h * 0.5f); close()
        }
        val bottom = Path().apply {
            moveTo(w * 0.5f, h * 0.5f); lineTo(w * 0.30f, h * 0.76f); lineTo(w * 0.70f, h * 0.76f); close()
        }
        drawPath(top, Color.White)
        drawPath(bottom, Color.White)
    }
}

@Composable
private fun ScreenTimeCard(enter: () -> Float, modifier: Modifier) {
    val context = LocalContext.current
    val usage by produceState<WidgetScreenTime?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { loadScreenTime(context) }.getOrNull() }
    }
    val u = usage
    Column(
        modifier.enterTile(enter, 3).widgetCard(RoundedCornerShape(30.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Like the iPhone Screen Time widget: a small title, the big total, a bar split by app, and the top apps.
        Row(verticalAlignment = Alignment.CenterVertically) {
            HourglassGlyph(Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            BasicText(
                "SCREEN TIME",
                style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.SemiBold).copy(letterSpacing = 0.8.sp),
            )
        }
        if (u != null && !u.granted) {
            Column {
                BasicText(
                    "Usage access is off. Press OK to open the settings and allow Glass.",
                    style = txt(Color.White.copy(alpha = 0.85f), 13.sp, FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "No such screen on this TV? Run: adb shell appops set ${context.packageName} GET_USAGE_STATS allow",
                    style = txt(Color.White.copy(alpha = 0.5f), 11.sp, FontWeight.Medium),
                )
            }
        } else if (u != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    BasicText(formatDuration(u.totalMs), style = txt(Color.White, 38.sp, FontWeight.Light))
                    BasicText("Today", style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.Medium))
                }
                Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    u.top.take(3).forEachIndexed { i, a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    // The top apps fade up one after another.
                                    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 3f, 400f, 550f + i * 80f))
                                    alpha = l
                                    translationY = (1f - l) * 10.dp.toPx()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(UsageColors[i], RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = txt(Color.White.copy(alpha = 0.85f), 12.sp, FontWeight.Medium),
                            )
                            BasicText(formatDuration(a.ms), style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium))
                        }
                    }
                }
            }
            // One bar split by app (the grey part is every other app); it grows from the left.
            val topSum = u.top.sumOf { it.ms }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .graphicsLayer {
                        scaleX = WidgetsEnter.out(WidgetsEnter.local(enter(), 3f, 650f, 300f))
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                u.top.forEachIndexed { i, a ->
                    Box(Modifier.weight(a.ms.coerceAtLeast(1L).toFloat()).fillMaxHeight().background(UsageColors[i]))
                }
                val rest = u.totalMs - topSum
                if (rest > 0L) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().background(UsageOther))
                if (u.top.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight().background(UsageOther))
            }
        } else {
            Spacer(Modifier.height(1.dp))
        }
    }
}

// ---- Boost RAM ----

private enum class BoostPhase { Idle, Working, Done, Failed }

private val ramCard = Color(0xFF1C1C1E)
private val ramBlue = Color(0xFF0A84FF)
private val ramGreen = Color(0xFF30D158)
private val ramOrange = Color(0xFFFF9F0A)
private val ramRed = Color(0xFFFF453A)

private fun ramTextStyle(color: Color, size: TextUnit, weight: FontWeight): TextStyle =
    TextStyle(color = color, fontSize = size, fontWeight = weight)

/** The ring: a faint full circle, then the share in use, or a spinning arc while a boost runs. */
private fun DrawScope.drawRamRing(used: Float, working: Boolean, spin: Float, color: Color, strokeDp: Float) {
    val stroke = strokeDp.dp.toPx()
    val inset = stroke / 2f
    val box = Size(size.width - stroke, size.height - stroke)
    val corner = Offset(inset, inset)
    val ringStyle = Stroke(width = stroke, cap = StrokeCap.Round)
    drawArc(Color.White.copy(alpha = 0.16f), -90f, 360f, false, corner, box, style = ringStyle)
    if (working) {
        drawArc(ramBlue, spin, 110f, false, corner, box, style = ringStyle)
    } else if (used > 0f) {
        drawArc(color, -90f, 360f * used, false, corner, box, style = ringStyle)
    }
}

@Composable
private fun RamStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(label, style = ramTextStyle(Color.White.copy(alpha = 0.6f), 14.sp, FontWeight.Medium))
        BasicText(value, style = ramTextStyle(Color.White, 28.sp, FontWeight.Light))
    }
}

/**
 * "Boost RAM": a ring with the RAM in use, the free memory and a Boost button.
 * [pressCount] goes up by one for every OK press. [large] is the full-screen version of the same card.
 */
@Composable
fun BoostRamCard(pressCount: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(MemoryBoost.read(ctx)) }
    var phase by remember { mutableStateOf(BoostPhase.Idle) }
    var freed by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // The press count this card has already handled. A card that opens later starts from the current count,
    // so it does not boost by itself.
    var seen by remember { mutableIntStateOf(pressCount) }
    val spinAngle = remember { mutableFloatStateOf(0f) }

    // The memory is read every 2.5 seconds (not while a boost runs).
    LaunchedEffect(Unit) {
        while (true) {
            if (!busy) stats = MemoryBoost.read(ctx)
            delay(2500L)
        }
    }

    fun runBoost() {
        if (busy) return
        busy = true
        phase = BoostPhase.Working
        scope.launch {
            val result = MemoryBoost.boost(ctx)
            if (result.ok) {
                freed = result.freedBytes
                message = ""
                phase = BoostPhase.Done
            } else {
                message = result.message
                phase = BoostPhase.Failed
            }
            stats = MemoryBoost.read(ctx)
            busy = false
            delay(4000L)
            if (!busy) phase = BoostPhase.Idle
        }
    }

    LaunchedEffect(pressCount) {
        if (pressCount != seen) {
            seen = pressCount
            runBoost()
        }
    }

    // The ring turns while a boost runs (only then).
    LaunchedEffect(phase) {
        if (phase != BoostPhase.Working) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                spinAngle.floatValue = (spinAngle.floatValue + (now - last) / 1_000_000f * 0.3f) % 360f
                last = now
            }
        }
    }

    val used by animateFloatAsState(stats.usedFraction, spring(stiffness = Spring.StiffnessLow), label = "ramUsed")
    val percent = (used * 100f).roundToInt()
    val ringColor = when {
        used < 0.6f -> ramGreen
        used < 0.8f -> ramOrange
        else -> ramRed
    }
    val label = when (phase) {
        BoostPhase.Idle -> "Boost"
        BoostPhase.Working -> "Boosting\u2026"
        BoostPhase.Done -> if (freed < 1_048_576L) "Already fast" else "Freed ${MemoryBoost.mb(freed)}"
        BoostPhase.Failed -> "Needs ADB"
    }
    val pill = when (phase) {
        BoostPhase.Failed -> ramRed
        BoostPhase.Done -> ramGreen
        else -> ramBlue
    }
    val usedBytes = stats.totalBytes - stats.availBytes
    val freeLine = "${MemoryBoost.gb(stats.availBytes)} free of ${MemoryBoost.gb(stats.totalBytes)}"
    val line = if (phase == BoostPhase.Failed && message.isNotEmpty()) message else freeLine
    val shape = RoundedCornerShape(30.dp)

    if (large) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRamRing(used, phase == BoostPhase.Working, spinAngle.floatValue, ringColor, 12f)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("$percent%", style = ramTextStyle(Color.White, 64.sp, FontWeight.Light))
                    BasicText("used", style = ramTextStyle(Color.White.copy(alpha = 0.6f), 18.sp, FontWeight.Medium))
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                RamStat("Used", MemoryBoost.gb(usedBytes))
                RamStat("Free", MemoryBoost.gb(stats.availBytes))
                RamStat("Total", MemoryBoost.gb(stats.totalBytes))
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier
                    .width(340.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pill),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = ramTextStyle(Color.White, 20.sp, FontWeight.SemiBold), maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            BasicText(
                if (phase == BoostPhase.Failed && message.isNotEmpty()) message else "OK boosts. It stops background apps.",
                style = ramTextStyle(Color.White.copy(alpha = 0.55f), 15.sp, FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    Row(
        modifier
            .clip(shape)
            .background(ramCard.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // The ring shows the share of RAM in use. It turns while the boost runs.
        Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawRamRing(used, phase == BoostPhase.Working, spinAngle.floatValue, ringColor, 9f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("$percent%", style = ramTextStyle(Color.White, 22.sp, FontWeight.Light))
                BasicText("used", style = ramTextStyle(Color.White.copy(alpha = 0.6f), 10.sp, FontWeight.Medium))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            BasicText(
                "MEMORY",
                style = ramTextStyle(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.SemiBold).copy(letterSpacing = 0.8.sp),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                line,
                style = ramTextStyle(Color.White, 15.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            // The pill button, like the blue buttons on iOS.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pill),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = ramTextStyle(Color.White, 13.sp, FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}

// ---- Now playing (vinyl) ----

/** What the vinyl views show, read from NowPlaying. [art] is null when there is no picture. */
class VinylTrack(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * The song for the vinyl views, read straight from NowPlaying (the same data the media card uses).
 * Returns null when nothing is playing. It is read again every quarter second, so the progress and the lyrics move.
 */
@Composable
fun rememberNowPlayingTrack(): VinylTrack? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(250L)
        }
    }
    if (!NowPlaying.active) return null
    return VinylTrack(
        title = NowPlaying.title.ifBlank { "Now playing" },
        artist = NowPlaying.artist,
        art = NowPlaying.art,
        playing = NowPlaying.playing,
        positionMs = NowPlaying.position(now),
        durationMs = NowPlaying.durationMs,
    )
}

private val vinylBlue = Color(0xFF0A84FF)

private fun vinylTextStyle(color: Color, size: TextUnit, weight: FontWeight): TextStyle =
    TextStyle(color = color, fontSize = size, fontWeight = weight)

/**
 * iOS-style Now playing card with a spinning record: blurred album art behind, the record on the left,
 * title, artist, progress and the three buttons on the right.
 * [buttonFocus] is -1 when the card has no focus. Otherwise it is the highlighted button:
 * 0 = previous, 1 = play / pause, 2 = next.
 */
@Composable
fun VinylCard(track: VinylTrack?, buttonFocus: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    val art = track?.art
    val playing = track?.playing == true
    val fraction = track?.let { t ->
        if (t.durationMs > 0L) (t.positionMs.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
    } ?: 0f

    // The record turns while music plays. It is read only while drawing, so it never recomposes the screen.
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * 40f) % 360f
                last = now
            }
        }
    }

    Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.14f), shape)) {
        // Background: the blurred album art, or a dusky gradient when there is no art.
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3154), Color(0xFF14121E)))))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))

        Row(
            Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The record. The label in the middle shows the album picture.
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .graphicsLayer { rotationZ = angle.floatValue },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { drawRecord() }
                Box(
                    Modifier
                        .fillMaxSize(0.44f)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFB27A), Color(0xFFE8613C)))),
                ) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF0B0B0E)))
            }

            // The text, the progress and the buttons.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                BasicText(
                    track?.title ?: "Nothing playing",
                    style = vinylTextStyle(Color.White, 16.sp, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    if (track == null) "Play music to see it here" else track.artist,
                    style = vinylTextStyle(Color.White.copy(alpha = 0.66f), 12.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val live = track != null
                    VinylButton(Icons.Rounded.SkipPrevious, 22.dp, buttonFocus == 0, live, false)
                    VinylButton(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        20.dp, buttonFocus == 1, live, true,
                    )
                    VinylButton(Icons.Rounded.SkipNext, 22.dp, buttonFocus == 2, live, false)
                }
            }
        }
    }
}

@Composable
private fun VinylButton(
    icon: ImageVector,
    iconSize: Dp,
    selected: Boolean,
    enabled: Boolean,
    filled: Boolean,
) {
    Box(
        Modifier
            .size(if (filled) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(
                when {
                    filled -> Color.White.copy(alpha = if (enabled) 1f else 0.4f)
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.Transparent
                },
            )
            .border(2.dp, if (selected && filled) vinylBlue else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(icon),
            null,
            Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(
                if (filled) Color(0xFF101014) else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            ),
        )
    }
}

/** The black vinyl: a dark disc with fine grooves and a light sheen across it. */
private fun DrawScope.drawRecord() {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2E2E34), Color(0xFF070709)), center = c, radius = r), r, c)
    val step = 2.4f.dp.toPx()
    var g = r * 0.40f
    while (g < r * 0.96f) {
        drawCircle(Color.White.copy(alpha = 0.07f), g, c, style = Stroke(0.8f.dp.toPx()))
        g += step
    }
    drawCircle(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        r,
        c,
    )
}

/** m:ss for the progress labels. */
private fun playerTime(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60L, s % 60L)
}

/** The full-screen player: a big record with the album art, the title, the synced lyrics, the progress and the three buttons. */
@Composable
private fun VinylPlayerView(track: VinylTrack?, buttonFocus: Int, modifier: Modifier) {
    val art = track?.art
    val playing = track?.playing == true
    val position = track?.positionMs ?: 0L
    val duration = track?.durationMs ?: 0L
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // The lyrics: null while they are being looked up, an empty list when the song has none.
    val lines by produceState<List<LyricLine>?>(null, track?.title, track?.artist, track?.durationMs) {
        value = if (track == null) emptyList() else LyricsRepository.fetch(track.title, track.artist, track.durationMs)
    }

    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * 40f) % 360f
                last = now
            }
        }
    }

    Box(modifier) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize().blur(60.dp), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3154), Color(0xFF14121E)))))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // The record, turning while the music plays.
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .graphicsLayer { rotationZ = angle.floatValue },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { drawRecord() }
                Box(
                    Modifier
                        .fillMaxSize(0.44f)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFB27A), Color(0xFFE8613C)))),
                ) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF0B0B0E)))
            }

            // The title, the lyrics, the progress and the buttons.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BasicText(
                    track?.title ?: "Nothing playing",
                    style = vinylTextStyle(Color.White, 32.sp, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    if (track == null) "Play music to see it here" else track.artist,
                    style = vinylTextStyle(Color.White.copy(alpha = 0.7f), 18.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                if (track != null) {
                    LyricsView(lines, position, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(playerTime(position), style = vinylTextStyle(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium))
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
                    }
                    Spacer(Modifier.width(12.dp))
                    BasicText(playerTime(duration), style = vinylTextStyle(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val live = track != null
                    VinylButton(Icons.Rounded.SkipPrevious, 26.dp, buttonFocus == 0, live, false)
                    Spacer(Modifier.width(32.dp))
                    VinylButton(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        30.dp, buttonFocus == 1, live, true,
                    )
                    Spacer(Modifier.width(32.dp))
                    VinylButton(Icons.Rounded.SkipNext, 26.dp, buttonFocus == 2, live, false)
                }
            }
        }
    }
}

/** The synced lyrics: the line being sung is big and bright, and the lines around it fade out. */
@Composable
private fun LyricsView(lines: List<LyricLine>?, positionMs: Long, modifier: Modifier) {
    val density = LocalDensity.current
    val lineHpx = with(density) { 56.dp.toPx() }
    val index = lines?.indexOfLast { it.timeMs <= positionMs } ?: -1
    // The list glides so that the current line stays in the middle.
    val scroll by animateFloatAsState(
        index.coerceAtLeast(0) * lineHpx,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "lyricsScroll",
    )
    var boxH by remember { mutableFloatStateOf(0f) }
    Box(modifier.clipToBounds().onSizeChanged { boxH = it.height.toFloat() }) {
        when {
            lines == null -> BasicText(
                "Finding lyrics\u2026",
                Modifier.align(Alignment.Center),
                style = vinylTextStyle(Color.White.copy(alpha = 0.5f), 18.sp, FontWeight.Medium),
            )
            lines.isEmpty() -> BasicText(
                "No synced lyrics for this song",
                Modifier.align(Alignment.Center),
                style = vinylTextStyle(Color.White.copy(alpha = 0.5f), 18.sp, FontWeight.Medium),
            )
            else -> Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (boxH / 2f - lineHpx / 2f - scroll).roundToInt()) },
            ) {
                lines.forEachIndexed { i, line ->
                    val current = i == index
                    val distance = abs(i - index)
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                        BasicText(
                            line.text,
                            style = vinylTextStyle(
                                Color.White.copy(alpha = if (current) 1f else (0.5f - 0.1f * distance).coerceAtLeast(0.12f)),
                                if (current) 30.sp else 22.sp,
                                if (current) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- Expanded views (a widget opened with a long press on OK) ----

/** The whole year: twelve months in a 4 x 3 grid. The current month and today are marked. */
@Composable
private fun YearCalendarView(modifier: Modifier) {
    val now = remember { Calendar.getInstance() }
    val year = now.get(Calendar.YEAR)
    val currentMonth = now.get(Calendar.MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    val todayText = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.time) }
    Column(modifier.padding(horizontal = 56.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BasicText(year.toString(), style = txt(Color.White, 44.sp, FontWeight.Light))
            Spacer(Modifier.weight(1f))
            BasicText(todayText, style = txt(Color.White.copy(alpha = 0.6f), 15.sp, FontWeight.Medium))
        }
        Spacer(Modifier.height(16.dp))
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (col in 0 until 4) {
                    val month = row * 4 + col
                    MonthGrid(
                        year = year,
                        month = month,
                        today = if (month == currentMonth) today else -1,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** One month: its name, the weekday letters (Monday first) and the days. [today] is today's day in this month, or -1. */
@Composable
private fun MonthGrid(year: Int, month: Int, today: Int, modifier: Modifier) {
    val first = Calendar.getInstance().apply { set(year, month, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val name = SimpleDateFormat("LLLL", Locale.getDefault()).format(first.time)
    val isCurrent = today > 0
    Column(modifier) {
        BasicText(
            name,
            style = txt(if (isCurrent) IosRed else Color.White, 16.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    BasicText(d, style = txt(Color.White.copy(alpha = 0.45f), 11.sp, FontWeight.SemiBold))
                }
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (wd in 0 until 7) {
                    val day = week * 7 + wd - offset + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            if (day == today) {
                                Box(Modifier.size(22.dp).background(IosRed, CircleShape))
                            }
                            BasicText(
                                day.toString(),
                                style = txt(
                                    Color.White.copy(alpha = if (day == today) 1f else 0.85f),
                                    12.sp,
                                    if (day == today) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The weather, full screen: the temperature and the sky, then the next five days. */
@Composable
private fun WeatherExpandedView(weather: Weather?, modifier: Modifier) {
    val sun = remember(weather) { sunTimes(weather) }
    val night = System.currentTimeMillis() !in sun.first..sun.second
    val live = weather?.takeIf { !it.isPlaceholder }
    val scene = sceneOf(live?.code)
    val days = remember(live) { wxDays(live, scene) }
    val now = remember(live, night) { wxNow(live, scene, night, days) }
    Column(modifier.padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    weather?.place?.takeIf { it.isNotBlank() } ?: "Weather",
                    style = txt(Color.White.copy(alpha = 0.8f), 18.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(now.temp ?: "--", style = txt(Color.White, 96.sp, FontWeight.Light))
                BasicText(now.text, style = txt(Color.White, 20.sp, FontWeight.Medium))
                now.hiLo?.let { BasicText(it, style = txt(Color.White.copy(alpha = 0.75f), 15.sp, FontWeight.Medium)) }
            }
            WxIcon(scene, night, Modifier.size(180.dp))
        }
        Spacer(Modifier.weight(1f))
        if (days.isEmpty()) {
            BasicText("No forecast yet", style = txt(Color.White.copy(alpha = 0.7f), 16.sp, FontWeight.Medium))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                days.forEach { d ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(d.label, style = txt(Color.White.copy(alpha = 0.8f), 15.sp, FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        WxIcon(d.scene, false, Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        BasicText(d.hi ?: "--", style = txt(Color.White, 22.sp, FontWeight.SemiBold))
                        BasicText(d.lo ?: "--", style = txt(Color.White.copy(alpha = 0.6f), 17.sp, FontWeight.Medium))
                    }
                }
            }
        }
    }
}

/** The sun, full screen: the next sun event, the day's arc, and the readings. */
@Composable
private fun SunExpandedView(weather: Weather?, modifier: Modifier) {
    val sun = remember(weather) { sunTimes(weather) }
    val nowMs = System.currentTimeMillis()
    val t: Float? = if (sun.second > sun.first && nowMs in sun.first..sun.second) {
        (nowMs - sun.first).toFloat() / (sun.second - sun.first).toFloat()
    } else null
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val stats = weatherStats(weather)
    Column(modifier.padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column {
                BasicText(
                    if (t != null) "SUNSET" else "SUNRISE",
                    style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.SemiBold).copy(letterSpacing = 1.sp),
                )
                BasicText(
                    clock.format(Date(if (t != null) sun.second else sun.first)),
                    style = txt(Color.White, 72.sp, FontWeight.Light),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                BasicText(
                    "Sunrise " + clock.format(Date(sun.first)),
                    style = txt(Color.White.copy(alpha = 0.7f), 16.sp, FontWeight.Medium),
                )
                BasicText(
                    "Sunset " + clock.format(Date(sun.second)),
                    style = txt(Color.White.copy(alpha = 0.7f), 16.sp, FontWeight.Medium),
                )
            }
        }
        SunArc(t, { 1f }, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            stats.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    BasicText(label, style = txt(Color.White.copy(alpha = 0.6f), 14.sp, FontWeight.Medium))
                    BasicText(value, style = txt(Color.White, 28.sp, FontWeight.Light))
                }
            }
        }
    }
}

/** Screen time, full screen: today's total, one bar, and the apps. */
@Composable
private fun ScreenTimeExpandedView(modifier: Modifier) {
    val context = LocalContext.current
    val usage by produceState<WidgetScreenTime?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { loadScreenTime(context) }.getOrNull() }
    }
    val u = usage
    Column(modifier.padding(horizontal = 56.dp, vertical = 40.dp)) {
        BasicText(
            "SCREEN TIME",
            style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.SemiBold).copy(letterSpacing = 1.sp),
        )
        Spacer(Modifier.height(8.dp))
        when {
            u == null -> BasicText(
                "Loading\u2026",
                style = txt(Color.White.copy(alpha = 0.7f), 18.sp, FontWeight.Medium),
            )
            !u.granted -> BasicText(
                "Usage access is off. Press OK to open the settings and allow Glass.",
                style = txt(Color.White.copy(alpha = 0.85f), 18.sp, FontWeight.Medium),
            )
            else -> {
                BasicText(formatDuration(u.totalMs), style = txt(Color.White, 72.sp, FontWeight.Light))
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    u.top.forEachIndexed { i, a ->
                        Box(Modifier.weight(a.ms.coerceAtLeast(1L).toFloat()).fillMaxHeight().background(UsageColors[i]))
                    }
                    val rest = u.totalMs - u.top.sumOf { it.ms }
                    if (rest > 0L) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().background(UsageOther))
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    u.top.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(UsageColors[i], CircleShape))
                            Spacer(Modifier.width(14.dp))
                            BasicText(
                                a.label,
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = txt(Color.White, 20.sp, FontWeight.Medium),
                            )
                            BasicText(formatDuration(a.ms), style = txt(Color.White.copy(alpha = 0.7f), 20.sp, FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}
