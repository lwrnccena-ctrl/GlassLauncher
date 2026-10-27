package com.fares.glass

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Set to true to bring the idle screensaver back. */
private const val ENABLE_IDLE_SAVER = false

/** How long with no remote press before the screensaver starts over another app (5 minutes). */
private const val IDLE_MS = 5 * 60_000L

class HomeAccessibilityService : AccessibilityService() {

    private val stockLaunchers = setOf(
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.amazon.tv.launcher",
        "com.amazon.firelauncher",
        "com.android.tv.launcher"
    )

    // While one of these is on screen, ignore stock launcher refreshes
    private val settingsPackages = setOf(
        "com.android.tv.settings",
        "com.android.settings",
        "com.google.android.tvsettings",
        "com.android.captiveportallogin",
        "com.google.android.captiveportallogin",
        "com.android.networkstack"
    )

    private val musicApps = setOf(
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic",
        "com.apple.android.music"
    )

    private val ignoredPackages = setOf("com.android.systemui", "android")

    private val handler = Handler(Looper.getMainLooper())
    private var lastLaunch = 0L
    private var lastInput = SystemClock.elapsedRealtime()
    private var foregroundPkg = ""
    private var saverStarted = false

    private val idleCheck = object : Runnable {
        override fun run() {
            checkIdle()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastInput = SystemClock.elapsedRealtime()
        handler.removeCallbacks(idleCheck)
        if (ENABLE_IDLE_SAVER) handler.postDelayed(idleCheck, 5_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        lastInput = SystemClock.elapsedRealtime()
        saverStarted = false

        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && event.eventTime - event.downTime < 500) {
            launchGlass(screensaver = false)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Log.d("GlassHome", "window: $pkg (foreground=$foregroundPkg)")

        if (pkg in stockLaunchers) {
            // Ignore launcher refreshes (e.g. after joining Wi-Fi) while in Settings
            if (foregroundPkg in settingsPackages) return
            launchGlass(screensaver = false)
            return
        }
        if (pkg !in ignoredPackages) foregroundPkg = pkg
    }

    private fun checkIdle() {
        if (saverStarted) return
        if (foregroundPkg.isEmpty() || foregroundPkg == packageName) return
        if (SystemClock.elapsedRealtime() - lastInput < IDLE_MS) return

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.isMusicActive && foregroundPkg !in musicApps) return

        saverStarted = true
        launchGlass(screensaver = true)
    }

    private fun launchGlass(screensaver: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 600) return
        lastLaunch = now

        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("screensaver", screensaver)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
        )
    }

    override fun onInterrupt() {}
}
