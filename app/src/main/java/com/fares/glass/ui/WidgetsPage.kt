package com.fares.glass.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.Weather
import com.fares.glass.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
private val Blue = Color(0xFF3B82F6)
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
    .background(CardColor.copy(alpha = 0.88f), shape)
    .border(
        if (focused) 3.dp else 1.dp,
        if (focused) Color.White else Color.White.copy(alpha = 0.10f),
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

/**
 * The widgets page (landscape, Android TV). Top row: the weather card and the month calendar, both the same size.
 * Bottom row: the sun card under the weather, and screen time under the calendar.
 * HomeScreen shows it when Left is pressed on the first app and slides it in from the left (see [WidgetsMotion]).
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
    // The entrance of the tiles. WidgetsPage is only composed while the page is shown, so this replays at every open.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(WidgetsEnter.TotalMs, easing = LinearEasing)) }
    val raw: () -> Float = { enter.value }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress()
                // One strip with the home screen: the page enters from the left edge (-1 screen width -> 0).
                translationX = (p - 1f) * size.width
                // Blurred while it moves, sharp once it has settled (Android 12+; older versions slide without blur).
                if (Build.VERSION.SDK_INT >= 31) {
                    val px = WidgetsMotion.MaxBlur.toPx() * WidgetsMotion.blurMix(p)
                    renderEffect = if (px > 0.5f) BlurEffect(px, px) else null
                }
            },
    ) {
        // The page brings its own background (the soft wallpaper, dimmed), so it slides in as a whole page.
        backdrop.frosted?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        Column(
            Modifier.fillMaxSize().padding(start = Metrics.Margin, end = Metrics.Margin, top = 36.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.weight(172f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WeatherCard(backdrop, weather, true, raw, Modifier.weight(1f).fillMaxHeight())
                CalendarCard(raw, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(90f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SunCard(weather, raw, Modifier.weight(1f).fillMaxHeight())
                ScreenTimeCard(raw, Modifier.weight(1f).fillMaxHeight())
            }
        }
        BasicText(
            "Press Back to return home",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            style = txt(Color.White.copy(alpha = 0.55f), 12.sp, FontWeight.Medium),
        )
    }
}

// ---- Weather ----

@Composable
private fun WeatherCard(backdrop: Backdrop, weather: Weather?, focused: Boolean, enter: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.enterTile(enter, 0).widgetCard(shape, focused).padding(18.dp)) {
        BasicText("Weather", style = txt(Orange, 15.sp, FontWeight.SemiBold))
        if (weather != null && weather.place.isNotBlank()) {
            BasicText(weather.place, style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            // The same weather text as the home screen, drawn bigger.
            Box(
                Modifier.graphicsLayer {
                    scaleX = 1.7f
                    scaleY = 1.7f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    // The big weather text fades in a moment after its card.
                    alpha = WidgetsEnter.out(WidgetsEnter.local(enter(), 0f, 450f, 300f))
                },
            ) {
                WeatherPill(backdrop, weather, false, Modifier, visible = true, plain = true)
            }
        } else {
            BasicText("Weather isn't available yet", style = txt(Color.White.copy(alpha = 0.6f), 14.sp, FontWeight.Medium))
        }
        Spacer(Modifier.weight(1f))
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
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time)
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(modifier.enterTile(enter, 1).widgetCard(RoundedCornerShape(28.dp)).padding(18.dp)) {
        BasicText(title, style = txt(Color.White, 20.sp, FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            names.forEach {
                Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                    BasicText(it, style = txt(Color.White.copy(alpha = 0.75f), 13.sp, FontWeight.SemiBold))
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
                                    .width(36.dp).height(28.dp)
                                    .graphicsLayer {
                                        // Today's blue square pops in last.
                                        val l = WidgetsEnter.local(enter(), 1f, 420f, 900f)
                                        val sc = WidgetsEnter.back(l)
                                        scaleX = sc
                                        scaleY = sc
                                        alpha = l
                                    }
                                    .background(Blue, RoundedCornerShape(9.dp)),
                            )
                        }
                        BasicText(
                            d.get(Calendar.DAY_OF_MONTH).toString(),
                            style = txt(Color.White.copy(alpha = if (inMonth) 1f else 0.25f), 15.sp, FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

// ---- Sun: sunrise, sunset and the weather readings (under the weather card) ----

/**
 * The four readings on the right of the sun card, as (label, value).
 * TODO: connect these to your Weather class (wind, humidity, temperature, pressure). Until then they show "--".
 */
private fun weatherStats(w: Weather?): List<Pair<String, String>> = listOf(
    "Wind" to "--",
    "Humidity" to "--",
    "Temp" to "--",
    "Pressure" to "--",
)

/** Sunrise and sunset of the place (or the usual times while the weather is not loaded), the same as HomeScreen uses. */
private fun sunTimes(w: Weather?): Pair<Long, Long> =
    w?.takeIf { !it.isPlaceholder && it.sunrise > 0L && it.sunset > 0L }
        ?.let { it.sunrise + WeatherRepository.skewMs(it.zone) to it.sunset + WeatherRepository.skewMs(it.zone) }
        ?: WeatherRepository.fallbackTimes()

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
    val stats = weatherStats(weather)
    val dim = Color.White.copy(alpha = 0.75f)
    Row(
        modifier.enterTile(enter, 2).widgetCard(RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: the sun arc with sunrise and sunset under its two ends.
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SunArc(t, enter, Modifier.weight(1f).fillMaxWidth())
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Sunrise and sunset fade up once the arc has drawn itself.
                        val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 2f, 400f, 500f))
                        alpha = l
                        translationY = (1f - l) * 8.dp.toPx()
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    BasicText("Sunrise", style = txt(dim, 12.sp, FontWeight.Medium))
                    BasicText(clock.format(Date(sun.first)), style = txt(Color.White, 14.sp, FontWeight.SemiBold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText("Sunset", style = txt(dim, 12.sp, FontWeight.Medium))
                    BasicText(clock.format(Date(sun.second)), style = txt(Color.White, 14.sp, FontWeight.SemiBold))
                }
            }
        }
        Box(
            Modifier.padding(horizontal = 14.dp).width(1.dp).fillMaxHeight(0.8f)
                .background(Color.White.copy(alpha = 0.15f)),
        )
        // Right: wind, humidity, temperature, pressure.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 2f, 400f, 600f))
                    alpha = l
                    translationX = (1f - l) * 14.dp.toPx()
                },
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            stats.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(label, style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium))
                    BasicText(value, style = txt(Color.White, 13.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

/**
 * The dashed sun arc. [t] = 0..1 is how far the sun is along it (null = night, no sun).
 * On entrance the arc draws itself from sunrise to sunset, then the sun travels from sunrise to its place.
 */
@Composable
private fun SunArc(t: Float?, enter: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val raw = enter()
        val drawn = WidgetsEnter.local(raw, 2f, 700f, 150f)                          // how much of the arc is drawn
        val travel = WidgetsEnter.out(WidgetsEnter.local(raw, 2f, 800f, 450f))      // how far the sun has travelled
        val pad = 10.dp.toPx()
        val x0 = pad
        val x1 = size.width - pad
        val yb = size.height - 8.dp.toPx()
        val peak = 6.dp.toPx()
        val cx = (x0 + x1) / 2f
        val cy = 2f * peak - yb // control point of the curve: its top lands on [peak]
        fun ax(u: Float) = (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * cx + u * u * x1
        fun ay(u: Float) = (1 - u) * (1 - u) * yb + 2 * (1 - u) * u * cy + u * u * yb
        val steps = 48
        val tt = t?.let { it * travel }
        if (tt != null && tt > 0f) {
            // The soft area under the arc, up to the sun.
            val n = (steps * tt).toInt().coerceAtLeast(1)
            val shade = Path().apply {
                moveTo(x0, yb)
                for (i in 0..n) lineTo(ax(tt * i / n), ay(tt * i / n))
                lineTo(ax(tt), yb)
                close()
            }
            drawPath(shade, Color.White.copy(alpha = 0.10f))
        }
        if (drawn > 0f) {
            val n = (steps * drawn).toInt().coerceAtLeast(1)
            val arc = Path().apply {
                moveTo(ax(0f), ay(0f))
                for (i in 1..n) lineTo(ax(drawn * i / n), ay(drawn * i / n))
            }
            drawPath(
                arc, Color.White,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
        drawCircle(Color.White.copy(alpha = drawn), 4.dp.toPx(), Offset(x0, yb))
        drawCircle(Color.White.copy(alpha = drawn), 4.dp.toPx(), Offset(x1, yb))
        if (tt != null) drawCircle(SunYellow.copy(alpha = drawn), 8.dp.toPx(), Offset(ax(tt), ay(tt)))
    }
}

// ---- Screen time (under the calendar) ----

private class AppUse(val label: String, val ms: Long)
private class ScreenTimeData(val granted: Boolean, val totalMs: Long, val top: List<AppUse>)

private fun formatDuration(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

@Suppress("DEPRECATION")
private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Today's foreground time per app (needs "Usage access" for this app in the TV's settings). */
private fun loadScreenTime(context: Context): ScreenTimeData {
    if (!hasUsageAccess(context)) return ScreenTimeData(false, 0L, emptyList())
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
        AppUse(label, s.totalTimeInForeground)
    }
    return ScreenTimeData(true, used.sumOf { it.totalTimeInForeground }, top)
}

@Composable
private fun ScreenTimeCard(enter: () -> Float, modifier: Modifier) {
    val context = LocalContext.current
    val usage by produceState<ScreenTimeData?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { loadScreenTime(context) }.getOrNull() }
    }
    val u = usage
    Column(
        modifier.enterTile(enter, 3).widgetCard(RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("Screen time", style = txt(Orange, 15.sp, FontWeight.SemiBold))
            if (u != null && u.granted) {
                BasicText("${formatDuration(u.totalMs)} today", style = txt(Color.White, 15.sp, FontWeight.SemiBold))
            }
        }
        if (u != null && !u.granted) {
            BasicText(
                "Allow Usage access for Glass in the TV settings to see your screen time.",
                style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.Medium),
            )
        } else if (u != null) {
            // One bar split by app (the grey part is every other app).
            val topSum = u.top.sumOf { it.ms }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .graphicsLayer {
                        // The bar grows from the left.
                        scaleX = WidgetsEnter.out(WidgetsEnter.local(enter(), 3f, 650f, 300f))
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                u.top.forEachIndexed { i, a ->
                    Box(Modifier.weight(a.ms.coerceAtLeast(1L).toFloat()).fillMaxHeight().background(UsageColors[i]))
                }
                val rest = u.totalMs - topSum
                if (rest > 0L) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().background(UsageOther))
                if (u.top.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight().background(UsageOther))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    Column(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                // The app names and times fade up one after another.
                                val l = WidgetsEnter.out(WidgetsEnter.local(enter(), 3f, 400f, 550f + i * 80f))
                                alpha = l
                                translationY = (1f - l) * 10.dp.toPx()
                            },
                    ) {
                        u.top.getOrNull(i)?.let { a ->
                            BasicText(
                                a.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = txt(Color.White.copy(alpha = 0.7f), 12.sp, FontWeight.Medium),
                            )
                            BasicText(formatDuration(a.ms), style = txt(UsageColors[i], 14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}
