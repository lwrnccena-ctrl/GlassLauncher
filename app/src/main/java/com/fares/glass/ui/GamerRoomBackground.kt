package com.fares.glass.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The gamer room picture, drawn a little bigger than the screen. When the focus moves along the
 * dock the picture glides sideways (focusX goes from -1 on the first app to +1 on the last one).
 */
@Composable
fun GamerRoomBackground(image: ImageBitmap?, focusX: Float, modifier: Modifier = Modifier) {
    val shift by animateFloatAsState(
        (focusX + 1f) / 2f,
        spring(stiffness = Spring.StiffnessLow),
        label = "roomShift",
    )
    if (image == null) return
    Canvas(modifier) {
        val scale = 1.08f // 8% extra, so the edges never show
        val dw = size.width * scale
        val dh = size.height * scale
        drawImage(
            image,
            dstOffset = IntOffset((-(dw - size.width) * shift).roundToInt(), (-(dh - size.height) / 2f).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}
