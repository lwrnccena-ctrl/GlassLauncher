package com.fares.glass.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.AppEntry
import com.fares.glass.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The apps card is a grid of this many columns and 2 rows. HomeScreen uses these numbers to move the selection. */
const val WIDGET_APP_COLS = 6
const val WIDGET_APP_COUNT = 12

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

private val CardColor = Color(0xFF1C1C1E)
private val Orange = Color(0xFFFF9F0A)
private val Blue = Color(0xFF3B82F6)

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

/**
 * The widgets page (landscape): month calendar on the left; weather and the date on top right; your first
 * apps under them. HomeScreen shows it when Left is pressed on the first app and slides it in from the left (see [WidgetsMotion]).
 *
 * [focus]: 0 = the weather card, 1.. = the apps (1 = the first one).
 * [progress] is read only while drawing (0 = hidden, 1 = shown), so animating it never recomposes the screen.
 * Animate it with tween(WidgetsMotion.DurationMs, easing = WidgetsMotion.Easing).
 */
@Composable
fun WidgetsPage(
    backdrop: Backdrop,
    apps: List<AppEntry>,
    weather: Weather?,
    focus: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
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
        Row(
            Modifier.fillMaxSize().padding(start = Metrics.Margin, end = Metrics.Margin, top = 36.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CalendarCard(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeatherCard(backdrop, weather, focus == 0, Modifier.weight(1.6f).fillMaxHeight())
                    DateCard(Modifier.weight(1f).fillMaxHeight())
                }
                AppsCard(apps, focus, Modifier.weight(1f).fillMaxWidth())
            }
        }
        BasicText(
            "Press Back to return home",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            style = txt(Color.White.copy(alpha = 0.55f), 12.sp, FontWeight.Medium),
        )
    }
}

// ---- Weather (this card replaces the steps counter of the phone version) ----

@Composable
private fun WeatherCard(backdrop: Backdrop, weather: Weather?, focused: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.widgetCard(shape, focused).padding(18.dp)) {
        BasicText("Weather", style = txt(Orange, 15.sp, FontWeight.SemiBold))
        if (weather != null && weather.place.isNotBlank()) {
            BasicText(weather.place, style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            // The same weather text as the home screen, drawn bigger.
            Box(Modifier.graphicsLayer { scaleX = 1.7f; scaleY = 1.7f; transformOrigin = TransformOrigin(0f, 0.5f) }) {
                WeatherPill(backdrop, weather, false, Modifier, visible = true, plain = true)
            }
        } else {
            BasicText("Weather isn't available yet", style = txt(Color.White.copy(alpha = 0.6f), 14.sp, FontWeight.Medium))
        }
        Spacer(Modifier.weight(1f))
        BasicText("OK for the forecast", style = txt(Color.White.copy(alpha = 0.45f), 12.sp, FontWeight.Medium))
    }
}

// ---- Date ----

@Composable
private fun DateCard(modifier: Modifier) {
    val now = Calendar.getInstance()
    val weekday = SimpleDateFormat("EEE", Locale.getDefault()).format(now.time)
    val day = now.get(Calendar.DAY_OF_MONTH).toString()
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3D6B8E), Color(0xFF13293D))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            BasicText(weekday, style = txt(Color.White, 24.sp, FontWeight.Medium).copy(fontFamily = FontFamily.Serif))
            BasicText(day, style = txt(Color.White, 78.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Serif))
        }
    }
}

// ---- Month calendar (Monday first, like the phone version) ----

@Composable
private fun CalendarCard(modifier: Modifier) {
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    // The grid starts on the Monday on or before the 1st, and always has 6 rows.
    val start = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    start.add(Calendar.DAY_OF_MONTH, -offset)
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time)
    val footer = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.time)
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(modifier.widgetCard(RoundedCornerShape(28.dp)).padding(18.dp)) {
        BasicText(title, style = txt(Color.White, 20.sp, FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            names.forEach {
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                    BasicText(it, style = txt(Color.White.copy(alpha = 0.75f), 13.sp, FontWeight.SemiBold))
                }
            }
        }
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, r * 7 + c) }
                    val inMonth = d.get(Calendar.MONTH) == month
                    val isToday = inMonth && d.get(Calendar.DAY_OF_MONTH) == today
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (isToday) Box(Modifier.size(36.dp).background(Blue, RoundedCornerShape(11.dp)))
                        BasicText(
                            d.get(Calendar.DAY_OF_MONTH).toString(),
                            style = txt(Color.White.copy(alpha = if (inMonth) 1f else 0.25f), 15.sp, FontWeight.Medium),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BasicText(footer, style = txt(Color.White.copy(alpha = 0.6f), 13.sp, FontWeight.Medium))
    }
}

// ---- Apps (the first apps of the home screen, in a small grid) ----

@Composable
private fun AppsCard(apps: List<AppEntry>, focus: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.widgetCard(shape).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until WIDGET_APP_COUNT / WIDGET_APP_COLS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until WIDGET_APP_COLS) {
                        val i = r * WIDGET_APP_COLS + c
                        val entry = apps.getOrNull(i)
                        if (entry != null) {
                            AppCell(entry, focus == i + 1, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        BasicText(
            apps.getOrNull(focus - 1)?.label ?: "Your apps",
            style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppCell(entry: AppEntry, focused: Boolean, modifier: Modifier) {
    val icon = rememberAppIcon(entry.pkg)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val s = if (focused) 1.08f else 1f
                scaleX = s
                scaleY = s
            }
            .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(0.78f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
        }
    }
}

/** The app's launcher icon, loaded off the main thread. */
@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(pkg), 128).asImageBitmap() }.getOrNull()
        }
    }
    return state.value
}

private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, size, size)
    d.draw(canvas)
    return bmp
}
