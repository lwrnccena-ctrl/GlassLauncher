package com.fares.glass

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fares.glass.data.AppRepository
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.SettingsShortcuts
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.WifiController
import com.fares.glass.ui.Backdrop
import com.fares.glass.ui.CcCloseMs
import com.fares.glass.ui.CcOpenMs
import com.fares.glass.ui.CcPage
import com.fares.glass.ui.CcReveal
import com.fares.glass.ui.ControlCenter
import com.fares.glass.ui.PanelEnd
import com.fares.glass.ui.PanelTop
import com.fares.glass.ui.Typo

/**
 * Shows the Control Center over any app when a trigger button on the remote is pressed.
 * Needs to be enabled as an accessibility service (that is what lets it hear remote keys in other apps).
 */
class GlassOverlayService : AccessibilityService() {

    companion object {
        private const val TAG = "GlassOverlay"

        /**
         * Buttons that open the overlay.
         * Xiaomi remote: gear button = SETTINGS, yellow button = backup in case the TV keeps the gear for itself.
         * Other choices: KEYCODE_PROG_RED, KEYCODE_PROG_GREEN, KEYCODE_PROG_BLUE, KEYCODE_INFO, KEYCODE_GUIDE.
         */
        private val TRIGGER_KEYS = setOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,      // gear button
            KeyEvent.KEYCODE_PROG_YELLOW,   // yellow button, backup
        )

        /** Button that also CLOSES the overlay while it is open (the yellow button toggles it). */
        private const val CLOSE_KEY = KeyEvent.KEYCODE_PROG_YELLOW

        /** Logs every remote key to logcat so you can find your button's code: adb logcat -s GlassOverlay */
        private const val LOG_KEYS = true

        /** Panel background (ARGB). Lower the first two hex digits (E0) for more see-through. */
        private const val PANEL_COLOR = 0xE02E2A44.toInt()

        /** The window shrinks back to 1x1 this long after closing (must be longer than CcCloseMs). */
        private const val HIDE_DELAY_MS = 300L

        @Volatile private var instance: GlassOverlayService? = null

        /** Called by the launcher when it comes to the front (for example Home pressed). */
        fun dismiss() { instance?.dismissOverlay() }
    }

    private lateinit var wm: WindowManager
    private lateinit var cc: ControlCenter
    private lateinit var root: KeyRoot
    private lateinit var owner: OverlayOwner
    private val backdrop = Backdrop()
    private val handler = Handler(Looper.getMainLooper())
    private val swallowed = HashSet<Int>()
    private var hideTask: Runnable? = null
    private var visible by mutableStateOf(false)
    private var shown = false
    private var attached = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (attached) return
        instance = this
        Typo.load(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner = OverlayOwner()

        // A flat translucent colour replaces the blurred wallpaper (there is no wallpaper over other apps).
        val bg = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(PANEL_COLOR) }
        backdrop.frosted = bg.asImageBitmap()

        cc = ControlCenter(
            this, AppRepository(this), WifiController(this), BluetoothController(this),
            SystemInfo(this), SettingsShortcuts(this),
            onOpen = { intent ->
                try {
                    startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w(TAG, "Can't open $intent", e)
                }
                cc.close()
            },
            onRequestPermissions = { /* permissions are granted once through ADB (scripts/install.sh) */ },
            onNextWallpaper = { /* the wallpaper only exists on the home screen */ },
            onClose = { hide() },
        )

        root = KeyRoot(this) { cc.onKey(it) }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }
        val compose = ComposeView(this).apply { setContent { Overlay() } }
        root.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Starts as a 1x1 invisible window so the first press opens quickly.
        wm.addView(root, hiddenParams())
        attached = true
    }

    // ---- Keys ----

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (LOG_KEYS && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
        }
        if (!attached) return false
        // The key-up of the press that opened (or closed) the overlay.
        if (event.action == KeyEvent.ACTION_UP && swallowed.remove(event.keyCode)) return true

        // Overlay open: the yellow button closes it again (it toggles).
        // The service hears the key before the overlay window does, so this works while the overlay has focus.
        if (shown && event.keyCode == CLOSE_KEY) {
            // Not while the Wi-Fi password is being typed.
            if (cc.typing) return false
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) cc.close() // runs onClose -> hide()
                swallowed.add(event.keyCode)
            }
            return true
        }

        // Overlay open: let the other keys reach the overlay window. Launcher in front: it has its own Control Center.
        if (shown || MainActivity.isForeground || event.keyCode !in TRIGGER_KEYS) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) show()
            swallowed.add(event.keyCode)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---- Show / hide ----

    private fun show() {
        if (shown) return
        hideTask?.let(handler::removeCallbacks)
        hideTask = null
        shown = true
        cc.open(CcPage.Main)
        wm.updateViewLayout(root, shownParams())
        root.requestFocus()
        visible = true
    }

    /** Only called through cc.close() (Back, Menu, yellow, or dismissOverlay). */
    private fun hide() {
        if (!shown) return
        shown = false
        visible = false
        // Wait for the close animation, then shrink the window back to 1x1 so it doesn't cover the video.
        val task = Runnable { if (!shown && attached) wm.updateViewLayout(root, hiddenParams()) }
        hideTask = task
        handler.postDelayed(task, HIDE_DELAY_MS)
    }

    fun dismissOverlay() {
        if (attached && shown) cc.close()
    }

    private fun hiddenParams() = WindowManager.LayoutParams(
        1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun shownParams() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    // ---- UI ----

    @Composable
    private fun Overlay() {
        // Same scaling as MainActivity: the layout is designed for a 960 dp wide screen.
        val width = resources.displayMetrics.widthPixels
        val font = LocalDensity.current.fontScale
        CompositionLocalProvider(LocalDensity provides Density(width / 960f, font)) {
            // 0 -> 1 when opening, 1 -> 0 when closing. The panel reads it only while drawing.
            val progress = animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(if (visible) CcOpenMs else CcCloseMs, easing = LinearEasing),
                label = "ccReveal",
            )
            val reveal = remember { CcReveal(progress = { progress.value }, opening = { visible }) }

            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                }
                // Stays composed until the close animation has finished.
                if (visible || progress.value > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = PanelTop, end = PanelEnd),
                    ) {
                        cc.Panel(backdrop, "Home screen", reveal, Modifier)
                    }
                }
                cc.PasswordLayer(backdrop) { root.requestFocus() }
            }
        }
    }

    override fun onDestroy() {
        instance = null
        if (attached) {
            attached = false
            hideTask?.let(handler::removeCallbacks)
            runCatching { wm.removeView(root) }
            owner.destroy()
        }
        super.onDestroy()
    }
}

/** Root view of the overlay window: sends remote keys to the Control Center first. */
private class KeyRoot(context: Context, private val onKey: (KeyEvent) -> Boolean) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = onKey(event) || super.dispatchKeyEvent(event)
}

/** Compose needs a lifecycle, saved-state and view-model owner; a service has none, so we provide one. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
