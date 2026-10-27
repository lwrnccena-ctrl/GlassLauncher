package com.fares.glass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** On/off switches of the home screen. They are saved, so they stay as you set them after a restart. */
object Features {
    var verse by mutableStateOf(true)
        private set
    var music by mutableStateOf(true)
        private set
    var lyrics by mutableStateOf(true)
        private set
    var weather by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    /** Reads the saved values (only the first call does anything). */
    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("glass_features", Context.MODE_PRIVATE)
        prefs = p
        verse = p.getBoolean("verse", true)
        music = p.getBoolean("music", true)
        lyrics = p.getBoolean("lyrics", true)
        weather = p.getBoolean("weather", true)
    }

    fun toggleVerse() {
        verse = !verse
        prefs?.edit()?.putBoolean("verse", verse)?.apply()
    }

    fun toggleMusic() {
        music = !music
        prefs?.edit()?.putBoolean("music", music)?.apply()
    }

    fun toggleLyrics() {
        lyrics = !lyrics
        prefs?.edit()?.putBoolean("lyrics", lyrics)?.apply()
    }

    fun toggleWeather() {
        weather = !weather
        prefs?.edit()?.putBoolean("weather", weather)?.apply()
    }
}
