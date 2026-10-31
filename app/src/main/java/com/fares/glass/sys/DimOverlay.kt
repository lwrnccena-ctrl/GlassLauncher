package com.fares.glass.sys

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Full-screen translucent black layer that dims everything (Android 11 has no Extra Dim).
 * Not touchable and not focusable, so the remote keeps working normally.
 * Must be called from the main thread.
 */
object DimOverlay {
    private var view: View? = null

    /** Compose reads this, so the Screen dim tile updates by itself. */
    var on by mutableStateOf(false)
        private set

    fun show(ctx: Context, alpha: Float = 0.55f): Boolean {
        if (view != null) return true
        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = View(app).apply {
            setBackgroundColor(0xFF000000.toInt())
            this.alpha = alpha
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching {
            wm.addView(v, lp)
            view = v
            on = true
        }.isSuccess
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        val wm = ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(v) }
        view = null
        on = false
    }
}
