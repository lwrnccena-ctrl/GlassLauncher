package com.fares.glass.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.fares.glass.R

/**
 * Looping, muted video wallpaper: the video the user chose from the TV, or the built-in one
 * (res/raw/wallpaper_video.mp4).
 *
 * Two ways to loop:
 *  - Simple loop: two copies of the video are queued, so there is no black frame at the loop point.
 *  - Smooth loop (only for the user's own videos): two players take turns. About 1.5 seconds before the
 *    playing one ends, the other starts from the beginning and fades in over it, so the end of the video
 *    dissolves into its start. The video file does not need any editing. The built-in video already has
 *    this fade inside the file, so it always uses the simple loop (no double fade).
 *
 * It uses TextureViews (not SurfaceViews), so it works inside the home screen's layers. It pauses by
 * itself when the launcher is not visible (an app is open, the screen is off).
 */
class VideoWallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val views = arrayOf(TextureView(context), TextureView(context))
    private val players = arrayOfNulls<ExoPlayer>(2)
    private val handler = Handler(Looper.getMainLooper())

    private var videoW = 0
    private var videoH = 0
    private var usingCustom = false
    private var smooth = false
    private var failed = false

    /** Smooth loop: which of the two players is the one on screen. */
    private var active = 0
    private var fading = false
    private var fadeAnim: ValueAnimator? = null

    /** true = fill the screen (crops the edges), false = fit the whole video (black bars). */
    private var zoom = true

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width
            videoH = videoSize.height
            applyScale()
        }

        // If the video cannot play, say why (instead of a silent black screen).
        override fun onPlayerError(error: PlaybackException) {
            Log.e("VideoWallpaper", "Playback failed", error)
            if (failed) return
            failed = true
            when {
                smooth -> {
                    // Maybe this TV cannot run two decoders: go back to the simple loop (the home screen rebuilds this view).
                    Toast.makeText(context, "Smooth loop doesn't work on this TV, using the simple loop", Toast.LENGTH_LONG).show()
                    VideoSettings.setSmoothLoop(context, false)
                }
                usingCustom -> {
                    // The chosen video is gone or unreadable: forget it, the home screen then rebuilds this view
                    // with the built-in video.
                    Toast.makeText(context, "Couldn't play that video, using the built-in one", Toast.LENGTH_LONG).show()
                    VideoSettings.setCustom(context, null)
                }
                else -> Toast.makeText(context, "Video wallpaper error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Smooth loop: checks the playing video a few times a second and starts the fade near its end. */
    private val tick = object : Runnable {
        override fun run() {
            checkFade()
            handler.postDelayed(this, 100)
        }
    }

    init {
        isFocusable = false
        setBackgroundColor(Color.BLACK)
    }

    fun setFillMode(zoom: Boolean) {
        this.zoom = zoom
        applyScale()
    }

    /**
     * Creates the player(s) the first time, then plays.
     * [customUri] = the video chosen from the TV; null = the built-in video.
     * [smoothLoop] = the live crossfade (ignored for the built-in video).
     */
    fun start(customUri: String? = null, smoothLoop: Boolean = false) {
        if (players[0] != null) {
            resume()
            return
        }
        usingCustom = customUri != null
        smooth = smoothLoop && usingCustom
        // R.raw makes the build fail if the built-in file is missing, instead of failing silently.
        val uri = if (customUri != null) Uri.parse(customUri)
        else Uri.parse("android.resource://${context.packageName}/${R.raw.wallpaper_video}")
        val item = MediaItem.fromUri(uri)
        val count = if (smooth) 2 else 1
        for (i in 0 until count) {
            addView(views[i], LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            views[i].addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyScale() }
            players[i] = ExoPlayer.Builder(context).build().apply {
                setVideoTextureView(views[i])
                volume = 0f
                addListener(listener)
                if (smooth) {
                    // One video each; the second one waits, ready on its first frame.
                    setMediaItem(item)
                    repeatMode = Player.REPEAT_MODE_OFF
                } else {
                    // Two copies of the same video in a playlist: the player moves from one to the next without
                    // stopping, so there is no black frame at the loop point.
                    setMediaItems(listOf(item, item))
                    repeatMode = Player.REPEAT_MODE_ALL
                }
                playWhenReady = i == 0 && windowVisibility == View.VISIBLE
                prepare()
            }
        }
        if (smooth) views[1].alpha = 0f
    }

    /** Smooth loop: starts the other player and fades it in over the one that is about to end. */
    private fun checkFade() {
        if (fading) return
        val p = players[active] ?: return
        if (!p.isPlaying) return
        val duration = p.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val fadeMs = minOf(FADE_MS, duration / 3)
        val remaining = duration - p.currentPosition
        if (remaining <= fadeMs) beginFade(maxOf(remaining, 200L))
    }

    private fun beginFade(ms: Long) {
        val next = 1 - active
        val incoming = players[next] ?: return
        fading = true
        views[next].alpha = 0f
        views[next].bringToFront()
        incoming.seekTo(0)
        incoming.play()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            addUpdateListener { views[next].alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finishFade(next)
            })
            start()
        }
    }

    /** The fade is over (or was cancelled): the new player is the one on screen, the old one gets ready again. */
    private fun finishFade(next: Int) {
        if (!fading) return
        fading = false
        val old = active
        active = next
        views[next].alpha = 1f
        players[old]?.let {
            it.pause()
            it.seekTo(0)
        }
    }

    private fun resume() {
        if (players[0] == null) return
        players[active]?.play()
        if (smooth) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    private fun pauseAll() {
        handler.removeCallbacks(tick)
        // A fade in progress is finished at once (the new player takes over), then everything pauses.
        fadeAnim?.cancel()
        fadeAnim = null
        for (p in players) p?.pause()
    }

    fun stop() = pauseAll()

    fun release() {
        handler.removeCallbacks(tick)
        fading = false // so cancelling the animation does not start a swap
        fadeAnim?.cancel()
        fadeAnim = null
        for (i in players.indices) {
            players[i]?.release()
            players[i] = null
        }
    }

    /** A TextureView stretches the video to its size; this keeps the picture's shape (fill or fit). */
    private fun applyScale() {
        for (v in views) {
            val vw = v.width.toFloat()
            val vh = v.height.toFloat()
            if (vw <= 0f || vh <= 0f || videoW <= 0 || videoH <= 0) continue
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
            v.setTransform(m)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) resume() else pauseAll()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** How long the end of the video dissolves into its start (smooth loop). */
        const val FADE_MS = 1500L
    }
}
