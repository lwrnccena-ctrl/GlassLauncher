package com.fares.glass.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Is the live 3D gamer room the wallpaper? It sits on top of the normal wallpaper choice:
 * the old wallpapers (Liquid, Aurora, Dusk, Graphite, Auto) keep working underneath, and picking one of
 * them simply switches this off.
 */
object RoomSettings {
    var on by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("glass_room", Context.MODE_PRIVATE)

    fun load(c: Context) {
        on = prefs(c).getBoolean("on", false)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean("on", value).apply()
    }
}
