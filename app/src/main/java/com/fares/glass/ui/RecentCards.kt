package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

private val ZeroState: State<Float> = mutableStateOf(0f)

private class Ghost<T>(val key: Any, val item: T, val pos: Float)

/**
 * Recent apps as a cascade of cards: the selected one in front, the others peeking out behind it.
 */
@Composable
fun <T : Any> RecentCards(
    items: List<T>,
    selected: Int,
    alpha: Float,
    itemKey: (T) -> Any,
    label: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    val fade = alpha
    val cardW = 400.dp
    val cardH = cardW * (Metrics.TileH.value / Metrics.TileW.value)
    val peek = 120.dp

    // An app closed with Up: its card flies up and fades while the others slide into the gap.
    var ghosts by remember { mutableStateOf(listOf<Ghost<T>>()) }
    val memory = remember { object { var items: List<T> = items; var selected: Int = selected } }
    LaunchedEffect(items) {
        val newKeys = items.map(itemKey).toSet()
        val gone = memory.items.withIndex()
            .filter { itemKey(it.value) !in newKeys }
            .map { Ghost(itemKey(it.value), it.value, (it.index - memory.selected).toFloat()) }
        memory.items = items
        if (gone.isNotEmpty()) ghosts = ghosts + gone
    }
    LaunchedEffect(selected) { memory.selected = selected }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = fade }.background(Color.Black.copy(alpha = 0.25f)))

        items.forEachIndexed { i, item ->
            if (abs(i - selected) <= 5) key(itemKey(item)) {
                val pos = animateFloatAsState(
                    (i - selected).toFloat(), spring(dampingRatio = 0.82f, stiffness = 380f), label = "pos",
                )
                RecentCard(
                    pos, ZeroState, fade, 10f - abs(i - selected), peek, cardW, cardH,
                    label(item), item, content,
                )
            }
        }

        ghosts.forEach { g ->
            key(g.key) {
                val leave = remember { Animatable(0f) }
                val pos = remember { mutableStateOf(g.pos) }
                LaunchedEffect(Unit) {
                    leave.animateTo(1f, tween(240))
                    ghosts = ghosts.filter { it.key != g.key }
                }
                RecentCard(
                    pos, leave.asState(), fade, 10f - abs(g.pos), peek, cardW, cardH,
                    label(g.item), g.item, content,
                )
            }
        }

        if (items.isEmpty() && ghosts.isEmpty()) {
            BasicText(
                "No recent apps",
                Modifier.align(Alignment.Center).graphicsLayer { this.alpha = fade },
                style = TextStyle(color = Color.White, fontSize = 22.sp),
            )
        }
        BasicText(
            "Left / Right  Browse      OK  Open      Up  Close app      Down  Back",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).graphicsLayer { this.alpha = fade },
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp),
        )
    }
}

@Composable
private fun <T> RecentCard(
    pos: State<Float>,
    leave: State<Float>,
    fade: Float,
    z: Float,
    peek: Dp,
    cardW: Dp,
    cardH: Dp,
    title: String,
    item: T,
    content: @Composable (T) -> Unit,
) {
    val chipH = 36.dp
    val chipScale = chipH.value / Metrics.TileH.value
    val chipW = Metrics.TileW * chipScale
    val faceScale = cardW.value / Metrics.TileW.value

    Box(Modifier.fillMaxSize().zIndex(z), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer {
                val a = abs(pos.value)
                translationX = pos.value * peek.toPx()
                translationY = (1f - fade) * 40.dp.toPx() - leave.value * 300.dp.toPx() - 24.dp.toPx()
                val s = 1f - 0.1f * a.coerceAtMost(1f)
                scaleX = s
                scaleY = s
                this.alpha = (1f - 0.18f * a).coerceIn(0f, 1f) * fade * (1f - leave.value)
            },
        ) {
            Row(
                Modifier.width(cardW).padding(horizontal = 6.dp).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Small version of the app's tile; it glides to the right edge for cards right of the selection.
                Box(
                    Modifier
                        .size(chipW, chipH)
                        .graphicsLayer {
                            translationX = (cardW - 12.dp - chipW).toPx() * pos.value.coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(9.dp)),
                ) { ScaledTile(item, chipScale, content) }
                Spacer(Modifier.width(12.dp))
                BasicText(
                    title,
                    Modifier.graphicsLayer { this.alpha = 1f - abs(pos.value).coerceAtMost(1f) },
                    style = TextStyle(color = Color.White, fontSize = 20.sp),
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(cardW, cardH)
                    .clip(RoundedCornerShape(26.dp))
                    .drawWithContent {
                        drawContent()
                        val ring = 1f - abs(pos.value).coerceAtMost(1f)
                        val inset = 1.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.75f * ring),
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            cornerRadius = CornerRadius(26.dp.toPx()),
                            style = Stroke(2.dp.toPx()),
                        )
                    },
            ) { ScaledTile(item, faceScale, content) }
        }
    }
}

/** Draws [content] at the normal tile size, then scales it from the top-left corner. */
@Composable
private fun <T> ScaledTile(item: T, scale: Float, content: @Composable (T) -> Unit) {
    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(Metrics.TileW, Metrics.TileH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) { content(item) }
}
