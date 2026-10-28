package com.fares.glass.sys

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.fares.glass.ui.Recents
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Takes a small picture of the app on screen every few seconds (only apps opened from Glass),
 * so the recent apps screen can show what each app looked like.
 */
class GlassAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val lastShot = ConcurrentHashMap<String, Long>()
    @Volatile private var current: String? = null
    private var scheduled = false

    override fun onServiceConnected() {
        Screenshots.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // Glass itself is on screen: no app is in front.
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) current = null
            return
        }
        if (pkg !in Recents.list) return

        // A window of the app opened: it is the app in front.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) current = pkg
        if (pkg != current || scheduled) return

        val sinceLast = SystemClock.elapsedRealtime() - (lastShot[pkg] ?: 0L)
        val wait = if (sinceLast > 5000L) 1500L else 5500L - sinceLast
        scheduled = true
        handler.postDelayed({
            scheduled = false
            capture(pkg)
        }, wait)
    }

    private fun capture(pkg: String) {
        if (current != pkg) return
        if (Build.VERSION.SDK_INT >= 30) shoot(pkg)
    }

    @RequiresApi(30)
    private fun shoot(pkg: String) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        if (current != pkg) return // the user already left the app
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return
                        val soft = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        hardware.recycle()
                        if (soft != null && !mostlyBlack(soft)) {
                            Screenshots.save(pkg, soft)
                            lastShot[pkg] = SystemClock.elapsedRealtime()
                        }
                    } catch (e: Exception) {
                        Log.w("Glass", "Screenshot failed", e)
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w("Glass", "Screenshot refused: $errorCode")
                }
            })
        } catch (e: Exception) {
            Log.w("Glass", "Can't take a screenshot", e)
        }
    }

    /** Apps that block screenshots (DRM video) give a black picture: keep the old one instead. */
    private fun mostlyBlack(bmp: Bitmap): Boolean {
        for (ix in 0 until 12) {
            for (iy in 0 until 8) {
                val p = bmp.getPixel(
                    (bmp.width - 1) * ix / 11,
                    (bmp.height - 1) * iy / 7,
                )
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r + g + b > 24) return false
            }
        }
        return true
    }

    override fun onInterrupt() {}
}
