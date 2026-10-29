package com.fares.glass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.DayForecast

private val MenuInk = Color(0xFF111114)

private fun hourLabel(h: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

/**
 * The forecast list that opens from the weather text. First the next days (high / low and the weather in
 * words); OK on a day shows its hours (every 3 hours); OK or Back goes back to the days.
 */
@Composable
fun ForecastMenu(
    backdrop: Backdrop,
    days: List<DayForecast>,
    selectedDay: Int,
    hourLevel: Boolean,
    hourIndex: Int,
    modifier: Modifier = Modifier,
) {
    val day = days.getOrNull(selectedDay)
    Box(modifier.width(300.dp)) {
        Glass(backdrop, Modifier.matchParentSize(), shape = RoundedCornerShape(22.dp), tint = Color.White.copy(alpha = 0.10f))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            BasicText(
                if (hourLevel && day != null) "${day.label} \u00B7 OK or Back for the days" else "Next days \u00B7 OK shows the hours",
                Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = txt(Color.White.copy(alpha = 0.55f), 11.sp, FontWeight.Medium),
            )
            if (!hourLevel) {
                days.forEachIndexed { i, d ->
                    ForecastRow(
                        d.label,
                        "${d.maxC}\u00B0 / ${d.minC}\u00B0 ${weatherDescribe(d.code, true).second}",
                        i == selectedDay,
                    )
                }
            } else {
                day?.hours?.filter { it.hour % 3 == 0 }?.forEachIndexed { i, h ->
                    ForecastRow(
                        hourLabel(h.hour),
                        "${h.tempC}\u00B0 ${weatherDescribe(h.code, h.hour in 6..17).second}",
                        i == hourIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastRow(left: String, right: String, selected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            left, Modifier.weight(1f),
            style = txt(if (selected) MenuInk else Color.White, 14.sp, FontWeight.SemiBold),
            maxLines = 1,
        )
        BasicText(
            right,
            style = txt(if (selected) MenuInk.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
