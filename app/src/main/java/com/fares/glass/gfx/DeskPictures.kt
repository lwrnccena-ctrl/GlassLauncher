package com.fares.glass.gfx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File

/**
 * Your own pictures for the desk wallpaper. Put them in the folder  Glass/desk  on the TV's storage
 * (the same place as Glass/wallpaper.jpg), named:
 *
 *   monitor1.jpg   left monitor
 *   monitor2.jpg   middle monitor
 *   monitor3.jpg   right monitor
 *   frame.jpg      picture frame on the wall, top left
 *
 * .jpg, .jpeg, .png and .webp all work. A picture that is missing keeps the built-in look.
 * Pictures are cropped to fit, so any shape works.
 */
class DeskPictures(val monitors: List<Bitmap?>, val frame: Bitmap?) {

    companion object {
        private val dir get() = File(Environment.getExternalStorageDirectory(), "Glass/desk")

        /** Returns null when there is no permission or no picture at all. Call it off the main thread. */
        fun load(context: Context): DeskPictures? {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return null
            val monitors = (1..3).map { decode("monitor$it") }
            val frame = decode("frame")
            if (monitors.all { it == null } && frame == null) return null
            return DeskPictures(monitors, frame)
        }

        /** Changes when a picture is added, replaced or removed, so the wallpaper redraws by itself. */
        fun signature(): Long {
            var sum = 0L
            for (name in listOf("monitor1", "monitor2", "monitor3", "frame")) {
                for (ext in listOf("jpg", "jpeg", "png", "webp")) {
                    val f = File(dir, "$name.$ext")
                    sum += f.lastModified() + f.length()
                }
            }
            return sum
        }

        private fun decode(name: String): Bitmap? {
            for (ext in listOf("jpg", "jpeg", "png", "webp")) {
                val file = File(dir, "$name.$ext")
                if (!file.canRead()) continue
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 800) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.let { return it }
            }
            return null
        }
    }
}
