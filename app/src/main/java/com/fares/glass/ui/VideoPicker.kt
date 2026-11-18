package com.fares.glass.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picker for the video wallpaper, built into Glass (no file manager): a grid of the videos on this TV
 * with a picture from each one. The first card is the built-in video.
 * Arrows move, OK chooses, Back closes.
 * HomeScreen sends keys to [onKey] and draws [VideoPickerOverlay] while [visible] is true.
 */
object VideoPicker {
    /** uri = null means the built-in video. */
    class Item(val uri: Uri?, val title: String, val info: String, val toggle: Boolean = false)

    const val COLS = 3

    var visible by mutableStateOf(false)
        private set
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Pictures of the videos near the selection (item number -> picture). Far ones are dropped to save memory. */
    val thumbs = mutableStateMapOf<Int, ImageBitmap>()
    private val pending = HashSet<Int>()

    private var onPicked: ((Uri?) -> Unit)? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Opens the grid. [onPicked] gets the chosen video, or null for the built-in one. */
    fun show(context: Context, onPicked: (Uri?) -> Unit) {
        this.onPicked = onPicked
        val appContext = context.applicationContext
        this.appContext = appContext
        val builtIn = Item(null, "Built-in video", "Comes with Glass")
        // The second card is a switch (Smooth loop), not a video: OK flips it and the grid stays open.
        val smooth = Item(null, "Smooth loop", "Fades the end into the start", toggle = true)
        val fixed = listOf(builtIn, smooth)
        thumbs.clear()
        pending.clear()
        items = fixed
        index = 0
        loading = true
        visible = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { query(appContext) }
            items = fixed + found
            loading = false
            val current = VideoSettings.customUri
            if (current != null) {
                index = items.indexOfFirst { it.uri?.toString() == current }.coerceAtLeast(0)
            }
        }
    }

    private fun query(context: Context): List<Item> = try {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val out = ArrayList<Item>()
        context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            while (c.moveToNext() && out.size < 200) {
                val id = c.getLong(idCol)
                val sec = c.getLong(durCol) / 1000
                val info = "%d:%02d \u00B7 %d\u00D7%d".format(sec / 60, sec % 60, c.getInt(wCol), c.getInt(hCol))
                out.add(Item(ContentUris.withAppendedId(collection, id), c.getString(nameCol) ?: "Video", info))
            }
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    /** Loads the pictures of items [from]..[to] (the ones on screen) and drops the ones far away. */
    fun loadThumbs(context: Context, from: Int, to: Int) {
        val appContext = context.applicationContext
        thumbs.keys.filter { it < from - COLS * 2 || it > to + COLS * 2 }.forEach { thumbs.remove(it) }
        for (i in from..minOf(to, items.lastIndex)) {
            val item = items[i]
            if (i < 0 || item.toggle || thumbs.containsKey(i) || !pending.add(i)) continue
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { thumbnail(appContext, item.uri) }
                pending.remove(i)
                if (bmp != null && visible) thumbs[i] = bmp
            }
        }
    }

    /** One picture from the video. uri = null is the built-in video. */
    private fun thumbnail(context: Context, uri: Uri?): ImageBitmap? {
        var bmp: Bitmap? = null
        if (uri != null && Build.VERSION.SDK_INT >= 29) {
            bmp = try { context.contentResolver.loadThumbnail(uri, Size(320, 180), null) } catch (e: Exception) { null }
        }
        if (bmp == null) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri ?: Uri.parse("android.resource://${context.packageName}/${R.raw.wallpaper_video}"))
                bmp = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { Bitmap.createScaledBitmap(it, 320, 180, true) }
            } catch (e: Exception) {
                bmp = null
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
        }
        return bmp?.asImageBitmap()
    }

    /** Keys while the grid is showing. */
    fun onKey(e: AndroidKey): Boolean {
        val down = e.action == AndroidKey.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_LEFT -> if (down && index % COLS > 0) index -= 1
            AndroidKey.KEYCODE_DPAD_RIGHT -> if (down && index % COLS < COLS - 1 && index + 1 < items.size) index += 1
            AndroidKey.KEYCODE_DPAD_UP -> if (down && index - COLS >= 0) index -= COLS
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) {
                val next = index + COLS
                index = when {
                    next <= items.lastIndex -> next
                    index / COLS < items.lastIndex / COLS -> items.lastIndex
                    else -> index
                }
            }
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> if (first) {
                val chosen = items.getOrNull(index)
                if (chosen?.toggle == true) {
                    // Smooth loop on / off. The wallpaper restarts with the new setting; the grid stays open.
                    appContext?.let { VideoSettings.setSmoothLoop(it, !VideoSettings.smoothLoop) }
                } else {
                    close()
                    onPicked?.invoke(chosen?.uri)
                }
            }
            AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_MENU -> if (first) close()
        }
        return true
    }

    private fun close() {
        visible = false
        thumbs.clear()
        pending.clear()
    }
}

private val PickerInk = Color(0xFF111114)

/** The grid itself, drawn above everything (like the other pickers). */
@Composable
fun VideoPickerOverlay(backdrop: Backdrop) {
    val context = LocalContext.current
    val items = VideoPicker.items
    val sel = VideoPicker.index
    val cols = VideoPicker.COLS
    val rowsShown = 2
    val totalRows = (items.size + cols - 1) / cols
    val selRow = sel / cols
    // The selected row is the first or the second of the two rows on screen.
    val startRow = (if (selRow == 0) 0 else selRow - 1).coerceAtMost((totalRows - rowsShown).coerceAtLeast(0))
    LaunchedEffect(startRow, items.size) {
        VideoPicker.loadThumbs(context, startRow * cols, (startRow + rowsShown) * cols - 1)
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Glass(backdrop, Modifier.width(640.dp), RoundedCornerShape(32.dp), tint = Color.White.copy(alpha = 0.10f)) {
            Column(Modifier.padding(24.dp)) {
                BasicText("Choose a video wallpaper", style = txt(Color.White, 22.sp, FontWeight.Bold))
                BasicText(
                    "A landscape 1080p video works best. Arrows move \u00B7 OK chooses \u00B7 Back closes",
                    style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium),
                )
                Spacer(Modifier.height(14.dp))
                for (r in startRow until startRow + rowsShown) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            val item = items.getOrNull(i)
                            if (item != null) {
                                VideoCard(
                                    item, VideoPicker.thumbs[i], i == sel,
                                    !item.toggle && item.uri?.toString() == VideoSettings.customUri,
                                    Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (VideoPicker.loading) {
                    BasicText("Looking for videos\u2026", Modifier.padding(top = 6.dp), style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium))
                } else if (items.size <= 1) {
                    BasicText(
                        "No videos found on this TV. Copy a video into its Movies or Download folder, then open this again.",
                        Modifier.padding(top = 6.dp),
                        style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium), maxLines = 3,
                    )
                }
            }
        }
    }
}

/** One video: its picture, name and length. The selected one is white and a little bigger. */
@Composable
private fun VideoCard(item: VideoPicker.Item, thumb: ImageBitmap?, focused: Boolean, inUse: Boolean, modifier: Modifier) {
    val fg = if (focused) PickerInk else Color.White
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer {
                val s = if (focused) 1.05f else 1f
                scaleX = s
                scaleY = s
            }
            .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f), shape)
            .padding(6.dp),
    ) {
        val on = VideoSettings.smoothLoop
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                .background(
                    if (item.toggle && on) Brush.linearGradient(listOf(Color(0xFF1E9E48), Color(0xFF30D158)))
                    else Brush.linearGradient(listOf(Color(0xFF2B2E35), Color(0xFF4A4F5A))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.toggle) {
                BasicText(if (on) "On" else "Off", style = txt(Color.White, 24.sp, FontWeight.Bold))
            } else if (thumb != null) {
                Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(item.title, Modifier.weight(1f), style = txt(fg, 12.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (inUse) BasicText("In use", style = txt(if (focused) Color(0xFF1E9E48) else Color(0xFF30D158), 11.sp, FontWeight.SemiBold), maxLines = 1)
        }
        BasicText(item.info, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp), style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
    }
}
