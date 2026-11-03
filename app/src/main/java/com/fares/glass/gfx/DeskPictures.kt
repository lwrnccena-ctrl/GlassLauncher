package com.fares.glass.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File

/**
 * The two pictures of Simple mode:
 *  - background.jpg (or .png): the photo (required)
 *  - foreground.png: a transparent cutout of the subject, drawn over the clock (optional)
 * They are read from /sdcard/Glass/depth/, then from the app's own folder
 * (Android/data/<package>/files/depth/).
 */
object DepthPictures {
    class Pictures(val background: Bitmap?, val foreground: Bitmap?)

    private const val MAX_SIDE = 1920

    private fun folders(context: Context): List<File> = listOfNotNull(
        File(Environment.getExternalStorageDirectory(), "Glass/depth"),
        context.getExternalFilesDir(null)?.let { File(it, "depth") },
    )

    /** Heavy: call it off the main thread. */
    fun load(context: Context): Pictures {
        val dirs = folders(context)
        val back = dirs.firstNotNullOfOrNull { d ->
            listOf("background.jpg", "background.jpeg", "background.png")
                .map { File(d, it) }.firstOrNull { it.isFile }?.let(::decode)
        }
        val front = dirs.firstNotNullOfOrNull { d ->
            File(d, "foreground.png").takeIf { it.isFile }?.let(::decode)
        }
        return Pictures(back, front)
    }

    private fun decode(file: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.path, opts)
    } catch (e: Exception) {
        null
    }
}
