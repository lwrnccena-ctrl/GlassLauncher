package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.zIndex
import kotlin.math.abs

// Card tilt in degrees. If the cards lean the wrong way on your TV, swap the signs.
private const val STACK3D_TILT_RIGHT = 16f
private const val STACK3D_TILT_LEFT = -12f

/**
 * Recent apps as a 3D stack of cards.
 * The selected card is big and sits left of center, the next card peeks in from the right,
 * and the cards already passed pile up behind on the left, smaller and fading.
 *
 * card:  draws one card (the home screen passes the app's tile).
 * label: optional, drawn above each card (app icon + name). It only shows on the selected card.
 */
@Composable
fun <T> RecentStack3D(
    items: List<T>,
    selected: Int,
    alpha: Float,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    label: (@Composable (T) -> Unit)? = null,
    card: @Composable (T) -> Unit,
) {
    val pos by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = GlassSpring.cardSpec(),
        label = "stackPos",
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black.copy(alpha = 0.62f)),
    ) {
        if (items.isEmpty()) {
            BasicText(
                "No recent apps",
                Modifier.align(Alignment.Center),
                style = txt(Color.White.copy(alpha = 0.8f), 18.sp, FontWeight.SemiBold),
            )
        } else {
            val d = LocalDensity.current
            val wPx = with(d) { maxWidth.toPx() }
            val hPx = with(d) { maxHeight.toPx() }
            val tileWPx = with(d) { Metrics.TileW.toPx() }
            val tileHPx = with(d) { (Metrics.TileH + 30.dp).toPx() }
            val cardWPx = wPx * 0.62f
            val base = cardWPx / tileWPx

            // Only the cards that can be seen are drawn.
            val lo = (selected - 4).coerceAtLeast(0)
            val hi = (selected + 3).coerceAtMost(items.lastIndex)
            for (i in lo..hi) {
                key(itemKey(items[i])) {
                    StackCard3D(
                        index = i,
                        selected = selected,
                        pos = { pos },
                        wPx = wPx,
                        hPx = hPx,
                        tileWPx = tileWPx,
                        tileHPx = tileHPx,
                        cardWPx = cardWPx,
                        base = base,
                        label = label?.let { l -> { l(items[i]) } },
                        content = { card(items[i]) },
                    )
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

@Composable
private fun BoxScope.StackCard3D(
    index: Int,
    selected: Int,
    pos: () -> Float,
    wPx: Float,
    hPx: Float,
    tileWPx: Float,
    tileHPx: Float,
    cardWPx: Float,
    base: Float,
    label: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .size(Metrics.TileW, Metrics.TileH + 30.dp)
            .zIndex(100f - abs(index - selected))
            .graphicsLayer {
                val p = index - pos()
                val a = abs(p)
                val tx: Float
                val s: Float
                val rot: Float
                val al: Float
                if (p >= 0f) {
                    // Cards waiting on the right: a little smaller, tilted, fully visible.
                    val q = p.coerceAtMost(1f)
                    tx = p * cardWPx * 1.05f
                    s = 1f - 0.10f * q
                    rot = STACK3D_TILT_RIGHT * q
                    al = 1f
                } else {
                    // Cards already passed: pile up behind on the left, shrinking and fading.
                    tx = p * wPx * 0.045f
                    s = (1f - 0.07f * a).coerceAtLeast(0.55f)
                    rot = STACK3D_TILT_LEFT * a.coerceAtMost(1f)
                    al = (1f - 0.28f * a).coerceIn(0f, 1f)
                }
                translationX = wPx * 0.09f + cardWPx / 2f - tileWPx / 2f + tx
                translationY = 6f * density
                scaleX = base * s
                scaleY = base * s
                rotationY = rot
                cameraDistance = 14f * density
                alpha = al
            },
    ) { content() }

    if (label != null) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .zIndex(200f - abs(index - selected))
                .graphicsLayer {
                    val p = index - pos()
                    val a = abs(p)
                    val s = if (p >= 0f) 1f - 0.10f * p.coerceAtMost(1f)
                    else (1f - 0.07f * a).coerceAtLeast(0.55f)
                    val tx = if (p >= 0f) p * cardWPx * 1.05f else p * wPx * 0.045f
                    val scaledW = cardWPx * s
                    val scaledH = tileHPx * base * s
                    val centerX = wPx * 0.09f + cardWPx / 2f + tx
                    translationX = centerX - scaledW / 2f
                    translationY = hPx / 2f + 6f * density - scaledH / 2f - 34f * density
                    alpha = (1f - a).coerceIn(0f, 1f)
                },
        ) { label() }
    }
}
