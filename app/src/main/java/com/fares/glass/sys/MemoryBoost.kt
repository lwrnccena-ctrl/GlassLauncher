package com.fares.glass.sys

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Memory numbers and the "Boost RAM" action for the widgets page.
 *
 * Boost = `am kill-all`, run through the ADB connection Glass already has (AdbController).
 * It stops background apps. The app on screen is not touched. Music playing in the background
 * may stop too.
 */
object MemoryBoost {
    const val COMMAND = "am kill-all"

    /** Shown on the widget when ADB is not connected. */
    const val NOT_CONNECTED = "Connect ADB: Control Center > ADB"

    class Stats(val totalBytes: Long, val availBytes: Long) {
        /** 0..1: how much of the RAM is in use. */
        val usedFraction: Float
            get() = if (totalBytes <= 0L) 0f else (1f - availBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    class Outcome(val ok: Boolean, val freedBytes: Long, val message: String)

    fun read(ctx: Context): Stats {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Stats(info.totalMem, info.availMem)
    }

    /**
     * Reads the free memory, runs the boost, waits a moment and reads it again.
     * AdbController.run connects by itself when it can (it tries the last TV it reached, this device and localhost).
     * If that fails, the ADB pairing has not been done yet, so the widget asks the user to set it up.
     */
    suspend fun boost(ctx: Context): Outcome {
        val before = read(ctx)
        val r = AdbController.run(ctx, COMMAND)
        if (!r.ok) return Outcome(false, 0L, NOT_CONNECTED)
        delay(1500L)
        val after = read(ctx)
        return Outcome(true, (after.availBytes - before.availBytes).coerceAtLeast(0L), "")
    }

    fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    fun mb(bytes: Long): String = String.format(Locale.US, "%d MB", bytes / 1_048_576L)
}
