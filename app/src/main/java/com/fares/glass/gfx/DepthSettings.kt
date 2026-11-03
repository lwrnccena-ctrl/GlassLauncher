package com.fares.glass.gfx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Simple mode (depth wallpaper) switch. [reload] changes whenever the pictures should be read again. */
object DepthSettings {
    private const val PREFS = "glass_depth"
    private const val KEY_ON = "on"

    var on by mutableStateOf(false)
        private set

    var reload by mutableIntStateOf(0)
        private set

    fun load(context: Context) {
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    }

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, value).apply()
        on = value
        // Turning it on re-reads the pictures, so a photo that was just replaced shows up.
        if (value) reload++
    }

    /** Call after the pictures in Glass/depth/ have changed. */
    fun requestReload() {
        reload++
    }
}
