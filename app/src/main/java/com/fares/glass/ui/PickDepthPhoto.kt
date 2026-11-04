package com.fares.glass.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Saves the picked picture into the depth folder.
 * Background -> background.jpg (a png is converted). Cutout -> foreground.png (keeps transparency).
 */
private fun saveDepthPicture(context: Context, uri: Uri, cutout: Boolean): Boolean {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3840) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false

        val dir = File(context.getExternalFilesDir(null), "depth").apply { mkdirs() }
        val name = if (cutout) "foreground" else "background"
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use {
            if (cutout) bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        dir.listFiles()?.filter { it.nameWithoutExtension == name && it.extension != "tmp" }
            ?.forEach { it.delete() }
        tmp.renameTo(File(dir, if (cutout) "foreground.png" else "background.jpg"))
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun rememberDepthPicker(cutout: Boolean, onPicked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    if (LocalActivityResultRegistryOwner.current == null) {
        return { Toast.makeText(context, "Picking a picture isn't available here", Toast.LENGTH_LONG).show() }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (saveDepthPicture(context, uri, cutout)) onPicked()
            else Toast.makeText(context, "Could not save the picture", Toast.LENGTH_SHORT).show()
        }
    }
    return {
        try {
            launcher.launch(if (cutout) arrayOf("image/png") else arrayOf("image/jpeg", "image/png"))
        } catch (e: Exception) {
            Toast.makeText(context, "No file picker on this TV", Toast.LENGTH_LONG).show()
        }
    }
}

/** Picker for the background photo (jpg or png). */
@Composable
fun rememberDepthPhotoPicker(onPicked: () -> Unit): () -> Unit = rememberDepthPicker(false, onPicked)

/** Picker for the transparent cutout (png only). */
@Composable
fun rememberDepthCutoutPicker(onPicked: () -> Unit): () -> Unit = rememberDepthPicker(true, onPicked)
