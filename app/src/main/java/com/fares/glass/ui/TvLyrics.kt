package com.fares.glass.ui

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.data.LyricLine
import com.fares.glass.data.LyricsRepository
import com.fares.glass.sys.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where the TV screen is inside SkyWallpaper (1280 x 720), as fractions of the picture.
 * From SkyWallpaper: tvX = 0.40 w, tvY = 0.23 h, tvW = 0.40 w, tvH = tvW * 9 / 16.
 * If you move or resize the TV in SkyWallpaper.kt, change these numbers too.
 */
object TvScreen {
    const val WALLPAPER_W = 1280f
    const val WALLPAPER_H = 720f
    const val LEFT = 0.40f
    const val TOP = 0.23f
    const val RIGHT = 0.80f
    const val BOTTOM = 0.63f

    /** The TV screen in pixels on a display of this size (the wallpaper is drawn with ContentScale.Crop). */
    fun bounds(screenW: Int, screenH: Int): android.graphics.Rect {
        val scale = max(screenW / WALLPAPER_W, screenH / WALLPAPER_H)
        val dx = (screenW - WALLPAPER_W * scale) / 2f
        val dy = (screenH - WALLPAPER_H * scale) / 2f
        return android.graphics.Rect(
            (dx + WALLPAPER_W * scale * LEFT).roundToInt(),
            (dy + WALLPAPER_H * scale * TOP).roundToInt(),
            (dx + WALLPAPER_W * scale * RIGHT).roundToInt(),
            (dy + WALLPAPER_H * scale * BOTTOM).roundToInt(),
        )
    }
}

/** One lyric line: the line that is being sung is big and bright and bounces in, the others are small and dim. */
@Composable
private fun LyricRow(text: String, active: Boolean, blurAmount: Float) {
    // Low damping = a little overshoot, so the line "pops" when it becomes the current one.
    val scale by animateFloatAsState(
        if (active) 1f else 0.78f, spring(dampingRatio = 0.42f, stiffness = 340f), label = "lyricScale",
    )
    val fade by animateFloatAsState(if (active) 1f else 0.32f, tween(350), label = "lyricFade")
    // Android 12+ blurs the whole list (see TvLyrics). On older Android (the TV runs 11) the text is
    // faded out while a soft glow of the same text takes its place, which looks like a blur.
    val glowRadius = with(LocalDensity.current) { 9.dp.toPx() } * blurAmount
    val style = if (Build.VERSION.SDK_INT >= 31 || blurAmount < 0.01f) {
        txt(Color.White, 18.sp, FontWeight.Bold)
    } else {
        txt(Color.White.copy(alpha = 1f - blurAmount), 18.sp, FontWeight.Bold).copy(
            shadow = Shadow(Color.White.copy(alpha = 0.85f * blurAmount), Offset.Zero, glowRadius),
        )
    }
    BasicText(
        text.ifBlank { "\u266A" },
        style = style,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = fade
            transformOrigin = TransformOrigin(0f, 0.5f)
        },
    )
}

/** Synced lyrics of the song that is playing, drawn on the TV screen of the sky wallpaper (Apple Music style). */
@Composable
fun TvLyrics(modifier: Modifier = Modifier, blurAmount: Float = 0f) {
    var lines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val title = NowPlaying.title
    val artist = NowPlaying.artist
    val duration = NowPlaying.durationMs

    // New song: look up its lyrics (the short delay skips songs that are skipped quickly).
    LaunchedEffect(title, artist, duration) {
        lines = emptyList()
        if (title.isBlank()) return@LaunchedEffect
        delay(500)
        lines = LyricsRepository.fetch(title, artist, duration)
    }

    // Clock for the current line.
    LaunchedEffect(NowPlaying.active) {
        while (NowPlaying.active) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    if (!NowPlaying.active || lines.isEmpty()) return

    val position = NowPlaying.position(now) + 250 // show a line a little early
    var index = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= position) index = i else break
    }

    val listState = rememberLazyListState()
    val target = max(index, 0) + 1 // +1: the list starts with a spacer

    // Keep the current line in the middle of the TV. It glides there with a soft bounce.
    LaunchedEffect(target, lines) {
        fun centerDelta(): Float? {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return null
            return item.offset + item.size / 2f - info.viewportSize.height / 2f
        }
        val delta = centerDelta()
        if (delta != null) {
            listState.animateScrollBy(delta, spring(dampingRatio = 0.72f, stiffness = 130f))
        } else {
            // The line is far away (the song was seeked): jump close, then center it.
            listState.scrollToItem(target)
            withFrameNanos { }
            centerDelta()?.let { listState.scrollBy(it) }
        }
    }

    BoxWithConstraints(modifier) {
        // The wallpaper is drawn with ContentScale.Crop: work out where the TV ends up on screen.
        val tv = TvScreen.bounds(constraints.maxWidth, constraints.maxHeight)
        val density = LocalDensity.current
        val tvHeight = with(density) { tv.height().toDp() }

        Box(
            Modifier
                .offset { IntOffset(tv.left, tv.top) }
                .size(with(density) { tv.width().toDp() }, tvHeight)
                // Behind the Control Center the lyrics blur and dim like the wallpaper (blur: Android 12+).
                .then(
                    if (Build.VERSION.SDK_INT >= 31 && blurAmount > 0.01f) {
                        Modifier.blur(14.dp * blurAmount, BlurredEdgeTreatment.Unbounded)
                    } else Modifier,
                )
                .graphicsLayer { alpha = 1f - 0.3f * blurAmount }
                .padding(horizontal = 18.dp),
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Lines fade out toward the top and bottom edge of the TV.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent, 0.20f to Color.Black,
                                0.80f to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                item(key = "top") { Spacer(Modifier.height(tvHeight / 2)) }
                itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                    LyricRow(line.text, active = i == index, blurAmount = blurAmount)
                }
                item(key = "bottom") { Spacer(Modifier.height(tvHeight / 2)) }
            }
        }
    }
}
