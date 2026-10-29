package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.DayForecast

private val FcInk = Color(0xFF111114)
private val FcWhite = Color.White
private val FcSun = Color(0xFFFFD60A)
private val FcBlue = Color(0xFF0A84FF)

/** Icon for an Open-Meteo weather code. */
private fun iconFor(code: Int): ImageVector = when (code) {
    0 -> Icons.Rounded.WbSunny
    1, 2, 3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Bolt
    else -> Icons.Rounded.Cloud
}

/** Short text for an Open-Meteo weather code. */
private fun textFor(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "Cloudy"
}

private fun tintFor(code: Int): Color = when (code) {
    0 -> FcSun
    in 51..67, in 80..82, in 95..99 -> FcBlue
    else -> FcWhite
}

/** 0 -> "12 AM", 15 -> "3 PM". */
private fun hourText(h: Int): String {
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
    return "$h12 $suffix"
}

/**
 * Forecast of the next days. Rows: one per day, then "Change location".
 * [selected] is the highlighted row (== days.size means "Change location").
 * When [showHours] is true the hours (every 3 hours) of the selected day are shown instead,
 * and [hourIndex] is the highlighted hour.
 */
@Composable
fun ForecastMenu(
    backdrop: Backdrop,
    days: List<DayForecast>,
    selected: Int,
    showHours: Boolean,
    hourIndex: Int,
    modifier: Modifier = Modifier,
) {
    Glass(
        backdrop,
        modifier.width(290.dp),
        RoundedCornerShape(28.dp),
        tint = FcWhite.copy(alpha = 0.08f),
    ) {
        Column(Modifier.padding(12.dp)) {
            val day = days.getOrNull(selected)
            if (showHours && day != null) {
                BasicText(day.label, Modifier.padding(start = 8.dp, bottom = 6.dp), style = txt(FcWhite, 18.sp, FontWeight.Bold))
                val slots = day.hours.filter { it.hour % 3 == 0 }
                if (slots.isEmpty()) {
                    BasicText(
                        "No hourly data", Modifier.padding(8.dp),
                        style = txt(FcWhite.copy(alpha = 0.6f), 13.sp, FontWeight.Medium),
                    )
                }
                slots.forEachIndexed { i, h ->
                    ForecastRow(
                        icon = iconFor(h.code), tint = tintFor(h.code),
                        title = hourText(h.hour), subtitle = textFor(h.code),
                        trailing = "${h.tempC}\u00B0", focused = i == hourIndex,
                    )
                }
            } else {
                BasicText("Forecast", Modifier.padding(start = 8.dp, bottom = 6.dp), style = txt(FcWhite, 18.sp, FontWeight.Bold))
                days.forEachIndexed { i, d ->
                    ForecastRow(
                        icon = iconFor(d.code), tint = tintFor(d.code),
                        title = d.label, subtitle = textFor(d.code),
                        trailing = "${d.maxC}\u00B0 / ${d.minC}\u00B0", focused = i == selected,
                    )
                }
                ForecastRow(
                    icon = Icons.Rounded.Search, tint = FcWhite,
                    title = "Change location", subtitle = null,
                    trailing = null, focused = selected >= days.size,
                )
            }
        }
    }
}

@Composable
private fun ForecastRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    trailing: String?,
    focused: Boolean,
) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 650f), label = "fcFocus")
    val fg = lerp(FcWhite, FcInk, f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .graphicsLayer { scaleX = 1f + 0.025f * f; scaleY = 1f + 0.025f * f }
            .background(lerp(FcWhite.copy(alpha = 0.08f), FcWhite.copy(alpha = 0.95f), f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            rememberVectorPainter(icon), null, Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (f > 0.5f && tint == FcWhite) FcInk else tint),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
            if (subtitle != null) {
                BasicText(subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.height(0.dp))
            BasicText(trailing, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
        }
    }
}
