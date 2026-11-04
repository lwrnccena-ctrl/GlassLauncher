package com.fares.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [content] (the clock) with a sun arc over it. [sunrise] and [sunset] are in milliseconds,
 * [now] is the current time in milliseconds. The sun sits on the arc at today's progress.
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

    Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.padding(top = 56.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            content()
        }
        Canvas(Modifier.matchParentSize()) {
            val inset = 16.dp.toPx()
            val rx = (size.width / 2f) - inset
            val ry = size.height - inset * 2f
            val cx = size.width / 2f
            val cy = size.height - inset

            // Track: upper half of an ellipse, dashed
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f)),
                ),
            )
            // Elapsed part of the day
            drawArc(
                color = Color(0xFFFFD36E).copy(alpha = if (isDay) 0.9f else 0.35f),
                startAngle = 180f,
                sweepAngle = 180f * p,
                useCenter = false,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Sun
            val a = Math.toRadians((180f + 180f * p).toDouble())
            val sx = cx + rx * cos(a).toFloat()
            val sy = cy + ry * sin(a).toFloat()
            val c = if (isDay) Color(0xFFFFD36E) else Color.White.copy(alpha = 0.4f)
            drawCircle(c.copy(alpha = 0.25f), 14.dp.toPx(), Offset(sx, sy))
            drawCircle(c, 7.dp.toPx(), Offset(sx, sy))
        }
    }
}
