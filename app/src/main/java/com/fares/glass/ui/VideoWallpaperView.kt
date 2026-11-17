package com.fares.glass.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/**
 * Looping, muted video wallpaper (res/raw/wallpaper_video.mp4).
 *
 * It uses a TextureView (not a SurfaceView), so it can sit inside the home screen's blurred,
 * recorded "scene" layer. It pauses by itself when the launcher is not visible (an app is open,
 * the screen is off) and plays again when it comes back.
 */
class VideoWallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val textureView = TextureView(context)
    private var player: ExoPlayer? = null
    private var videoW = 0
    private var videoH = 0

    /** true = fill the screen (crops the edges), false = fit the whole video (black bars). */
    private var zoom = true

    init {
        isFocusable = false
        setBackgroundColor(Color.BLACK)
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyScale() }
    }

    fun setFillMode(zoom: Boolean) {
        this.zoom = zoom
        applyScale()
    }

    /** Creates the player the first time, then plays. */
    fun start() {
        if (player == null) {
            val uri = Uri.parse("android.resource://${context.packageName}/raw/wallpaper_video")
            player = ExoPlayer.Builder(context).build().apply {
                setVideoTextureView(textureView)
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoW = videoSize.width
                        videoH = videoSize.height
                        applyScale()
                    }
                })
                playWhenReady = windowVisibility == View.VISIBLE
                prepare()
            }
        } else if (windowVisibility == View.VISIBLE) {
            player?.play()
        }
    }

    fun stop() {
        player?.pause()
    }

    fun release() {
        player?.release()
        player = null
    }

    /** A TextureView stretches the video to its size; this keeps the picture's shape (fill or fit). */
    private fun applyScale() {
        val vw = textureView.width.toFloat()
        val vh = textureView.height.toFloat()
        if (vw <= 0f || vh <= 0f || videoW <= 0 || videoH <= 0) return
        val viewRatio = vw / vh
        val videoRatio = videoW.toFloat() / videoH
        var sx = 1f
        var sy = 1f
        if (zoom) {
            if (videoRatio > viewRatio) sx = videoRatio / viewRatio else sy = viewRatio / videoRatio
        } else {
            if (videoRatio > viewRatio) sy = viewRatio / videoRatio else sx = videoRatio / viewRatio
        }
        val m = Matrix()
        m.setScale(sx, sy, vw / 2f, vh / 2f)
        textureView.setTransform(m)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) player?.play() else player?.pause()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
