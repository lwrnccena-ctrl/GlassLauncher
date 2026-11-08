package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fares.glass.sys.Screenshots
import kotlin.math.abs

// Card tilt in degrees. If the cards lean the wrong way on your TV, swap the signs.
private const val CARDS3D_TILT_RIGHT = 16f
private const val CARDS3D_TILT_LEFT = -12f

private val ZeroState3D: State<Float> = mutableStateOf(0f)

/** Corner radius of a card that shows a real screenshot. */
private val ShotRadius3D = 28.dp

private class Ghost3D<T>(val key: Any, val item: T, val pos: Float)

/**
 * Same inputs as RecentCards, new look: the selected card is big and sits left of center,
 * the next card peeks in from the right, and the cards already passed pile up behind on the left,
 * smaller, tilted and fading. Closing an app with Up still flies its card away.
 *
 * Opening an app (see RecentsMotion.openThen): the chosen card grows to fill the screen and the
 * others fade. When Glass comes back, the card shrinks back into the stack.
 */
@Composable
fun <T : Any> RecentCards3D(
    items: List<T>,
    selected: Int,
    alpha: Float,
    itemKey: (T) -> Any,
    label: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    val fade = alpha

    var ghosts by remember { mutableStateOf(listOf<Ghost3D<T>>()) }
    val memory = remember { object { var items: List<T> = items; var selected: Int = selected } }
    LaunchedEffect(items) {
        val newKeys = items.map(itemKey).toSet()
        val gone = memory.items.withIndex()
            .filter { itemKey(it.value) !in newKeys }
            .map { Ghost3D(itemKey(it.value), it.value, (it.index - memory.selected).toFloat()) }
        memory.items = items
        if (gone.isNotEmpty()) ghosts = ghosts + gone
    }
    LaunchedEffect(selected) { memory.selected = selected }

    // 0 = normal stack, 1 = the chosen card fills the screen.
    val openP = animateFloatAsState(
        targetValue = if (RecentsMotion.opening != null) 1f else 0f,
        animationSpec = RecentsMotion.spec(380),
        label = "open",
    )
    DisposableEffect(Unit) { onDispose { RecentsMotion.opening = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wDp = maxWidth
        val hDp = maxHeight
        val cardW = wDp * 0.62f
        val cardH = cardW * (Metrics.TileH.value / Metrics.TileW.value)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = fade }
                .background(Color.Black.copy(alpha = 0.62f))
        )

        items.forEachIndexed { i, item ->
            if (i >= selected - 4 && i <= selected + 3) key(itemKey(item)) {
                val pos = animateFloatAsState(
                    (i - selected).toFloat(), GlassSpring.cardSpec(), label = "pos",
                )
                RecentCard3D(
                    pos, ZeroState3D, openP, RecentsMotion.opening == itemKey(item),
                    fade, 100f - abs(i - selected), wDp, hDp, cardW, cardH,
                    label(item), itemKey(item), item, content,
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
                RecentCard3D(
                    pos, leave.asState(), ZeroState3D, false,
                    fade, 100f - abs(g.pos), wDp, hDp, cardW, cardH,
                    label(g.item), g.key, g.item, content,
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
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .graphicsLayer { this.alpha = fade * (1f - openP.value) },
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp),
        )
    }
}

@Composable
private fun <T> RecentCard3D(
    pos: State<Float>,
    leave: State<Float>,
    openP: State<Float>,
    opened: Boolean,
    fade: Float,
    z: Float,
    wDp: Dp,
    hDp: Dp,
    cardW: Dp,
    cardH: Dp,
    title: String,
    shotKey: Any,
    item: T,
    content: @Composable (T) -> Unit,
) {
    val chipH = 36.dp
    val chipScale = chipH.value / Metrics.TileH.value
    val chipW = Metrics.TileW * chipScale
    val faceScale = cardW.value / Metrics.TileW.value
    val shot = (shotKey as? String)?.let { Screenshots.get(it) }

    Box(Modifier.fillMaxSize().zIndex(z), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer {
                val p = pos.value
                val a = abs(p)
                val wPx = wDp.toPx()
                val hPx = hDp.toPx()
                val cwPx = cardW.toPx()
                val chPx = cardH.toPx()
                // t: how far this card has grown to full screen. o: how far the other cards have faded.
                val t = if (opened) openP.value else 0f
                val o = if (opened) 0f else openP.value

                val tx: Float
                val s: Float
                val rot: Float
                val al: Float
                if (p >= 0f) {
                    // Cards waiting on the right: a little smaller, tilted, fully visible.
                    val q = p.coerceAtMost(1f)
                    tx = p * cwPx * 1.05f
                    s = 1f - 0.10f * q
                    rot = CARDS3D_TILT_RIGHT * q
                    al = 1f
                } else {
                    // Cards already passed: pile up behind on the left, shrinking and fading.
                    tx = p * wPx * 0.045f
                    s = (1f - 0.07f * a).coerceAtLeast(0.55f)
                    rot = CARDS3D_TILT_LEFT * a.coerceAtMost(1f)
                    al = (1f - 0.28f * a).coerceIn(0f, 1f)
                }

                // Scale that makes the card cover the whole screen.
                val sFull = maxOf(wPx / cwPx, hPx / chPx)
                val sNow = s + (sFull - s) * t

                // The label row above the card is 46 dp tall, so the card's center sits 23 dp
                // (times the scale) below the column's center.
                translationX = ((wPx * 0.09f + cwPx / 2f - wPx / 2f) + tx) * (1f - t)
                translationY = (1f - fade) * 40.dp.toPx() - leave.value * 300.dp.toPx() -
                    24.dp.toPx() * (1f - t) - 23.dp.toPx() * sFull * t
                scaleX = sNow
                scaleY = sNow
                rotationY = rot * (1f - t)
                cameraDistance = 14f * density
                alpha = al * fade * (1f - leave.value) * (1f - o)
            },
        ) {
            Row(
                Modifier
                    .width(cardW)
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 10.dp)
                    .graphicsLayer { alpha = 1f - (if (opened) openP.value else 0f) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(chipW, chipH)) { ScaledTile3D(item, chipScale, content) }
                Spacer(Modifier.width(12.dp))
                BasicText(
                    title,
                    Modifier.graphicsLayer { this.alpha = 1f - abs(pos.value).coerceAtMost(1f) },
                    style = TextStyle(color = Color.White, fontSize = 20.sp),
                    maxLines = 1,
                )
            }
            if (shot != null) {
                Image(
                    shot, null,
                    Modifier
                        .size(cardW, cardH)
                        .graphicsLayer {
                            // The rounded corners straighten out as the card grows to full screen.
                            val t = if (opened) openP.value else 0f
                            clip = true
                            shape = RoundedCornerShape(ShotRadius3D.toPx() * (1f - t))
                        },
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                Box(Modifier.size(cardW, cardH)) { ScaledTile3D(item, faceScale, content) }
            }
        }
    }
}

/** Draws [content] at the normal tile size, then scales it from the top-left corner. */
@Composable
private fun <T> ScaledTile3D(item: T, scale: Float, content: @Composable (T) -> Unit) {
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
