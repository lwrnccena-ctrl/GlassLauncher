package com.fares.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** The clock styles the user can pick in Control Center > Clock. The number is what gets saved. */
object ClockStyle {
    const val CLASSIC = 0
    const val STACKED = 1
    const val ANALOG = 2
    const val PILL = 3
    const val FLIP = 4
    const val COUNT = 5
}

private val W = Color.White
private val Lilac = Color(0xFFD9C8FF)
private val shade = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 18f)

/** Everything under the greeting (time + date) for the chosen style. Called from HomeHeader. */
@Composable
internal fun ClockBody(style: Int, now: Long, time: String, date: String) {
    when (style) {
        ClockStyle.STACKED -> StackedClock(time, date)
        ClockStyle.ANALOG -> AnalogClock(now, time, date)
        ClockStyle.PILL -> PillClock(time, date)
        ClockStyle.FLIP -> FlipClock(time, date)
        else -> {
            BasicText(time, style = txt(W, 64.sp, FontWeight.Bold).copy(shadow = shade, letterSpacing = (-1).sp))
            BasicText(date, style = txt(W.copy(alpha = 0.9f), 18.sp, FontWeight.Medium).copy(shadow = shade))
        }
    }
}

@Composable
private fun StackedClock(time: String, date: String) {
    val hh = time.substringBefore(':')
    val mm = time.substringAfter(':', "")
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            BasicText(hh, style = txt(W, 46.sp, FontWeight.Bold).copy(shadow = shade, lineHeight = 46.sp))
            BasicText(mm, style = txt(Lilac, 46.sp, FontWeight.Bold).copy(shadow = shade, lineHeight = 46.sp))
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(2.dp).height(72.dp).background(W.copy(alpha = 0.4f)))
        Spacer(Modifier.width(16.dp))
        BasicText(date, style = txt(W.copy(alpha = 0.9f), 18.sp, FontWeight.Medium).copy(shadow = shade))
    }
}

@Composable
private fun AnalogClock(now: Long, time: String, date: String) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hours = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val minutes = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AnalogFace(hours, minutes, Modifier.size(88.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            BasicText(time, style = txt(W, 30.sp, FontWeight.Bold).copy(shadow = shade))
            BasicText(date, style = txt(W.copy(alpha = 0.9f), 18.sp, FontWeight.Medium).copy(shadow = shade))
        }
    }
}

/** Round dial. [hours] is 0..12 (with the fraction), [minutes] is 0..60 (with the fraction). */
@Composable
internal fun AnalogFace(hours: Float, minutes: Float, modifier: Modifier, color: Color = W) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.14f), r)
        drawCircle(color.copy(alpha = 0.55f), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val outer = r * 0.86f
            val inner = outer - if (i % 3 == 0) r * 0.14f else r * 0.07f
            drawLine(
                color.copy(alpha = 0.8f),
                Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                1.5.dp.toPx(), StrokeCap.Round,
            )
        }
        fun hand(deg: Float, len: Float, width: Float, col: Color) {
            val a = Math.toRadians(deg - 90.0)
            drawLine(col, c, Offset(c.x + len * cos(a).toFloat(), c.y + len * sin(a).toFloat()), width, StrokeCap.Round)
        }
        hand(hours * 30f, r * 0.5f, 3.5.dp.toPx(), color)
        hand(minutes * 6f, r * 0.75f, 2.5.dp.toPx(), Lilac)
        drawCircle(color, 3.dp.toPx())
    }
}

@Composable
private fun PillClock(time: String, date: String) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .background(W.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(time, style = txt(W, 34.sp, FontWeight.Bold).copy(shadow = shade))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(1.dp).height(26.dp).background(W.copy(alpha = 0.45f)))
        Spacer(Modifier.width(14.dp))
        BasicText(date, style = txt(W.copy(alpha = 0.92f), 17.sp, FontWeight.Medium).copy(shadow = shade))
    }
}

@Composable
private fun FlipClock(time: String, date: String) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        time.forEach { ch ->
            if (ch == ':') {
                BasicText(":", style = txt(W, 40.sp, FontWeight.Bold).copy(shadow = shade))
            } else {
                BasicText(
                    ch.toString(),
                    Modifier
                        .background(W.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .drawBehind {
                            // The thin "flip" line across the middle of the tile.
                            drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                        }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    style = txt(W, 40.sp, FontWeight.Bold),
                )
            }
        }
    }
    BasicText(date, Modifier.padding(top = 4.dp), style = txt(W.copy(alpha = 0.9f), 17.sp, FontWeight.Medium).copy(shadow = shade))
}

/** Small static preview (always 12:51) for the tiles of the Clock page in Control Center. */
@Composable
internal fun ClockMini(style: Int, color: Color) {
    when (style) {
        ClockStyle.STACKED -> Column {
            BasicText("12", style = txt(color, 20.sp, FontWeight.Bold).copy(lineHeight = 20.sp))
            BasicText("51", style = txt(color.copy(alpha = 0.65f), 20.sp, FontWeight.Bold).copy(lineHeight = 20.sp))
        }
        ClockStyle.ANALOG -> AnalogFace(0.85f, 51f, Modifier.size(46.dp), color)
        ClockStyle.PILL -> BasicText(
            "12:51",
            Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
            style = txt(color, 16.sp, FontWeight.Bold),
        )
        ClockStyle.FLIP -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            "12:51".forEach { ch ->
                if (ch == ':') BasicText(":", style = txt(color, 15.sp, FontWeight.Bold))
                else BasicText(
                    ch.toString(),
                    Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
                    style = txt(color, 15.sp, FontWeight.Bold),
                )
            }
        }
        else -> BasicText("12:51", style = txt(color, 26.sp, FontWeight.Bold))
    }
}
