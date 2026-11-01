package com.fares.glass.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Calendar

/** Receives the still pictures the room sends back (used for the blur). */
class RoomBridge(private val onShot: (ImageBitmap) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onSnapshot(base64: String) {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        main.post { onShot(bmp.asImageBitmap()) }
    }
}

/**
 * The live 3D room (assets/room/room.html).
 * focusX / focusY: -1..1, where the selected app is (moves the camera).
 * sky: CLEAR, PARTLY, CLOUDY, FOG, RAIN, STORM or SNOW.
 * active: false pauses the 3D rendering (when the blur fully covers it).
 * Change snapshotRequest to ask for a fresh still picture.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RoomWallpaperView(
    focusX: Float,
    focusY: Float,
    sky: String,
    active: Boolean,
    snapshotRequest: Int,
    onSnapshot: (ImageBitmap) -> Unit,
    modifier: Modifier = Modifier,
    sunrise: Float = 6f,
    sunset: Float = 18f,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val shotCallback by rememberUpdatedState(onSnapshot)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                setBackgroundColor(0xFF0A0C14.toInt())
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                addJavascriptInterface(RoomBridge { shotCallback(it) }, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready = true
                    }
                }
                loadUrl("file:///android_asset/room/room.html")
                web = this
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() },
    )

    fun run(js: String) {
        web?.evaluateJavascript(js, null)
    }

    // Camera, sky, sunrise and sunset.
    LaunchedEffect(ready, focusX, focusY, sky, sunrise, sunset) {
        if (ready) run("glassRoom.set({px:$focusX,py:$focusY,sky:'$sky',rise:$sunrise,set:$sunset})")
    }
    // The clock: the room reads the real time of day, and it is refreshed every 30 seconds.
    LaunchedEffect(ready) {
        while (ready) {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
            run("glassRoom.set({hour:$hour})")
            delay(30_000L)
        }
    }
    LaunchedEffect(ready, active) {
        if (ready) run("glassRoom.pause(${!active})")
    }
    LaunchedEffect(ready, snapshotRequest) {
        if (ready) {
            delay(250) // let the first frame be drawn
            run("glassRoom.snapshot()")
        }
    }
}
