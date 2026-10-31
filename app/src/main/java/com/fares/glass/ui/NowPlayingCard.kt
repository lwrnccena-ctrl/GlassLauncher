package com.fares.glass.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.sys.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Width of the wide card on the home screen. */
private val WideWidth = 290.dp

/**
 * Size of the tile next to the Control Center: a rounded square, two tile rows tall (64 + 8 + 64),
 * level with Wi-Fi and Miracast.
 */
val MediaTileW = 136.dp
val MediaTileH = 136.dp

/** The tile content is laid out at this size, then drawn scaled down to MediaTileW x MediaTileH. */
private val TileNatural = 168.dp

/** Kept so older code that still mentions it compiles. */
val MediaTileSide = MediaTileH

/** Round button: plain icon at rest, a white circle with a dark icon when selected. */
@Composable
private fun MediaButton(icon: ImageVector, iconSize: Dp, focused: Boolean) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 600f), label = "mediaButton")
    val fg = lerp(Color.White, Color(0xFF111114), f)
    Box(
        Modifier
            .size(iconSize + 18.dp)
            .graphicsLayer { scaleX = 1f + 0.12f * f; scaleY = 1f + 0.12f * f }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.94f * f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(iconSize), colorFilter = ColorFilter.tint(fg))
    }
}

/** What is inside the wide card (cover, title, buttons, progress). */
@Composable
private fun WideBody(focus: Int) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(NowPlaying.active) {
        while (NowPlaying.active) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = NowPlaying.art
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Image(
                        rememberVectorPainter(Icons.Rounded.MusicNote), null, Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    NowPlaying.title.ifBlank { "Now playing" },
                    style = txt(Color.White, 15.sp, FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (NowPlaying.artist.isNotBlank()) {
                    BasicText(
                        NowPlaying.artist,
                        style = txt(Color.White.copy(alpha = 0.65f), 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaButton(Icons.Rounded.SkipPrevious, 26.dp, focus == 0)
            MediaButton(
                if (NowPlaying.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                32.dp, focus == 1,
            )
            MediaButton(Icons.Rounded.SkipNext, 26.dp, focus == 2)
        }

        if (NowPlaying.durationMs > 0) {
            Spacer(Modifier.height(6.dp))
            val pos = NowPlaying.position(now)
            val fraction = (pos.toFloat() / NowPlaying.durationMs).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(fmt(pos), style = txt(Color.White.copy(alpha = 0.65f), 11.sp))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(4.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(4.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                BasicText(fmt(NowPlaying.durationMs), style = txt(Color.White.copy(alpha = 0.65f), 11.sp))
            }
        }
    }
}

/**
 * Glass card with what is playing. It fades in and out by itself.
 * focus: -1 = none, 0 = previous, 1 = play/pause, 2 = next.
 */
@Composable
fun NowPlayingCard(backdrop: Backdrop, focus: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        NowPlaying.active, modifier,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.95f),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f),
    ) {
        Glass(backdrop, Modifier.width(WideWidth), RoundedCornerShape(24.dp), tint = Color.White.copy(alpha = 0.10f)) {
            WideBody(focus)
        }
    }
}

/**
 * The square tile: cover and cast icon on top, title and artist in the middle, the three buttons at the bottom.
 * When nothing is playing it says "Not playing". Its natural size is TileNatural x TileNatural.
 * focus: -1 = none, 0 = previous, 1 = play/pause, 2 = next.
 */
@Composable
private fun TileBody(focus: Int) {
    val playing = NowPlaying.active
    Box(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.Top) {
            if (playing) {
                val art = NowPlaying.art
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Image(
                            rememberVectorPainter(Icons.Rounded.MusicNote), null, Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Image(
                rememberVectorPainter(Icons.Rounded.Cast), null, Modifier.size(26.dp),
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
            )
        }

        if (playing) {
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 46.dp)) {
                BasicText(
                    NowPlaying.title.ifBlank { "Now playing" },
                    style = txt(Color.White, 17.sp, FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (NowPlaying.artist.isNotBlank()) {
                    BasicText(
                        NowPlaying.artist,
                        style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            BasicText(
                "Not playing",
                Modifier.align(Alignment.Center).padding(bottom = 18.dp),
                style = txt(Color.White.copy(alpha = 0.6f), 15.sp, FontWeight.Medium),
            )
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .graphicsLayer { alpha = if (playing) 1f else 0.55f },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaButton(Icons.Rounded.SkipPrevious, 20.dp, focus == 0)
            MediaButton(
                if (NowPlaying.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                26.dp, focus == 1,
            )
            MediaButton(Icons.Rounded.SkipNext, 20.dp, focus == 2)
        }
    }
}

/**
 * Lays out [content] at its natural size ([naturalW] x [naturalH], or any height when null), then draws it
 * scaled from its top-left corner. The slot it is given is only a window: the content is never squeezed.
 */
@Composable
private fun ScaledContent(
    naturalW: Dp,
    naturalH: Dp?,
    scale: Float,
    alpha: Float,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val w = naturalW.roundToPx()
        val childConstraints = if (naturalH != null) {
            Constraints.fixed(w, naturalH.roundToPx())
        } else {
            Constraints(minWidth = w, maxWidth = w)
        }
        val placeable = measurables.firstOrNull()?.measure(childConstraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable?.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        }
    }
}

/**
 * The now playing card flying to the Control Center and turning into the two-row tile (and back).
 *
 * progress: 0 = the wide card where it sits on the home screen, 1 = the tile next to the Control Center.
 *   It is allowed to go a little past 1 (and a little below 0): that is the spring bounce.
 * from: where the wide card is on the screen, in pixels (null = nothing is playing, so the tile just grows in).
 * to: where the tile belongs, in pixels.
 * alpha: overall opacity (used to hide the tile on the wide Personalization page).
 * focus: -1 = none, 0 = previous, 1 = play/pause, 2 = next (only the tile has a selection).
 */
@Composable
fun MediaMorph(
    backdrop: Backdrop,
    progress: () -> Float,
    from: Rect?,
    to: Rect,
    alpha: Float,
    focus: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Not clamped: the spring overshoots, and that overshoot is what makes the card bounce.
    val p = progress()
    val pc = p.coerceIn(0f, 1f)

    // With nothing playing there is no card to fly from: the tile grows out of its own middle.
    val start = from ?: Rect(
        to.center.x - to.width * 0.3f, to.center.y - to.height * 0.3f,
        to.center.x + to.width * 0.3f, to.center.y + to.height * 0.3f,
    )
    val left = start.left + (to.left - start.left) * p
    val top = start.top + (to.top - start.top) * p
    val w = (start.width + (to.width - start.width) * p).coerceAtLeast(1f)
    val h = (start.height + (to.height - start.height) * p).coerceAtLeast(1f)

    val shape = RoundedCornerShape((24f + 2f * pc).dp)
    val wideAlpha = if (from == null) 0f else (1f - pc * 2.2f).coerceIn(0f, 1f)
    val tileAlpha = if (from == null) 1f else ((pc - 0.3f) / 0.5f).coerceIn(0f, 1f)
    val total = alpha * (if (from == null) (pc * 2f).coerceIn(0f, 1f) else 1f)

    val wideNaturalPx = with(density) { WideWidth.toPx() }
    val tileNaturalPx = with(density) { TileNatural.toPx() }

    Glass(
        backdrop,
        modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer { this.alpha = total },
        shape,
        tint = Color.White.copy(alpha = 0.10f),
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            if (wideAlpha > 0f) {
                ScaledContent(WideWidth, null, w / wideNaturalPx, wideAlpha) { WideBody(-1) }
            }
            if (tileAlpha > 0f) {
                ScaledContent(TileNatural, TileNatural, w / tileNaturalPx, tileAlpha) { TileBody(focus) }
            }
        }
    }
}
