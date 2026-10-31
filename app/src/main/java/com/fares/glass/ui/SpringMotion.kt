package com.fares.glass.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Spring settings shared by the media card, the Control Center panel and its tiles. */
object GlassSpring {
    // Card + panel: soft, clear overshoot, then settles.
    val card = spring<Float>(dampingRatio = 0.55f, stiffness = 170f)

    // Tiles: slightly tighter so the row-by-row ripple stays crisp.
    val tile = spring<Float>(dampingRatio = 0.58f, stiffness = 210f)
}

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 0f = home screen, 1f = Control Center open.
 * It can go slightly above 1f (the bounce). If the user presses the key again
 * mid-animation, the spring keeps its speed and turns around smoothly.
 */
@Composable
fun rememberControlCenterProgress(open: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = GlassSpring.card,
        label = "controlCenter"
    )

/**
 * Per-row progress for the tile ripple. row = 1..5 (top to bottom).
 * Opening: top rows first. Closing: bottom rows first.
 */
@Composable
fun rememberTileProgress(open: Boolean, row: Int): State<Float> {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(open) {
        delay(if (open) 50L + row * 40L else (6 - row) * 20L)
        target = if (open) 1f else 0f
    }
    return animateFloatAsState(
        targetValue = target,
        animationSpec = GlassSpring.tile,
        label = "tileRow$row"
    )
}
