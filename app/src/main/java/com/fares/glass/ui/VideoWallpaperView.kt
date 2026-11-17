package com.fares.glass.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Looping, muted video wallpaper.
 *
 * Usage:
 *   val v = VideoWallpaperView(context)
 *   container.addView(v)
 *   v.start()          // e.g. onStart / when wallpaper is selected
 *   v.stop()           // e.g. onStop / when wallpaper is switched away
 *   v.release()        // when the view is destroyed
 */
@OptIn(UnstableApi::class)
class VideoWallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val playerView = PlayerView(context).apply {
        useController = false
        // ZOOM fills the screen (crops edges). Use RESIZE_MODE_FIT to see the whole frame.
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setKeepContentOnPlayerReset(true)
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        isFocusable = false
        isClickable = false
    }

    private var player: ExoPlayer? = null

    init {
        isFocusable = false
        addView(
            playerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    /** Change how the video fills the screen. */
    fun setFillMode(zoom: Boolean) {
        playerView.resizeMode =
            if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun start() {
        if (player == null) {
            val uri = Uri.parse("android.resource://${context.packageName}/raw/wallpaper_video")
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                prepare()
            }
            playerView.player = player
        } else {
            player?.play()
        }
    }

    fun stop() {
        player?.pause()
    }

    fun release() {
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
