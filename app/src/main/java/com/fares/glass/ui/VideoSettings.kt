package com.fares.glass.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.gfx.DepthSettings

/**
 * Is the video wallpaper the wallpaper, which video is it, and does it use the live "smooth loop"?
 * Works like RoomSettings: a switch that sits on top of the normal wallpapers.
 * Picking any other wallpaper turns it off.
 */
object VideoSettings {
    private const val PREFS = "glass_video"
    private const val KEY = "video_wallpaper_on"
    private const val KEY_URI = "video_wallpaper_uri"
    private const val KEY_SMOOTH = "video_smooth_loop"

    /** true = the video is the wallpaper until the user picks another one (this is the "default" switch). */
    private const val DEFAULT_ON = true

    var on by mutableStateOf(DEFAULT_ON)
        private set

    /** The video the user chose from the TV (a content:// address), or null for the built-in video. */
    var customUri by mutableStateOf<String?>(null)
        private set

    /**
     * Smooth loop: the launcher fades the end of the video into its start while playing (two players for
     * a moment). Only used for videos the user chose: the built-in video already has the fade in the file.
     */
    var smoothLoop by mutableStateOf(true)
        private set

    val hasCustom: Boolean
        get() = customUri != null

    /** True while the video is really the wallpaper (Simple mode wins over it). Glass and the home screen use this. */
    val active: Boolean
        get() = on && !DepthSettings.on

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        on = p.getBoolean(KEY, DEFAULT_ON)
        customUri = p.getString(KEY_URI, null)
        smoothLoop = p.getBoolean(KEY_SMOOTH, true)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    /** Remembers the chosen video (null = go back to the built-in one). */
    fun setCustom(context: Context, uri: String?) {
        customUri = uri
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (uri == null) e.remove(KEY_URI) else e.putString(KEY_URI, uri)
        e.apply()
    }

    fun setSmoothLoop(context: Context, value: Boolean) {
        smoothLoop = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SMOOTH, value).apply()
    }
}
