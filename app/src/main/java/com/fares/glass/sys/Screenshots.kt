package com.fares.glass.sys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** The last picture of each app, shown on the recent apps cards. Saved as small JPEGs in the cache folder. */
object Screenshots {
    private var dir: File? = null
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** Changes every time a picture is saved, so the screen redraws. */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.cacheDir, "recents").also { it.mkdirs() }
    }

    fun get(pkg: String): ImageBitmap? {
        if (version < 0) return null // reading it makes Compose redraw when a new picture arrives
        cache[pkg]?.let { return it }
        val file = File(dir ?: return null, "$pkg.jpg")
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        return bmp.asImageBitmap().also { cache[pkg] = it }
    }

    fun save(pkg: String, shot: Bitmap) {
        val folder = dir ?: return
        val w = 640
        val h = (w * shot.height.toFloat() / shot.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(shot, w, h, true)
        try {
            FileOutputStream(File(folder, "$pkg.jpg")).use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        } catch (e: Exception) {
            return
        }
        cache[pkg] = small.asImageBitmap()
        version++
    }

    fun remove(pkg: String) {
        cache.remove(pkg)
        dir?.let { File(it, "$pkg.jpg").delete() }
        version++
    }
}
