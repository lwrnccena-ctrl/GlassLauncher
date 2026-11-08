package com.fares.glass.ui

import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * The tvOS-style zoom when an app is opened from the recent apps stack and when you come back:
 *  - opening: the chosen card grows to fill the screen, the other cards fade, then the app starts.
 *  - coming back: the card shrinks back into the stack, and the home screen zooms in behind it.
 * The "Dock & icons > Motion" setting set to Off turns all of it into instant changes.
 */
object RecentsMotion {
    const val OPEN_MS = 380L

    /** Key (package name) of the app being opened from the stack, or null. */
    var opening by mutableStateOf<Any?>(null)

    /** Goes up every time Glass comes back to the screen; the home screen plays its zoom-in. */
    var returnTick by mutableIntStateOf(0)

    private var away = false

    fun spec(ms: Int): AnimationSpec<Float> =
        if (DockSettings.anim == 0) snap() else tween(ms, easing = FastOutSlowInEasing)

    /** Plays the zoom, then runs [launch] (your normal "start this app" code). */
    suspend fun openThen(key: Any, launch: () -> Unit) {
        opening = key
        delay(if (DockSettings.anim == 0) 0L else OPEN_MS)
        try {
            launch()
        } catch (e: Exception) {
            opening = null
        }
    }

    /** Pass this as the second argument of startActivity so the system adds no animation of its own. */
    fun fadeOptions(context: Context): Bundle? =
        ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()

    /** Call from MainActivity.onPause. */
    fun onPause() {
        away = true
    }

    /** Call from MainActivity.onResume. */
    fun onResume() {
        if (!away) return
        away = false
        opening = null
        returnTick++
    }
}

/**
 * Put this on the root of the home screen. Each time Glass comes back, the home screen
 * starts a little bigger and settles to normal size while fading in, like the app shrinking away.
 */
@Composable
fun Modifier.glassReturnReveal(): Modifier {
    val p = remember { Animatable(1f) }
    val tick = RecentsMotion.returnTick
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, RecentsMotion.spec(480))
    }
    return this.graphicsLayer {
        val s = 1.10f - 0.10f * p.value
        scaleX = s
        scaleY = s
        alpha = 0.35f + 0.65f * p.value
    }
}
