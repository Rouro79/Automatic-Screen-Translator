package com.example.comictranslate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

@SuppressLint("ClickableViewAccessibility")
object OverlayManager {

    private var overlayComposeView: ComposeView? = null
    private val translatedTextsState = mutableStateOf<List<TranslatedText>>(emptyList())

    private var touchDetectorView: View? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null

    // Handler + Runnable for auto-clear scheduling
    private val handler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null

    // Config: whether overlay should persist until the user explicitly taps it.
    // Set true if you want the overlay to remain until tap (default true per your comment).
    var persistUntilTap: Boolean = true

    // TTL when persistUntilTap == false (ms). If you want auto-dismiss, set persistUntilTap=false and adjust TTL.
    var overlayTTL: Long = 1200L

    fun hasOverlays(): Boolean = overlayComposeView?.parent != null

    fun showOverlays(context: Context, newTexts: List<TranslatedText>) {

        // update backing state (Compose will recompose)
        translatedTextsState.value = newTexts

        // if we are persisting until tap, cancel any scheduled clear
        cancelScheduledClear()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (overlayComposeView == null) {

            lifecycleOwner = CustomLifecycleOwner().apply {
                performRestore(null)
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            }

            overlayComposeView = ComposeView(context).apply {
                // Attach lifecycle/saved state owners so Compose doesn't crash inside WindowManage
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
        }

        // Set Compose content every time (recompose on state change)
        overlayComposeView?.setContent {
            TranslationOverlayView(translatedTexts = translatedTextsState.value)
        }

        if (!hasOverlays()) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            runCatching { windowManager.addView(overlayComposeView, params) }
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        // Ensure touch detector is present so user can tap to clear
        showTouchDetector(context)

        // If we DO want auto-clear, schedule it using the handler; otherwise, do nothing (persist until tap)
        if (!persistUntilTap) scheduleAutoClear(context)
    }

    private fun scheduleAutoClear(context: Context) {
        cancelScheduledClear()
        val r = Runnable {
            // double-check timestamp/visibility before clearing
            try {
                clearOverlays(context)
            } catch (_: Exception) {}
        }
        clearRunnable = r
        handler.postDelayed(r, overlayTTL)
    }

    private fun cancelScheduledClear() {
        clearRunnable?.let {
            handler.removeCallbacks(it)
            clearRunnable = null
        }
    }

    private fun showTouchDetector(context: Context) {
        if (touchDetectorView != null) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        touchDetectorView = View(context).apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    clearOverlays(context)
                    true
                } else {
                    false
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // This view must receive touch events, so DO NOT use FLAG_NOT_TOUCHABLE here.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        runCatching { windowManager.addView(touchDetectorView, params) }
    }

    fun clearOverlays(context: Context) {
        cancelScheduledClear()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        translatedTextsState.value = emptyList()

        overlayComposeView?.let {
            runCatching { windowManager.removeView(it) }
        }

        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        overlayComposeView = null
        lifecycleOwner = null

        touchDetectorView?.let {
            runCatching { windowManager.removeView(it) }
        }
        touchDetectorView = null
    }
}

/**
 * Minimal SavedStateRegistryOwner + LifecycleOwner used for ComposeView when attached via WindowManager.
 * This avoids the "ViewTreeLifecycleOwner not found" crashes.
 */
private class CustomLifecycleOwner : SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}