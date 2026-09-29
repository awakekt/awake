/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.awakekt.awake.core.host.AndroidFrameLoop
import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.core.input.AndroidSoftKeyboardBridge
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.createAwakeInputConnection
import com.awakekt.awake.core.input.syncAwakeKeyInput
import com.awakekt.awake.core.input.syncAwakePointerInput

@Suppress("TooManyFunctions") // One override per WindowLifecycle / SurfaceHolder callback.
class VulkanView(
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

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onCheckIsTextEditor(): Boolean = true

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
                    post { softKeyboardBridge.syncSoftKeyboardVisibility() }
                }
            }
        }, "VulkanView-Render").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Read here, not once: a fold or a DeX move changes the density without a new view.
        lifecycle.setDensity(resources.displayMetrics.density)
        lifecycle.resize(0, 0, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
        renderThread = null
        input.clearKeys()
        lifecycle.releaseSurface()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (engineCreated) {
            lifecycle.dispose()
            engineCreated = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = event.syncAwakePointerInput(input) || super.onTouchEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = event.syncAwakeKeyInput(down = true, input) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = event.syncAwakeKeyInput(down = false, input) || super.onKeyUp(keyCode, event)

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            input.clearKeys()
        }
    }

    override fun surfaceRedrawNeeded(holder: SurfaceHolder) = Unit
}
