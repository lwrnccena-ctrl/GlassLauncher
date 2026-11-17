package com.fares.glass.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.gfx.DepthSettings

/**
 * Is the video wallpaper (res/raw/wallpaper_video.mp4) the wallpaper?
 * Works like RoomSettings: a switch that sits on top of the normal wallpapers.
 * Picking any other wallpaper turns it off.
 */
object VideoSettings {
    private const val PREFS = "glass_video"
    private const val KEY = "video_wallpaper_on"

    /** true = the video is the wallpaper until the user picks another one (this is the "default" switch). */
    private const val DEFAULT_ON = true

    var on by mutableStateOf(DEFAULT_ON)
        private set

    /** True while the video is really the wallpaper (Simple mode wins over it). Glass and the home screen use this. */
    val active: Boolean
        get() = on && !DepthSettings.on

    fun load(context: Context) {
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, DEFAULT_ON)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}
