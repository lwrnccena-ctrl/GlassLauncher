package com.fares.glass.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.Weather

/** Icon and words for a WMO weather code (what Open-Meteo returns). */
fun weatherDescribe(code: Int, day: Boolean): Pair<String, String> = when (code) {
    0 -> if (day) "\u2600\uFE0F" to "Sunny" else "\uD83C\uDF19" to "Clear"
    1 -> if (day) "\uD83C\uDF24\uFE0F" to "Mostly sunny" else "\uD83C\uDF19" to "Mostly clear"
    2 -> "\u26C5" to "Partly cloudy"
    3 -> "\u2601\uFE0F" to "Cloudy"
    45, 48 -> "\uD83C\uDF2B\uFE0F" to "Fog"
    in 51..57 -> "\uD83C\uDF26\uFE0F" to "Drizzle"
    in 61..67 -> "\uD83C\uDF27\uFE0F" to "Rain"
    in 71..77, 85, 86 -> "\u2744\uFE0F" to "Snow"
    in 80..82 -> "\uD83C\uDF27\uFE0F" to "Showers"
    in 95..99 -> "\u26C8\uFE0F" to "Thunderstorm"
    else -> "\u2601\uFE0F" to ""
}

/**
 * Place name (the detected city), temperature and weather words.
 * No place known: "Location unavailable" and "--°C" (no weather is shown for a place that is not the user's).
 * Before the first answer: "Locating..." and "--°C".
 */
@Composable
fun WeatherWidget(weather: Weather, modifier: Modifier = Modifier) {
    val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 6f)
    // No weather data to show yet, or no place found: only the place line and "--°C".
    val noData = weather.isPlaceholder || weather.place.isBlank()
    val place = when {
        weather.isPlaceholder -> "Locating\u2026"
        weather.place.isNotBlank() -> weather.place
        else -> "Location unavailable"
    }
    val now = System.currentTimeMillis()
    val day = weather.sunrise == 0L || now in weather.sunrise..weather.sunset
    val (icon, words) = weatherDescribe(weather.code, day)
    val temp = if (noData) "--\u00B0C" else "${weather.tempC}\u00B0C"
    val line = if (noData || words.isEmpty()) temp else "$temp $words"

    Column(modifier) {
        BasicText(
            place,
            Modifier.widthIn(max = 240.dp),
            style = txt(Color.White.copy(alpha = 0.85f), 14.sp, FontWeight.Medium).copy(shadow = shadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!noData) {
                BasicText(icon, style = txt(Color.White, 22.sp, FontWeight.Medium))
                Spacer(Modifier.width(6.dp))
            }
            BasicText(line, style = txt(Color.White, 22.sp, FontWeight.SemiBold).copy(shadow = shadow), maxLines = 1)
        }
    }
}
