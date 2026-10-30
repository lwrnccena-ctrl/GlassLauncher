package com.fares.glass.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.gfx.AppVisual
import com.fares.glass.gfx.TileFactory
import com.fares.glass.ui.ClockStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val activity: String, val label: String, val isTv: Boolean) {
    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(if (isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
        .setClassName(pkg, activity)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** Installed apps, their order (users rearrange them, as on tvOS), hidden apps and settings. */
class AppRepository(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager
    private val prefs = app.getSharedPreferences("glass", Context.MODE_PRIVATE)
    private val tiles = TileFactory(app)
    private val mutex = Mutex()
    private var installed: List<AppEntry> = emptyList()

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    val visuals = mutableStateMapOf<String, AppVisual>()
    var hiddenCount by mutableIntStateOf(0)
        private set
    var wallpaperIndex by mutableIntStateOf(prefs.getInt("wallpaper", 0))
        private set
    var topShelf by mutableStateOf(prefs.getBoolean("topShelf", false))
        private set
    /** Which home-screen clock style is used (see ClockStyle): 0 classic, 1 stacked, 2 analog, 3 pill, 4 flip. */
    var clockStyle by mutableIntStateOf(prefs.getInt("clockStyle", ClockStyle.CLASSIC).coerceIn(0, ClockStyle.COUNT - 1))
        private set
    /** First name in the greeting ("Good morning, Alex"): `am start -n com.fares.glass/.MainActivity --es name Alex`. */
    var userName by mutableStateOf(prefs.getString("name", "").orEmpty())
        private set

    fun setName(name: String) {
        userName = name.trim()
        prefs.edit().putString("name", userName).apply()
    }

    suspend fun refresh() = mutex.withLock {
        installed = withContext(Dispatchers.IO) { scan() }
        publish()
        for (entry in apps) {
            if (entry.pkg in visuals) continue
            val visual = withContext(Dispatchers.IO) {
                try {
                    tiles.build(entry)
                } catch (e: Exception) {
                    Log.w("Glass", "Can't build tile for ${entry.pkg}", e)
                    null
                }
            } ?: continue
            visuals[entry.pkg] = visual
        }
    }

    /** An updated app may have a new banner. */
    fun invalidate(pkg: String) {
        visuals.remove(pkg)
    }

    private fun scan(): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        for ((category, tv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == app.packageName || ai.packageName in byPkg) continue
                byPkg[ai.packageName] = AppEntry(ai.packageName, ai.name, ri.loadLabel(pm).toString().trim(), tv)
            }
        }
        return byPkg.values.toList()
    }

    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()).orEmpty()

    private fun publish() {
        val rank = prefs.getString("order", "").orEmpty().split('|').withIndex().associate { it.value to it.index }
        val hidden = hidden()
        // User order first; new apps go at the end, TV apps before mobile apps.
        apps = installed
            .sortedWith(compareBy<AppEntry>({ rank[it.pkg] ?: Int.MAX_VALUE }, { !it.isTv }, { it.label.lowercase() }))
            .filter { it.pkg !in hidden }
        hiddenCount = installed.count { it.pkg in hidden }
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in apps.indices || to !in apps.indices) return
        apps = apps.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun saveOrder() {
        val hidden = hidden()
        val order = apps.map { it.pkg } + installed.filter { it.pkg in hidden }.map { it.pkg }
        prefs.edit().putString("order", order.joinToString("|")).apply()
    }

    fun hide(pkg: String) {
        saveOrder()
        prefs.edit().putStringSet("hidden", hidden() + pkg).apply()
        publish()
    }

    fun unhideAll() {
        prefs.edit().remove("hidden").apply()
        publish()
    }

    fun setWallpaper(index: Int) {
        wallpaperIndex = index
        prefs.edit().putInt("wallpaper", index).apply()
    }

    fun toggleTopShelf() {
        topShelf = !topShelf
        prefs.edit().putBoolean("topShelf", topShelf).apply()
    }

    /** Saves the clock style chosen in Control Center > Clock. */
    fun changeClockStyle(style: Int) { 
        clockStyle = style.coerceIn(0, ClockStyle.COUNT - 1)
        prefs.edit().putInt("clockStyle", clockStyle).apply()
    }
}
