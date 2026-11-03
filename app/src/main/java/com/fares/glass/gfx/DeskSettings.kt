package com.fares.glass.gfx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Remembers whether the "Auto" wallpaper shows the Desk room (true) or the living room with the TV (false).
 * Both follow the time of day and the rain.
 */
object DeskSettings {
    var on by mutableStateOf(false)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("glass_wallpaper", Context.MODE_PRIVATE)

    fun load(context: Context) {
        on = prefs(context).getBoolean("desk", false)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        prefs(context).edit().putBoolean("desk", value).apply()
    }
}
