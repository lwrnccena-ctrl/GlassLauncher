package com.fares.glass.sys

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.delay

/**
 * The four Control Center quick tiles. Call these from a coroutine on the main thread
 * (ControlCenter's own `scope` is fine).
 *
 * AdbController.run() reports ok = true whenever the shell stream opens, even if the command
 * itself failed, so sh() also looks at the output for error words.
 */
object QuickActions {

    private val FAIL_WORDS = listOf(
        "not permitted", "denied", "not found", "exception",
        "error", "unknown", "usage:", "inaccessible",
    )

    private suspend fun sh(ctx: Context, cmd: String): AdbController.Result {
        val r = AdbController.run(ctx, cmd)
        if (!r.ok) return r
        val bad = FAIL_WORDS.any { r.output.contains(it, ignoreCase = true) }
        return AdbController.Result(!bad, r.output)
    }

    /** Tries each command in order and stops at the first one that works. */
    private suspend fun firstOf(ctx: Context, vararg cmds: String): AdbController.Result {
        var last = AdbController.Result(false, "No command")
        for (c in cmds) {
            last = sh(ctx, c)
            if (last.ok) return last
        }
        return last
    }

    /** Screen dim: toggles the dark overlay. Returns an error message, or null if all went well. */
    suspend fun toggleDim(ctx: Context): String? {
        if (DimOverlay.on) {
            DimOverlay.hide(ctx)
            return null
        }
        if (!Settings.canDrawOverlays(ctx)) {
            sh(ctx, "appops set ${AdbController.PKG} SYSTEM_ALERT_WINDOW allow")
            delay(250)
        }
        return if (DimOverlay.show(ctx)) null
        else "Allow \"Display over other apps\" for Glass in Settings > Apps"
    }

    /** Screenshot: result.output is the saved file path on success, or the error. */
    suspend fun screenshot(ctx: Context): AdbController.Result {
        delay(450) // let the panel finish closing so it isn't in the picture
        val dir = "/sdcard/Pictures/Screenshots"
        val file = "$dir/glass_${System.currentTimeMillis()}.png"
        val r = sh(ctx, "mkdir -p $dir; screencap -p $file")
        return AdbController.Result(r.ok, if (r.ok) file else r.output)
    }

    /** Sleep: screen off / standby. */
    suspend fun sleep(ctx: Context): AdbController.Result {
        delay(300)
        return sh(ctx, "input keyevent KEYCODE_SLEEP")
    }

    /**
     * Shutdown: on most TVs this ends up as standby. If the TV really turns off, the ADB link
     * drops and the later fallbacks simply fail, which is harmless.
     */
    suspend fun shutdown(ctx: Context): AdbController.Result {
        delay(300)
        return firstOf(
            ctx,
            "svc power shutdown",
            "reboot -p",
            "input keyevent --longpress KEYCODE_POWER", // opens the power menu
        )
    }
}
