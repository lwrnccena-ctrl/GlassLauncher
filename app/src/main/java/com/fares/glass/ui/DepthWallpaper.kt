package com.fares.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.gfx.DepthSettings
import kotlinx.coroutines.delay
import android.os.Environment
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** Your photo, filling the screen. Plain dark until a photo is found in Glass/depth/. */
@Composable
fun DepthBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
    } else {
        Box(modifier.background(Color(0xFF101418)))
    }
}

// ---- Rounded clock digits ----
// The digits are drawn with thick, round-ended strokes (no font file needed). Every digit lives in a
// 60 x 100 box; the stroke is centered on the lines below.

private const val GLYPH_W = 60f
private const val GLYPH_H = 100f
private const val COLON_W = 22f
private const val GLYPH_GAP = 12f
private const val GLYPH_STROKE = 14f

/** A 6 (or, flipped half a turn, a 9). */
private fun sixPath(flip: Boolean): Path {
    fun x(v: Float) = if (flip) GLYPH_W - v else v
    fun y(v: Float) = if (flip) GLYPH_H - v else v
    return Path().apply {
        moveTo(x(47f), y(16f))
        cubicTo(x(42f), y(10f), x(36f), y(8f), x(30f), y(8f))
        cubicTo(x(14f), y(8f), x(8f), y(28f), x(8f), y(58f))
        addOval(if (flip) Rect(8f, 8f, 52f, 56f) else Rect(8f, 44f, 52f, 92f))
    }
}

private val glyphs: Map<Char, Path> by lazy {
    mapOf(
        '0' to Path().apply { addOval(Rect(8f, 8f, 52f, 92f)) },
        '1' to Path().apply {
            moveTo(34f, 8f); lineTo(34f, 92f)
            moveTo(34f, 8f); lineTo(16f, 22f)
        },
        '2' to Path().apply {
            moveTo(10f, 28f)
            cubicTo(10f, 16f, 19f, 8f, 30f, 8f)
            cubicTo(42f, 8f, 51f, 16f, 51f, 29f)
            cubicTo(51f, 44f, 34f, 58f, 10f, 92f)
            lineTo(52f, 92f)
        },
        '3' to Path().apply {
            moveTo(10f, 24f)
            cubicTo(12f, 13f, 20f, 8f, 30f, 8f)
            cubicTo(42f, 8f, 49f, 16f, 49f, 28f)
            cubicTo(49f, 40f, 40f, 47f, 26f, 47f)
            moveTo(26f, 47f)
            cubicTo(42f, 47f, 52f, 55f, 52f, 69f)
            cubicTo(52f, 84f, 42f, 92f, 30f, 92f)
            cubicTo(18f, 92f, 11f, 86f, 9f, 75f)
        },
        '4' to Path().apply {
            moveTo(40f, 92f); lineTo(40f, 8f); lineTo(6f, 66f); lineTo(54f, 66f)
        },
        '5' to Path().apply {
            moveTo(48f, 8f); lineTo(17f, 8f); lineTo(13f, 47f)
            cubicTo(19f, 42f, 26f, 40f, 32f, 40f)
            cubicTo(44f, 40f, 52f, 50f, 52f, 66f)
            cubicTo(52f, 82f, 42f, 92f, 29f, 92f)
            cubicTo(18f, 92f, 11f, 86f, 9f, 76f)
        },
        '6' to sixPath(false),
        '7' to Path().apply {
            moveTo(8f, 8f); lineTo(52f, 8f); lineTo(22f, 92f)
        },
        '8' to Path().apply {
            addOval(Rect(11f, 8f, 49f, 50f))
            addOval(Rect(9f, 46f, 51f, 92f))
        },
        '9' to sixPath(true),
    )
}

/** Draws [text] (digits and ':') with the rounded strokes, [height] tall. */
@Composable
private fun RoundedClockText(text: String, height: Dp, color: Color, modifier: Modifier = Modifier) {
    val widthUnits = text.fold(0f) { sum, c -> sum + if (c == ':') COLON_W else GLYPH_W } +
        GLYPH_GAP * (text.length - 1).coerceAtLeast(0)
    Canvas(modifier.size(height * (widthUnits / GLYPH_H), height)) {
        val s = size.height / GLYPH_H
        var x = 0f
        text.forEach { c ->
            if (c == ':') {
                val r = GLYPH_STROKE / 2f * 1.1f * s
                drawCircle(color, r, Offset((x + COLON_W / 2f) * s, 36f * s))
                drawCircle(color, r, Offset((x + COLON_W / 2f) * s, 68f * s))
                x += COLON_W + GLYPH_GAP
            } else {
                glyphs[c]?.let { path ->
                    withTransform({
                        translate(x * s, 0f)
                        scale(s, s, Offset.Zero)
                    }) {
                        drawPath(path, color, style = Stroke(GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                x += GLYPH_W + GLYPH_GAP
            }
        }
    }
}

/**
 * The font of the Classic clock. It is read from storage, so no font has to be part of the app:
 * put a bold font file named clock.ttf (clock.otf, or sf-pro-text.ttf also work) in Glass/depth/ on the TV.
 * Next to it res/font/depth_clock.ttf is used if the app has one. Without a font the rounded drawn digits are used.
 * It is read again when Simple mode is switched on.
 */
@Composable
private fun clockFontOrNull(): FontFamily? {
    val context = LocalContext.current
    return remember(DepthSettings.reload) {
        val names = listOf("clock.ttf", "clock.otf", "sf-pro-text.ttf")
        val dirs = listOfNotNull(
            File(Environment.getExternalStorageDirectory(), "Glass/depth"),
            context.getExternalFilesDir(null)?.let { File(it, "depth") },
        )
        val file = dirs.flatMap { d -> names.map { File(d, it) } }.firstOrNull { it.isFile && it.canRead() }
        val fromStorage = file?.let {
            try {
                FontFamily(Typeface(android.graphics.Typeface.createFromFile(it)))
            } catch (e: Exception) {
                null
            }
        }
        fromStorage ?: run {
            val id = context.resources.getIdentifier("depth_clock", "font", context.packageName)
            if (id != 0) FontFamily(Font(id, FontWeight.Bold)) else null
        }
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
 * [style] is the style chosen in Control Center > Clock: Classic gives the big rounded digits (with the
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
    val font = clockFontOrNull()
    val density = LocalDensity.current
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 36.dp).fillMaxWidth(0.74f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (classic) {
                BasicText(
                    date,
                    style = TextStyle(
                        color = ink.copy(alpha = 0.85f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = font ?: FontFamily.SansSerif, textAlign = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.height(6.dp))
            }
            val clock: @Composable () -> Unit = {
                if (classic) {
                    val digits = if (sun != null) 120.dp else 150.dp
                    if (font != null) {
                        BasicText(
                            time,
                            style = TextStyle(
                                color = ink, fontSize = with(density) { (digits * 1.4f).toSp() },
                                fontWeight = FontWeight.Bold, fontFamily = font, letterSpacing = (-3).sp,
                                fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
                            ),
                        )
                    } else {
                        RoundedClockText(time, digits, ink)
                    }
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
