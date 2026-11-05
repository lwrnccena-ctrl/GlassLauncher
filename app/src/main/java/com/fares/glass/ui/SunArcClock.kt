package com.fares.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fixed size of the sun arc, so it stays the same however big or small the clock inside it is. */
private val ArcWidth = 440.dp
private val ArcHeight = 230.dp

/** Room under the arc for the sunrise / sunset times. */
private val LabelRoom = 24.dp

private fun timeLabel(ms: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

/**
 * Draws [content] (the clock) inside a thin white sun arc. [sunrise] and [sunset] are in milliseconds,
 * [now] is the current time in milliseconds. The sun (a small circle with thin rays) sits on the arc at
 * today's progress, and the sunrise and sunset times are written under the two ends of the arc.
 */
@Composable
fun SunArcClock(
    sunrise: Long,
    sunset: Long,
    now: Long,
    content: @Composable () -> Unit,
) {
    val span = (sunset - sunrise).coerceAtLeast(1L)
    val raw = (now - sunrise).toFloat() / span
    val isDay = raw in 0f..1f
    val p = raw.coerceIn(0f, 1f)
    val riseText = remember(sunrise / 60_000L) { timeLabel(sunrise) }
    val setText = remember(sunset / 60_000L) { timeLabel(sunset) }

    Box(Modifier.size(ArcWidth, ArcHeight)) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = 34.dp.toPx()
            val padTop = 10.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height - LabelRoom.toPx()
            val rx = cx - padX
            val ry = cy - padTop
            val thin = 1.5.dp.toPx()

            // The whole arc: thin and white, fainter where the sun has not been yet.
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )
            // The part of the day that has passed.
            drawArc(
                color = Color.White.copy(alpha = if (isDay) 0.95f else 0.45f),
                startAngle = 180f,
                sweepAngle = 180f * p,
                useCenter = false,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )

            // The sun: a small circle with thin rays around it.
            val a = Math.toRadians((180f + 180f * p).toDouble())
            val center = Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
            val sun = Color.White.copy(alpha = if (isDay) 1f else 0.5f)
            val r = 5.dp.toPx()
            drawCircle(sun, r, center)
            val from = r + 3.dp.toPx()
            val to = r + 8.dp.toPx()
            for (k in 0 until 8) {
                val ang = k * (PI / 4.0)
                val dx = cos(ang).toFloat()
                val dy = sin(ang).toFloat()
                drawLine(
                    color = sun,
                    start = Offset(center.x + dx * from, center.y + dy * from),
                    end = Offset(center.x + dx * to, center.y + dy * to),
                    strokeWidth = thin,
                    cap = StrokeCap.Round,
                )
            }
        }

        // The clock sits inside the arc, just above the line between the two ends.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LabelRoom + 6.dp)) {
            content()
        }

        // Sunrise (left end) and sunset (right end).
        val label = txt(Color.White.copy(alpha = 0.85f), 13.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center)
        BasicText(riseText, Modifier.align(Alignment.BottomStart).width(68.dp), style = label)
        BasicText(setText, Modifier.align(Alignment.BottomEnd).width(68.dp), style = label)
    }
}
