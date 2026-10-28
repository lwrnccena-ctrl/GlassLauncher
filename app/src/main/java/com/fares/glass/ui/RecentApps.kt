package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Recent apps as a stack of cards, like the app switcher on Apple devices: the selected card is in
 * front and big, the others sit behind it, smaller and turned a little, and fade out with distance.
 * card: draws one card (the home screen passes the app's tile).
 */
@Composable
fun <T> RecentStack(
    items: List<T>,
    selected: Int,
    alpha: Float,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    card: @Composable (T) -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        if (items.isEmpty()) {
            BasicText("No recent apps", style = txt(Color.White.copy(alpha = 0.8f), 18.sp, FontWeight.SemiBold))
        } else {
            // The cards farthest from the selected one are drawn first, so the selected card ends up on top.
            val order = items.indices.sortedByDescending { abs(it - selected) }
            for (i in order) {
                key(itemKey(items[i])) {
                    StackCard(i - selected) { card(items[i]) }
                }
            }
        }
        BasicText(
            "Left / Right  Browse        OK  Open        Up  Close app        Down  Back",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
            style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
        )
    }
}

/** One card. d = how far it is from the selected card (0 = selected, negative = to the left). */
@Composable
private fun StackCard(d: Int, content: @Composable () -> Unit) {
    val a = abs(d)
    val sign = if (d < 0) -1f else if (d > 0) 1f else 0f
    val spec = spring<Float>(dampingRatio = 0.78f, stiffness = 260f)

    val x by animateFloatAsState(sign * (if (a == 0) 0f else 190f + (a - 1) * 62f), spec, label = "stackX")
    val size by animateFloatAsState(
        when (a) { 0 -> 1f; 1 -> 0.80f; 2 -> 0.66f; 3 -> 0.55f; else -> 0.5f }, spec, label = "stackSize",
    )
    val turn by animateFloatAsState(-sign * 22f, spec, label = "stackTurn")
    val fade by animateFloatAsState(
        when (a) { 0 -> 1f; 1 -> 0.92f; 2 -> 0.7f; 3 -> 0.4f; else -> 0f }, tween(250), label = "stackFade",
    )

    val density = LocalDensity.current.density
    val big = 330f / Metrics.TileW.value // the selected card is about 330 dp wide
    Box(
        Modifier
            .size(Metrics.TileW, Metrics.TileH + 30.dp)
            .graphicsLayer {
                translationX = x * density
                scaleX = big * size
                scaleY = big * size
                rotationY = turn
                cameraDistance = 12f * density
                alpha = fade
            },
    ) { content() }
}
