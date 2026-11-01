package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.Weather

/** Same height as the round buttons of the icon bar, so the pill lines up with them. */
val WeatherPillHeight = 40.dp

/** What is inside the pill: the weather icon, the temperature and the words ("27°C Partly cloudy"). */
@Composable
fun WeatherPillBody(weather: Weather, ink: Color = Color.White) {
    val now = System.currentTimeMillis()
    val isDay = weather.sunrise == 0L || now in weather.sunrise..weather.sunset
    val (icon, words) = weatherDescribe(weather.code, isDay)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(icon, style = txt(ink, 17.sp, FontWeight.Medium))
        Spacer(Modifier.width(8.dp))
        BasicText("${weather.tempC}\u00B0C", style = txt(ink, 15.sp, FontWeight.SemiBold))
        Spacer(Modifier.width(8.dp))
        BasicText(words, style = txt(ink.copy(alpha = 0.8f), 14.sp, FontWeight.Medium), maxLines = 1)
    }
}

/**
 * The weather in a glass pill, to the left of the icon bar.
 * When it is selected it turns into a white pill with dark text, like the selected buttons of the icon bar.
 */
@Composable
fun WeatherPill(backdrop: Backdrop, weather: Weather, focused: Boolean, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 600f), label = "weatherPill")
    val ink = lerp(Color.White, Color(0xFF111114), f)
    val shape = RoundedCornerShape(50)
    Glass(backdrop, modifier, shape, tint = Color.White.copy(alpha = 0.10f)) {
        Box(
            Modifier
                .clip(shape)
                .height(WeatherPillHeight)
                .background(Color.White.copy(alpha = 0.94f * f))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            WeatherPillBody(weather, ink)
        }
    }
}
