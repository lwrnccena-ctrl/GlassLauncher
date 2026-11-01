package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.AppEntry
import com.fares.glass.gfx.AppVisual
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Corners of the app icons (Dock & icons > Icon shape): round, rounded or square. */
val TileShape: RoundedCornerShape
    get() = when (DockSettings.corner) {
        0 -> RoundedCornerShape(percent = 50)
        2 -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(14.dp)
    }

private val labelStyle get() = TextStyle(
    fontFamily = Typo.family,
    color = Color.White,
    fontSize = 15.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 10f),
)

/**
 * tvOS-style app icon: when focused it springs larger, lifts (shadow), tilts in 3D toward the move
 * direction and wobbles back, while a sheen slides across its surface.
 * How it moves follows Dock & icons > Motion style (off / smooth / bouncy) and Focus zoom.
 */
@Composable
fun AppTile(
    entry: AppEntry,
    visual: AppVisual?,
    focused: Boolean,
    pressed: Boolean,
    jiggling: Boolean,
    /** Name under the icon when focused; not in the dock (first row) unless [labelAlways]. */
    showLabel: Boolean,
    nudge: Nudge,
    modifier: Modifier,
    /** The name stays visible under the icon even when it is not selected. */
    labelAlways: Boolean = false,
    /** Shows a small lock on the icon (the app asks for the PIN). */
    locked: Boolean = false,
) {
    val anim = DockSettings.anim
    val scaleSpec: AnimationSpec<Float> = when (anim) {
        0 -> snap()
        1 -> spring(dampingRatio = 1f, stiffness = if (pressed) 1400f else 380f)
        else -> spring(dampingRatio = 0.62f, stiffness = if (pressed) 1400f else 380f)
    }
    val scale by animateFloatAsState(
        when {
            pressed -> 1.04f
            focused -> Metrics.FOCUS_SCALE
            else -> 1f
        },
        scaleSpec, label = "scale",
    )
    val liftSpec: AnimationSpec<Float> = if (anim == 0) snap() else tween(220)
    val lift by animateFloatAsState(if (focused) 1f else 0f, liftSpec, label = "lift")
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    LaunchedEffect(focused, nudge.tick, anim) {
        if (!focused || anim == 0) return@LaunchedEffect
        val damping = if (anim == 2) 0.28f else 0.75f
        launch {
            tiltY.snapTo(nudge.dx * 8f)
            tiltY.animateTo(0f, spring(dampingRatio = damping, stiffness = 130f))
        }
        launch {
            tiltX.snapTo(-nudge.dy * 8f)
            tiltX.animateTo(0f, spring(dampingRatio = damping, stiffness = 130f))
        }
    }
    val wiggle = if (jiggling) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            -1.6f, 1.6f, infiniteRepeatable(tween(130), RepeatMode.Reverse), label = "wiggle",
        ).value
    } else 0f

    Box(modifier.size(Metrics.TileW, Metrics.TileH)) {
        Box(
            Modifier
                .size(Metrics.TileW, Metrics.TileH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    rotationZ = wiggle
                    cameraDistance = 14f * density
                    shadowElevation = lift * 24.dp.toPx()
                    shape = TileShape
                    clip = true
                }
                .drawWithCache {
                    val rim = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.55f), 0.5f to Color.White.copy(alpha = 0.08f),
                        1f to Color.White.copy(alpha = 0.22f),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                    val topGloss = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.20f), 0.42f to Color.Transparent,
                    )
                    val corner = TileShape.topStart.toPx(size, this)
                    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    onDrawBehind {
                        if (visual != null) {
                            drawImage(visual.tile, dstSize = dst, filterQuality = FilterQuality.Medium)
                        } else {
                            drawRect(Color.White.copy(alpha = 0.12f))
                        }
                        if (lift > 0f) {
                            // The sheen follows the tilt, like light on a tvOS icon.
                            val shift = tiltY.value / 16f
                            val sheen = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.22f * lift),
                                1f to Color.Transparent,
                                start = Offset(size.width * (-0.25f + shift), 0f),
                                end = Offset(size.width * (0.55f + shift), size.height),
                            )
                            drawRect(sheen)
                            drawRect(topGloss, alpha = lift)
                        }
                        drawRoundRect(
                            rim, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                            style = Stroke(1.dp.toPx()), alpha = 0.35f + 0.65f * lift,
                        )
                    }
                },
        ) {
            if (visual == null) {
                BasicText(entry.label, Modifier.align(Alignment.Center), style = labelStyle, maxLines = 1)
            }
        }
        if (locked) {
            // A small lock in the corner: this app asks for the PIN before it opens.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    rememberVectorPainter(Icons.Rounded.Lock), null, Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
        if (showLabel) BasicText(
            entry.label,
            Modifier
                .align(Alignment.TopCenter)
                .requiredWidth(Metrics.TileW + 90.dp)
                .offset(y = Metrics.TileH + 10.dp + Metrics.TileH * (Metrics.FOCUS_SCALE - 1f) / 2)
                .graphicsLayer { alpha = if (labelAlways) 1f else lift },
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
