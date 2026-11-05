package com.fares.glass.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.view.KeyEvent as AndroidKey
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * The in-app picture picker of Simple mode. It lists the photos on the TV (MediaStore) as a grid you
 * move through with the remote, copies the chosen one into the app's own storage and calls [show]'s
 * callback so the home screen reloads it. No file has to be placed in any folder.
 *
 * Needs the photo permission (READ_EXTERNAL_STORAGE, or READ_MEDIA_IMAGES on Android 13+) in the manifest.
 */
object ImagePicker {
    enum class Kind { Background, Cutout }

    class Item(val uri: Uri, val id: Long)

    const val COLS = 4

    var visible by mutableStateOf(false)
        private set
    var kind by mutableStateOf(Kind.Background)
        private set
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set

    private var onDone: (() -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = LruCache<Long, Bitmap>(80)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Opens the picker. [done] runs after a picture was saved (or the default was restored). */
    fun show(context: Context, k: Kind, done: () -> Unit) {
        val app = context.applicationContext
        kind = k
        onDone = done
        index = 0
        items = emptyList()
        busy = true
        visible = true
        scope.launch {
            items = withContext(Dispatchers.IO) { query(app) }
            busy = false
        }
    }

    fun close() {
        visible = false
    }

    /** Keys while the picker is showing. Always consumes the key. */
    fun onKey(e: AndroidKey, context: Context): Boolean {
        if (e.action != AndroidKey.ACTION_DOWN) return true
        val total = items.size + 1 // cell 0 is "Default"
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_LEFT -> if (index > 0) index -= 1
            AndroidKey.KEYCODE_DPAD_RIGHT -> if (index < total - 1) index += 1
            AndroidKey.KEYCODE_DPAD_UP -> if (index >= COLS) index -= COLS
            AndroidKey.KEYCODE_DPAD_DOWN ->
                if (index + COLS < total) index += COLS
                else if (index / COLS < (total - 1) / COLS) index = total - 1
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (e.repeatCount == 0) choose(context)
            AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_MENU -> if (e.repeatCount == 0) close()
        }
        return true
    }

    private fun choose(context: Context) {
        if (busy) return
        val app = context.applicationContext
        val item = items.getOrNull(index - 1)
        val k = kind
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { if (item == null) clear(app, k) else save(app, item, k) }
            busy = false
            if (ok) {
                visible = false
                onDone?.invoke()
            } else {
                Toast.makeText(app, "Couldn't read that picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Files ----

    private fun file(context: Context, k: Kind): File =
        File(context.filesDir, if (k == Kind.Background) "depth_background.jpg" else "depth_cutout.png")

    /** The picture saved by the picker, or null when none was chosen (or the default was restored). */
    fun loadSaved(context: Context, k: Kind): Bitmap? = try {
        file(context, k).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (e: Exception) {
        null
    }

    private fun clear(context: Context, k: Kind): Boolean {
        file(context, k).delete()
        return true
    }

    private fun save(context: Context, item: Item, k: Kind): Boolean {
        return try {
            val bmp = decode(context, item.uri, 1920, 1080) ?: return false
            FileOutputStream(file(context, k)).use { out ->
                if (k == Kind.Background) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Reading photos ----

    private fun query(context: Context): List<Item> {
        val out = ArrayList<Item>()
        try {
            val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                base,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 300) {
                    val id = c.getLong(0)
                    out.add(Item(ContentUris.withAppendedId(base, id), id))
                }
            }
        } catch (e: Exception) {
            // No permission or no media provider: the grid only shows the "Default" cell.
        }
        return out
    }

    /** Decodes [uri] no bigger than [maxW] x [maxH] (keeps the aspect ratio). */
    private fun decode(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val s = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
        return if (s < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
        } else bmp
    }

    fun thumb(context: Context, item: Item): Bitmap? {
        cache.get(item.id)?.let { return it }
        val bmp = try {
            decode(context.applicationContext, item.uri, 336, 189)
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(item.id, bmp)
        return bmp
    }
}

private val CellW = 168.dp
private val CellH = 94.dp

/** The picker window: a grid of photos (first cell = the default), drawn above everything. */
@Composable
fun ImagePickerOverlay(backdrop: Backdrop) {
    val cols = ImagePicker.COLS
    val total = ImagePicker.items.size + 1
    val rowsTotal = (total + cols - 1) / cols
    val shownRows = 3
    val selRow = ImagePicker.index / cols
    val firstRow = (selRow - 1).coerceIn(0, (rowsTotal - shownRows).coerceAtLeast(0))
    val background = ImagePicker.kind == ImagePicker.Kind.Background

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Glass(backdrop, Modifier.width(760.dp), RoundedCornerShape(32.dp), tint = Color.White.copy(alpha = 0.10f)) {
            Column(Modifier.padding(24.dp)) {
                BasicText(
                    if (background) "Choose background" else "Choose cutout",
                    style = txt(Color.White, 22.sp, FontWeight.Bold),
                )
                BasicText(
                    if (background) "The first cell restores the default wallpaper" else "The first cell removes the cutout",
                    style = txt(Color.White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium),
                )
                Spacer(Modifier.height(14.dp))
                for (r in firstRow until minOf(firstRow + shownRows, rowsTotal)) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            if (i < total) PickerCell(i, i == ImagePicker.index, background) else Spacer(Modifier.size(CellW, CellH))
                        }
                    }
                }
                BasicText(
                    when {
                        ImagePicker.busy -> "Working\u2026"
                        ImagePicker.items.isEmpty() -> "No photos found on this TV. Copy a picture to its Pictures folder."
                        else -> "\u2190\u2191\u2192\u2193 move \u00B7 OK choose \u00B7 Back close"
                    },
                    style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun PickerCell(i: Int, selected: Boolean, background: Boolean) {
    val context = LocalContext.current
    val item = ImagePicker.items.getOrNull(i - 1)
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, spring(stiffness = 650f), label = "pickerCell")
    Box(
        Modifier
            .size(CellW, CellH)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (item == null) {
            BasicText(if (background) "Default" else "No cutout", style = txt(Color.White, 14.sp, FontWeight.SemiBold))
        } else {
            val bmp by produceState<ImageBitmap?>(null, item.id) {
                value = withContext(Dispatchers.IO) { ImagePicker.thumb(context, item) }?.asImageBitmap()
            }
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}
