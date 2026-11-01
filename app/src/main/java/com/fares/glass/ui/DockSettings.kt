package com.fares.glass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dock, icon, motion and app-lock settings (Control Center > Personalization > Dock & icons).
 * They are saved, so they stay as you set them after a restart. The home screen reads them through
 * [Metrics], AppTile and the dock code in HomeScreen.
 */
object DockSettings {
    const val MIN_APPS = 4
    const val MAX_APPS = 8

    // ---- Dock ----

    /** The glass bar behind the first row of apps. Off = the apps stay, only the bar goes away. */
    var dockOn by mutableStateOf(true)
        private set

    /** Space around the icons inside the dock: 0 small, 1 medium, 2 large. */
    var dockSize by mutableIntStateOf(1)
        private set

    /** How far the dock sits above the bottom of the screen: 0 low, 1 medium, 2 high. */
    var dockHeight by mutableIntStateOf(0)
        private set

    /** How see-through the dock is: 0 clear, 1 light, 2 frosted. */
    var glass by mutableIntStateOf(1)
        private set

    /** The dock slides away when the remote is not used: 0 off, 1 after 5 s, 2 after 10 s. */
    var autoHide by mutableIntStateOf(0)
        private set

    // ---- Icons ----

    /** Size of the app icons: 0 small, 1 medium, 2 large. */
    var iconSize by mutableIntStateOf(1)
        private set

    /** Corners of the icons: 0 round, 1 rounded, 2 square. */
    var corner by mutableIntStateOf(1)
        private set

    /** App names: 0 never, 1 only under the selected app (not in the dock), 2 always (also in the dock). */
    var label by mutableIntStateOf(1)
        private set

    /** How much the selected icon grows: 0 none, 1 small, 2 big. */
    var zoom by mutableIntStateOf(1)
        private set

    /** How many apps there are in the dock (the first row), and so in every row of the grid. */
    var appsInDock by mutableIntStateOf(6)
        private set

    // ---- Motion ----

    /** Style of the icon and card animations: 0 off, 1 smooth, 2 bouncy. */
    var anim by mutableIntStateOf(2)
        private set

    // ---- Favorites row ----

    /** With this on, the apps you pinned (long-press menu) come first, so they fill the dock. */
    var favorites by mutableStateOf(false)
        private set

    /** Package names of the pinned apps, in the order they were pinned. */
    var pinned by mutableStateOf<List<String>>(emptyList())
        private set

    // ---- App lock ----

    /** Package names of the locked apps. */
    var locked by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The 4-digit PIN ("" = not set yet). */
    var pin by mutableStateOf("")
        private set

    /** Set by Control Center, handled (and cleared) by the home screen, which asks for the PIN first. */
    var resetLocksRequested by mutableStateOf(false)
        private set

    private var prefs: SharedPreferences? = null

    /** Reads the saved values (only the first call does anything). */
    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("glass_dock", Context.MODE_PRIVATE)
        prefs = p
        dockOn = p.getBoolean("dockOn", true)
        dockSize = p.getInt("dockSize", 1).coerceIn(0, 2)
        dockHeight = p.getInt("dockHeight", 0).coerceIn(0, 2)
        glass = p.getInt("glass", 1).coerceIn(0, 2)
        autoHide = p.getInt("autoHide", 0).coerceIn(0, 2)
        iconSize = p.getInt("iconSize", 1).coerceIn(0, 2)
        corner = p.getInt("corner", 1).coerceIn(0, 2)
        label = p.getInt("label", 1).coerceIn(0, 2)
        zoom = p.getInt("zoom", 1).coerceIn(0, 2)
        appsInDock = p.getInt("appsInDock", 6).coerceIn(MIN_APPS, MAX_APPS)
        anim = p.getInt("anim", 2).coerceIn(0, 2)
        favorites = p.getBoolean("favorites", false)
        pinned = p.getString("pinned", "").orEmpty().split(",").filter { it.isNotBlank() }
        locked = p.getString("locked", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
        pin = p.getString("pin", "").orEmpty()
    }

    private fun saveInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    fun toggleDock() {
        dockOn = !dockOn
        prefs?.edit()?.putBoolean("dockOn", dockOn)?.apply()
    }

    fun cycleDockSize() { dockSize = (dockSize + 1) % 3; saveInt("dockSize", dockSize) }
    fun cycleDockHeight() { dockHeight = (dockHeight + 1) % 3; saveInt("dockHeight", dockHeight) }
    fun cycleGlass() { glass = (glass + 1) % 3; saveInt("glass", glass) }
    fun cycleAutoHide() { autoHide = (autoHide + 1) % 3; saveInt("autoHide", autoHide) }
    fun cycleIconSize() { iconSize = (iconSize + 1) % 3; saveInt("iconSize", iconSize) }
    fun cycleCorner() { corner = (corner + 1) % 3; saveInt("corner", corner) }
    fun cycleLabel() { label = (label + 1) % 3; saveInt("label", label) }
    fun cycleZoom() { zoom = (zoom + 1) % 3; saveInt("zoom", zoom) }
    fun cycleAnim() { anim = (anim + 1) % 3; saveInt("anim", anim) }

    fun cycleApps() {
        appsInDock = if (appsInDock >= MAX_APPS) MIN_APPS else appsInDock + 1
        saveInt("appsInDock", appsInDock)
    }

    fun toggleFavorites() {
        favorites = !favorites
        prefs?.edit()?.putBoolean("favorites", favorites)?.apply()
    }

    // ---- Pinned apps ----

    fun isPinned(pkg: String?) = pkg != null && pkg in pinned

    fun togglePinned(pkg: String) {
        pinned = if (pkg in pinned) pinned - pkg else pinned + pkg
        prefs?.edit()?.putString("pinned", pinned.joinToString(","))?.apply()
    }

    // ---- App lock ----

    fun isLocked(pkg: String?) = pkg != null && pkg in locked

    fun setLocked(pkg: String, on: Boolean) {
        locked = if (on) locked + pkg else locked - pkg
        prefs?.edit()?.putString("locked", locked.joinToString(","))?.apply()
    }

    fun setPin(code: String) {
        pin = code
        prefs?.edit()?.putString("pin", code)?.apply()
    }

    /** Removes the PIN and every lock. */
    fun clearLocks() {
        locked = emptySet()
        pin = ""
        prefs?.edit()?.putString("locked", "")?.putString("pin", "")?.apply()
    }

    fun requestResetLocks() {
        if (pin.isNotBlank() || locked.isNotEmpty()) resetLocksRequested = true
    }

    fun consumeResetRequest() {
        resetLocksRequested = false
    }

    // ---- The settings page ----

    /** OK on row [index] of the Dock & icons page. */
    fun cycle(index: Int) {
        when (index) {
            0 -> toggleDock()
            1 -> cycleDockSize()
            2 -> cycleDockHeight()
            3 -> cycleGlass()
            4 -> cycleAutoHide()
            5 -> cycleIconSize()
            6 -> cycleCorner()
            7 -> cycleLabel()
            8 -> cycleZoom()
            9 -> cycleApps()
            10 -> cycleAnim()
            11 -> toggleFavorites()
            else -> requestResetLocks()
        }
    }

    fun sizeName(level: Int) = when (level) { 0 -> "Small"; 2 -> "Large"; else -> "Medium" }
    fun heightName(level: Int) = when (level) { 1 -> "Medium"; 2 -> "High"; else -> "Low" }

    /** Title and value of every row of the Dock & icons page, in the order of [cycle]. */
    fun rows(): List<Pair<String, String>> = listOf(
        "Dock" to if (dockOn) "On" else "Off",
        "Dock size" to sizeName(dockSize),
        "Dock height" to heightName(dockHeight),
        "Dock glass" to listOf("Clear", "Light", "Frosted")[glass],
        "Auto-hide dock" to listOf("Off", "5 s", "10 s")[autoHide],
        "Icon size" to sizeName(iconSize),
        "Icon shape" to listOf("Round", "Rounded", "Square")[corner],
        "App names" to listOf("Never", "Selected", "Always")[label],
        "Focus zoom" to listOf("None", "Small", "Big")[zoom],
        "Apps in dock" to "$appsInDock",
        "Motion style" to listOf("Off", "Smooth", "Bouncy")[anim],
        "Favorites row" to if (favorites) "On" else "Off",
        "App lock" to when {
            pin.isBlank() && locked.isEmpty() -> "Not set up"
            else -> "${locked.size} locked \u00B7 OK resets"
        },
    )

    /** One line for the category row: "Dock on · 6 apps". */
    fun summary() = (if (dockOn) "Dock on" else "Dock off") + " \u00B7 $appsInDock apps"
}
