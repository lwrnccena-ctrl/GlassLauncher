package com.fares.glass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** What sits behind the glass: the pre-blurred wallpaper and the screen size. */
class Backdrop {
    var frosted by mutableStateOf<ImageBitmap?>(null)
    var screen by mutableStateOf(IntSize.Zero)
}

/**
 * "Liquid Glass" material. The background is static, so instead of blurring in real time (impossible
 * before Android 12) it draws the exact part of the pre-blurred background under the panel, then a
 * tint, a specular highlight at the top, an inner glow and a bright rim.
 */
@Composable
fun Glass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    tint: Color = Color.White.copy(alpha = 0.07f),
    /** Blurred image to sample when it isn't the wallpaper (Top Shelf ambience). */
    source: ImageBitmap? = null,
    /**
     * Where the top-left corner of this glass finally sits on the screen. Pass it when the glass is
     * scaled or moved by a graphicsLayer animation (the Control Center opening), so the frosted
     * background stays lined up. Null = measure the position from the layout, as before.
     */
    fixedOrigin: Offset? = null,
    /** False = draw nothing behind the content (no panel, no rim). The content is still shown. */
    showPanel: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val fixed = rememberUpdatedState(fixedOrigin)
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithCache {
                val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.65f),
                    0.35f to Color.White.copy(alpha = 0.10f),
                    0.7f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.35f),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val specular = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.4f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                )
                val innerGlow = Brush.radialGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.06f)),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = maxOf(size.width, size.height) * 0.75f,
                )
                val rimWidth = 1.2.dp.toPx()
                onDrawBehind {
                    if (!showPanel) return@onDrawBehind
                    val o = fixed.value ?: origin
                    clipPath(path) {
                        (source ?: backdrop.frosted)?.let { img ->
                            drawImage(
                                img,
                                dstOffset = IntOffset(-o.x.roundToInt(), -o.y.roundToInt()),
                                dstSize = backdrop.screen,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                        drawRect(tint)
                        drawRect(innerGlow)
                        drawRect(specular)
                    }
                    drawPath(path, rim, style = Stroke(rimWidth))
                }
            },
        content = content,
    )
}
