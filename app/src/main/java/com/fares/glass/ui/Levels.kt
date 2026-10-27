package com.fares.glass.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Screen brightness and volume for the Control Center sliders (both 0..100). */
class Levels(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var volume by mutableIntStateOf(50)
        private set
    var brightness by mutableIntStateOf(50)
        private set

    private var volumeBeforeMute = -1

    /** Changing the brightness needs the "modify system settings" permission (see the notes about ADB). */
    val canSetBrightness: Boolean
        get() = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context)

    private fun maxVolume() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun readVolume() {
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume()
    }

    /** Reads the current values (called when the Control Center opens). */
    fun refresh() {
        readVolume()
        val raw = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        brightness = (raw * 100 / 255).coerceIn(1, 100)
    }

    /** dir = -1 or +1: about 5% per press. */
    fun adjustBrightness(dir: Int) {
        val next = (brightness + dir * 5).coerceIn(1, 100)
        brightness = next
        if (!canSetBrightness) return
        try {
            val cr = context.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (next * 255 / 100).coerceIn(1, 255))
        } catch (e: Exception) {
            Log.w("Glass", "Can't set the brightness", e)
        }
    }

    /** dir = -1 or +1: about 5% per press. */
    fun adjustVolume(dir: Int) {
        val max = maxVolume()
        val step = (max / 20).coerceAtLeast(1)
        val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + dir * step).coerceIn(0, max)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        } catch (e: Exception) {
            Log.w("Glass", "Can't set the volume", e)
        }
        readVolume()
    }

    /** OK on the volume slider: mute, or back to the volume before. */
    fun toggleMute() {
        val max = maxVolume()
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            if (current > 0) {
                volumeBeforeMute = current
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                val back = if (volumeBeforeMute > 0) volumeBeforeMute else max / 2
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, back.coerceIn(1, max), 0)
            }
        } catch (e: Exception) {
            Log.w("Glass", "Can't change the volume", e)
        }
        readVolume()
    }
}
