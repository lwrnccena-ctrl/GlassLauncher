package com.fares.glass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Your photo, filling the screen. Until a photo is found in Glass/depth/ it draws a built-in default
 * wallpaper (a soft dusk gradient with a warm glow), so first-time setup never shows a black screen.
 */
@Composable
fun DepthBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
    } else {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16213E), Color(0xFF2F3E6B), Color(0xFF7A5C8A), Color(0xFFD99A7C)),
                ),
            ),
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0x55FFD9A8), Color.Transparent), radius = 900f),
                ),
            )
        }
    }
}

/** Size of the Classic clock digits. Lower these to make the clock smaller (the arc keeps its size). */
private val ClockSizeWithArc: TextUnit = 64.sp
private val ClockSizeNoArc: TextUnit = 110.sp

/**
 * Clock digits in the system font (bold), drawn like frosted glass:
 * a see-through white fill that fades from top to bottom, a thin lighter edge, and a soft shadow.
 */
@Composable
private fun FrostedClockText(text: String, size: TextUnit) {
    val base = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Default,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center,
    )
    Box {
        // Edge + soft shadow.
        BasicText(
            text,
            style = base.copy(
                color = Color.White.copy(alpha = 0.55f),
                drawStyle = Stroke(width = 2f),
                shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 4f), 14f),
            ),
        )
        // Frosted fill.
        BasicText(
            text,
            style = base.copy(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.38f)),
                ),
            ),
        )
    }
}

/** Draws its content [scale] times bigger and takes the bigger space in the layout. */
@Composable
private fun ScaleUp(scale: Float, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val p = measurables.firstOrNull()?.measure(Constraints())
        if (p == null) {
            layout(0, 0) {}
        } else {
            layout((p.width * scale).roundToInt(), (p.height * scale).roundToInt()) {
                p.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/**
 * The clock of Simple mode, with the weather under it when given. It is drawn between the photo and
 * the foreground cutout, so the cutout can cover it.
 * [style] is the style chosen in Control Center > Clock: Classic gives the big frosted digits (with the
 * date above); the other styles (Stacked, Analog, Pill, Flip) are drawn bigger, as on the normal home screen.
 * [sun] = (sunrise, sunset) in milliseconds adds the sun arc around the clock.
 */
@Composable
fun DepthClock(
    modifier: Modifier = Modifier,
    style: Int = ClockStyle.CLASSIC,
    sun: Pair<Long, Long>? = null,
    weather: (@Composable () -> Unit)? = null,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val time = remember(now / 60_000L) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3_600_000L) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val ink = Color(0xFFF2F6F7)
    val classic = style == ClockStyle.CLASSIC
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 36.dp).fillMaxWidth(0.74f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (classic) {
                BasicText(
                    date,
                    style = TextStyle(
                        color = ink.copy(alpha = 0.85f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default, textAlign = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.height(6.dp))
            }
            val clock: @Composable () -> Unit = {
                if (classic) {
                    FrostedClockText(time, if (sun != null) ClockSizeWithArc else ClockSizeNoArc)
                } else {
                    ScaleUp(1.5f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ClockBody(style, now, time, date)
                        }
                    }
                }
            }
            if (sun != null) SunArcClock(sun.first, sun.second, now, clock) else clock()
            if (weather != null) {
                Box(Modifier.padding(top = 10.dp)) { weather() }
            }
        }
    }
}
