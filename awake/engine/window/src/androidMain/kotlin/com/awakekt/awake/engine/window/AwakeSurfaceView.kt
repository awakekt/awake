/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import android.content.Context
import android.hardware.input.InputManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.awakekt.awake.core.host.AndroidFrameLoop
import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.core.input.AndroidSoftKeyboardBridge
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.awakeClipboardText
import com.awakekt.awake.core.input.createAwakeInputConnection
import com.awakekt.awake.core.input.releaseGamepadButtons
import com.awakekt.awake.core.input.syncAwakeGamepadInput
import com.awakekt.awake.core.input.syncAwakeKeyInput
import com.awakekt.awake.core.input.syncAwakePointerInput
import com.awakekt.awake.core.input.syncAwakeTextShortcut
import com.awakekt.awake.engine.platform.WindowLifecycle
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets

/**
 * The Android window: a [SurfaceView] whose render thread drives [lifecycle] and whose touch, key,
 * controller and IME events feed its input. It reports the display's density and real dots per inch, and what
 * the system bars and a display cutout cover of it as the safe area. The backend creates its swapchain from the `Surface` it receives;
 * nothing here knows about a GPU API.
 */
@Suppress("TooManyFunctions") // One override per WindowLifecycle / SurfaceHolder callback.
class AwakeSurfaceView(
    context: Context,
    private val lifecycle: WindowLifecycle,
) : SurfaceView(context),
    SurfaceHolder.Callback2 {

    @Volatile
    private var running = false

    /** The engine outlives any one surface: backgrounding releases the surface, not the app. */
    private var engineCreated = false
    private var renderThread: Thread? = null

    private val input: Input get() = lifecycle.input

    private val softKeyboardBridge by lazy { AndroidSoftKeyboardBridge(this, input) }

    // A controller unplugged with a button held never sends the key-up.
    private val controllerRemoved = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit

        override fun onInputDeviceRemoved(deviceId: Int) = input.releaseGamepadButtons()

        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    // Only while a field holds focus. Always answering yes let Android raise the soft keyboard on
    // its own whenever the window gained focus (launch, resume, rotation) with nothing to type into.
    override fun onCheckIsTextEditor(): Boolean = input.textInputFocused

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection =
        createAwakeInputConnection(outAttrs, input)

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (engineCreated) {
            lifecycle.restoreSurface(holder.surface)
        } else {
            lifecycle.create(holder.surface)
            engineCreated = true
        }
        running = true
        renderThread = Thread({
            val mode = lifecycle.windowConfig?.frameRateMode ?: FrameRateMode.Auto
            while (running) {
                AndroidFrameLoop.tick(mode) { deltaTime ->
                    lifecycle.update(deltaTime.toFloat())
                    post {
                        softKeyboardBridge.syncSoftKeyboardVisibility()
                        softKeyboardBridge.syncClipboard()
                    }
                }
            }
        }, "AwakeSurfaceView-Render").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Read here, not once: a fold or a DeX move changes the density without a new view.
        val metrics = resources.displayMetrics
        lifecycle.setDensity(metrics.density)
        lifecycle.setDpi((metrics.xdpi + metrics.ydpi) / 2f)
        rootWindowInsets?.let(::reportSafeArea)
        lifecycle.resize(0, 0, width, height)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        reportSafeArea(insets)
        return super.onApplyWindowInsets(insets)
    }

    /**
     * What the window's system bars and display cutout cover of this view, in dp: the window's
     * insets less how far this view already sits inside them, so a view the app has laid out clear of
     * a bar reports nothing there.
     */
    private fun reportSafeArea(insets: WindowInsets) {
        val covered = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val at = IntArray(2).also(::getLocationInWindow)
        val window = rootView
        val density = resources.displayMetrics.density
        val left = (covered.left - at[0]).coerceAtLeast(0)
        val top = (covered.top - at[1]).coerceAtLeast(0)
        val right = (covered.right - (window.width - at[0] - width)).coerceAtLeast(0)
        val bottom = (covered.bottom - (window.height - at[1] - height)).coerceAtLeast(0)
        lifecycle.setSafeArea(DisplayInsets(left / density, top / density, right / density, bottom / density))
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
        renderThread = null
        input.clearKeys()
        input.releaseGamepadButtons()
        lifecycle.releaseSurface()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.getSystemService(InputManager::class.java)?.registerInputDeviceListener(controllerRemoved, null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.getSystemService(InputManager::class.java)?.unregisterInputDeviceListener(controllerRemoved)
        if (engineCreated) {
            lifecycle.dispose()
            engineCreated = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = event.syncAwakePointerInput(input) || super.onTouchEvent(event)

    // A controller's d-pad is its d-pad buttons and the arrow keys both, so `or` passes it to each bridge.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        event.syncAwakeTextShortcut(input) { context.awakeClipboardText() } ||
            event.syncAwakeGamepadInput(down = true, input) or event.syncAwakeKeyInput(down = true, input) ||
            super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        event.syncAwakeGamepadInput(down = false, input) or event.syncAwakeKeyInput(down = false, input) ||
            super.onKeyUp(keyCode, event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        event.syncAwakeGamepadInput(input) || super.onGenericMotionEvent(event)

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            input.clearKeys()
            input.releaseGamepadButtons()
        }
    }

    override fun surfaceRedrawNeeded(holder: SurfaceHolder) = Unit
}
