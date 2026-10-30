package com.fares.glass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.DayForecast
import com.fares.glass.data.Weather
import kotlin.math.roundToInt

private val IslandFill = Color(0xF02E2A44)
private val IslandInk = Color(0xFF111114)
private val IslandWhite = Color.White

private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

/** Ease out with a small overshoot at the end, like the Control Center. */
private fun easeBack(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    val c1 = 0.6f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/** Progress of one edge: it starts [delay] into the animation and lasts [len] (both 0..1 of the whole). */
private fun edge(p: Float, delay: Float, len: Float) = easeBack((p - delay) / len)

private fun hourLabel(h: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return "$hh ${if (h < 12) "AM" else "PM"}"
}

/**
 * The weather pill grows into a full-screen weather panel, like the Dynamic Island.
 *
 * The panel is a window that opens over content that stays where it is: the sides and the bottom open
 * first, and the top edge starts later, so the top looks left behind and then catches up.
 * Everything is done while drawing, so the animation never recomposes the home screen.
 *
 * [progress] is 0 (closed) to 1 (open). [source] is where the small weather text sits on the screen (pixels).
 * [selected] is the selected day; one past the last day is the "Change location" row.
 */
@Composable
fun WeatherIsland(
    weather: Weather,
    days: List<DayForecast>,
    selected: Int,
    source: Rect?,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().drawWithContent {
            val p = progress()
            if (p <= 0.001f) return@drawWithContent
            val src = source ?: Rect(size.width * 0.25f, size.height * 0.05f, size.width * 0.5f, size.height * 0.13f)
            val padX = 16.dp.toPx()
            val padY = 8.dp.toPx()
            val l0 = src.left - padX
            val t0 = src.top - padY
            val r0 = src.right + padX
            val b0 = src.bottom + padY
            val inset = 12.dp.toPx()
            val l1 = inset
            val t1 = inset
            val r1 = size.width - inset
            val b1 = size.height - inset

            val l = mix(l0, l1, edge(p, 0f, 0.7f))
            val r = mix(r0, r1, edge(p, 0f, 0.7f))
            val b = mix(b0, b1, edge(p, 0.05f, 0.7f))
            // The top edge starts later: this is the part that looks left behind.
            val t = mix(t0, t1, edge(p, 0.3f, 0.7f))
            val radius = mix(22.dp.toPx(), 36.dp.toPx(), p.coerceIn(0f, 1f))
            val rect = RoundRect(l, t, maxOf(r, l + 1f), maxOf(b, t + 1f), CornerRadius(radius))
            val path = Path().apply { addRoundRect(rect) }

            // The home screen dims behind the panel.
            drawRect(Color.Black.copy(alpha = 0.35f * (p / 0.6f).coerceIn(0f, 1f)))
            val k = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
            clipPath(path) {
                drawRect(lerp(IslandWhite.copy(alpha = 0.28f), IslandFill, k))
                this@drawWithContent.drawContent()
            }
            drawPath(path, IslandWhite.copy(alpha = mix(0.9f, 0.25f, k)), style = Stroke(1.5.dp.toPx()))
        },
    ) {
        // The small weather text, where it sits on the home screen; it fades out as the panel opens.
        if (source != null) {
            Box(
                Modifier
                    .offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                    .graphicsLayer { alpha = 1f - (progress() / 0.18f).coerceIn(0f, 1f) },
            ) { WeatherWidget(weather) }
        }

        // The weather information; it is laid out at its final place and fades in once the panel is half open.
        Box(
            Modifier
                .fillMaxSize()
                .padding(40.dp)
                .graphicsLayer { alpha = ((progress() - 0.4f) / 0.35f).coerceIn(0f, 1f) },
        ) {
            IslandContent(weather, days, selected)
        }
    }
}

@Composable
private fun IslandContent(weather: Weather, days: List<DayForecast>, selected: Int) {
    val now = System.currentTimeMillis()
    val isDay = weather.sunrise == 0L || now in weather.sunrise..weather.sunset
    val (icon, words) = weatherDescribe(weather.code, isDay)
    val today = days.firstOrNull()
    val shown = days.getOrNull(selected.coerceIn(0, (days.size - 1).coerceAtLeast(0)))
    val slots = shown?.hours?.filter { it.hour % 3 == 0 }.orEmpty()

    Row(Modifier.fillMaxSize()) {
        // Left: the weather right now.
        Column(Modifier.weight(0.36f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                BasicText(
                    weather.place,
                    style = txt(IslandWhite.copy(alpha = 0.85f), 20.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText("${weather.tempC}\u00B0", style = txt(IslandWhite, 104.sp, FontWeight.SemiBold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(icon, style = txt(IslandWhite, 30.sp, FontWeight.Medium))
                    Spacer(Modifier.width(10.dp))
                    BasicText(words, style = txt(IslandWhite, 26.sp, FontWeight.Medium), maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatLine("Feels like", "${weather.feelsC}\u00B0")
                if (today != null) {
                    StatLine("High / Low", "${today.maxC}\u00B0 / ${today.minC}\u00B0")
                    StatLine("Chance of rain", "${today.rainPct}%")
                }
                StatLine("Humidity", "${weather.humidity}%")
                StatLine("Wind", "${weather.windKmh} km/h")
            }
        }

        Spacer(Modifier.width(28.dp))

        // Right: the hours of the selected day, then the days.
        Column(Modifier.weight(0.64f).fillMaxHeight()) {
            BasicText(
                if (shown != null) "Hourly \u00B7 ${shown.label}" else "Hourly",
                style = txt(IslandWhite.copy(alpha = 0.7f), 14.sp, FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEach { h ->
                    val (hIcon, _) = weatherDescribe(h.code, h.hour in 6..17)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(IslandWhite.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BasicText(hourLabel(h.hour), style = txt(IslandWhite.copy(alpha = 0.7f), 12.sp, FontWeight.Medium))
                        BasicText(hIcon, style = txt(IslandWhite, 24.sp, FontWeight.Medium))
                        BasicText("${h.tempC}\u00B0", style = txt(IslandWhite, 16.sp, FontWeight.SemiBold))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            BasicText("Forecast", style = txt(IslandWhite.copy(alpha = 0.7f), 14.sp, FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEachIndexed { i, d ->
                    val (dIcon, _) = weatherDescribe(d.code, true)
                    DayRow(d.label, dIcon, d.rainPct, d.maxC, d.minC, i == selected)
                }
                ActionRow("Change location", selected >= days.size)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = txt(IslandWhite.copy(alpha = 0.7f), 15.sp, FontWeight.Medium))
        BasicText(value, style = txt(IslandWhite, 15.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun DayRow(label: String, icon: String, rain: Int, maxC: Int, minC: Int, selected: Boolean) {
    val fg = if (selected) IslandInk else IslandWhite
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                if (selected) IslandWhite.copy(alpha = 0.95f) else IslandWhite.copy(alpha = 0.07f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = txt(fg, 15.sp, FontWeight.SemiBold), maxLines = 1)
        BasicText(icon, style = txt(fg, 18.sp, FontWeight.Medium))
        Spacer(Modifier.width(14.dp))
        BasicText(
            "$rain%",
            Modifier.width(44.dp),
            style = txt(fg.copy(alpha = 0.65f), 13.sp, FontWeight.Medium).copy(textAlign = TextAlign.End),
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            "$maxC\u00B0 / $minC\u00B0",
            Modifier.width(84.dp),
            style = txt(fg, 15.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.End),
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionRow(label: String, selected: Boolean) {
    val fg = if (selected) IslandInk else IslandWhite
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                if (selected) IslandWhite.copy(alpha = 0.95f) else IslandWhite.copy(alpha = 0.07f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = txt(fg, 15.sp, FontWeight.SemiBold), maxLines = 1)
    }
}
