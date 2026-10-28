package com.fares.glass.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.sys.Screenshots

/** The apps opened from Glass, newest first (saved), and whether the recent apps stack is showing. */
object Recents {
    private const val MAX = 8

    /** True while the stack of recent apps is on screen. */
    var open by mutableStateOf(false)

    var list by mutableStateOf<List<String>>(emptyList())
        private set

    private var prefs: SharedPreferences? = null

    /** Reads the saved list (only the first call does anything). */
    fun load(context: Context) {
        if (prefs != null) return
        Screenshots.init(context)
        val p = context.applicationContext.getSharedPreferences("glass_recents", Context.MODE_PRIVATE)
        prefs = p
        list = p.getString("apps", "").orEmpty().split(",").filter { it.isNotBlank() }
    }

    /** An app was opened: it goes to the front. */
    fun push(pkg: String) {
        if (pkg.isBlank()) return
        val dropped = list.filter { it != pkg }.drop(MAX - 1)
        list = (listOf(pkg) + list.filter { it != pkg }).take(MAX)
        dropped.forEach(Screenshots::remove)
        save()
    }

    fun remove(pkg: String) {
        list = list.filter { it != pkg }
        Screenshots.remove(pkg)
        save()
    }

    private fun save() {
        prefs?.edit()?.putString("apps", list.joinToString(","))?.apply()
    }
}
