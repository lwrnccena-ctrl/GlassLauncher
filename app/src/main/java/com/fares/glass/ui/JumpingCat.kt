package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** Size of the cat in dp (the screen is laid out as 960 x 540 dp). */
const val CAT_W = 44f
const val CAT_H = 40f

/** How far the cat's feet sink into the top of the icon it sits on, in dp. */
const val CAT_LIFT = 8f

private const val JUMP_MS = 420

private val CatOrange = Color(0xFFF4A259)
private val CatDark = Color(0xFFD9822B)
private val CatCream = Color(0xFFFFF1DC)
private val CatPink = Color(0xFFF28B9B)
private val CatInk = Color(0xFF2B2B2B)

/**
 * A cat that sits on top of the selected icon and jumps to the next one when the selection moves.
 * x, y: top-left corner of the cat in dp, where it should sit now. jumpKey: change it to make the cat jump.
 */
@Composable
fun JumpingCat(x: Float, y: Float, jumpKey: Any, visible: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val progress = remember { Animatable(1f) }   // 0 = takes off, 1 = has landed
    val landing = remember { Animatable(0f) }    // 1 = squashed on landing, springs back to 0
    val pos = remember { floatArrayOf(x, y) }    // where the cat is drawn right now
    val from = remember { floatArrayOf(x, y) }   // where the current jump started
    val appear by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "catAppear")

    // A new target: jump from wherever the cat is (even in mid-air), then squash a little when landing.
    LaunchedEffect(jumpKey) {
        from[0] = pos[0]
        from[1] = pos[1]
        progress.snapTo(0f)
        progress.animateTo(1f, tween(JUMP_MS, easing = FastOutSlowInEasing))
        landing.snapTo(1f)
        landing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 520f))
    }

    // The tail swings gently while the cat sits.
    val wagTransition = rememberInfiniteTransition(label = "catTail")
    val wag by wagTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "catWag",
    )

    Canvas(
        modifier
            .size(CAT_W.dp, CAT_H.dp)
            .graphicsLayer {
                val p = progress.value
                val dx = x - from[0]
                val dy = y - from[1]
                val distance = hypot(dx, dy)
                // The farther the jump, the higher the arc (a parabola).
                val arc = if (distance < 1f) 0f else (distance * 0.28f).coerceIn(14f, 46f)
                val px = from[0] + dx * p
                val py = from[1] + dy * p - arc * 4f * p * (1f - p)
                pos[0] = px
                pos[1] = py
                translationX = px * density
                translationY = py * density

                val air = sin(PI * p).toFloat() // 0 on the ground, 1 at the top of the jump
                val squash = landing.value
                scaleX = 1f - 0.10f * air + 0.14f * squash   // stretched in the air, squashed on landing
                scaleY = 1f + 0.16f * air - 0.16f * squash
                rotationZ = (if (dx >= 0f) 1f else -1f) * 12f * air // leans into the jump
                transformOrigin = TransformOrigin(0.5f, 1f)         // scales from the feet
                alpha = appear
            },
    ) {
        drawCat(air = sin(PI * progress.value).toFloat(), wag = wag)
    }
}

private fun DrawScope.drawCat(air: Float, wag: Float) {
    val w = size.width
    val h = size.height

    // Tail
    val tail = Path().apply {
        moveTo(w * 0.70f, h * 0.92f)
        cubicTo(w * 1.00f, h * 0.98f, w * 1.04f, h * 0.60f, w * (0.90f + 0.05f * wag), h * 0.52f)
    }
    drawPath(tail, CatOrange, style = Stroke(width = w * 0.10f, cap = StrokeCap.Round))

    // Body, chest and front paws
    drawOval(CatOrange, Offset(w * 0.22f, h * 0.52f), Size(w * 0.56f, h * 0.46f))
    drawOval(CatCream, Offset(w * 0.38f, h * 0.64f), Size(w * 0.24f, h * 0.30f))
    drawOval(CatCream, Offset(w * 0.28f, h * 0.88f), Size(w * 0.17f, h * 0.11f))
    drawOval(CatCream, Offset(w * 0.55f, h * 0.88f), Size(w * 0.17f, h * 0.11f))

    // Ears (the right one is the left one mirrored)
    fun triangle(color: Color, a: Offset, b: Offset, c: Offset, mirror: Boolean) {
        fun m(o: Offset) = if (mirror) Offset(w - o.x, o.y) else o
        val p = Path().apply {
            moveTo(m(a).x, m(a).y)
            lineTo(m(b).x, m(b).y)
            lineTo(m(c).x, m(c).y)
            close()
        }
        drawPath(p, color)
    }
    for (mirror in listOf(false, true)) {
        triangle(CatOrange, Offset(w * 0.25f, h * 0.30f), Offset(w * 0.44f, h * 0.12f), Offset(w * 0.27f, h * 0.01f), mirror)
        triangle(CatPink, Offset(w * 0.29f, h * 0.24f), Offset(w * 0.39f, h * 0.15f), Offset(w * 0.29f, h * 0.08f), mirror)
    }

    // Head
    drawCircle(CatOrange, w * 0.265f, Offset(w * 0.5f, h * 0.39f))
    // Forehead stripes
    val stripe = w * 0.025f
    drawLine(CatDark, Offset(w * 0.50f, h * 0.15f), Offset(w * 0.50f, h * 0.24f), stripe, StrokeCap.Round)
    drawLine(CatDark, Offset(w * 0.43f, h * 0.17f), Offset(w * 0.44f, h * 0.24f), stripe, StrokeCap.Round)
    drawLine(CatDark, Offset(w * 0.57f, h * 0.17f), Offset(w * 0.56f, h * 0.24f), stripe, StrokeCap.Round)

    // Eyes: they open wide while the cat is in the air
    val eye = w * (0.034f + 0.018f * air)
    for (cx in listOf(0.40f, 0.60f)) {
        drawCircle(CatInk, eye, Offset(w * cx, h * 0.38f))
        drawCircle(Color.White, eye * 0.35f, Offset(w * cx + eye * 0.3f, h * 0.38f - eye * 0.3f))
    }

    // Nose and whiskers
    drawCircle(CatPink, w * 0.024f, Offset(w * 0.5f, h * 0.46f))
    val whisker = 1.2.dp.toPx()
    val whiskerColor = Color.White.copy(alpha = 0.8f)
    for (side in listOf(-1f, 1f)) {
        val x0 = w * (0.5f + side * 0.15f)
        val x1 = w * (0.5f + side * 0.37f)
        drawLine(whiskerColor, Offset(x0, h * 0.47f), Offset(x1, h * 0.43f), whisker, StrokeCap.Round)
        drawLine(whiskerColor, Offset(x0, h * 0.50f), Offset(x1, h * 0.51f), whisker, StrokeCap.Round)
    }
}
