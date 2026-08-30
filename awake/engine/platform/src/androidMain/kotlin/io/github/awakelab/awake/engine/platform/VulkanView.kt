/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.platform

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import io.github.awakelab.awake.core.host.AndroidFrameLoop
import io.github.awakelab.awake.core.input.AndroidSoftKeyboardBridge
import io.github.awakelab.awake.core.input.Input
import io.github.awakelab.awake.core.input.createAwakeInputConnection
import io.github.awakelab.awake.core.input.syncAwakeKeyInput
import io.github.awakelab.awake.core.input.syncAwakePointerInput

class VulkanView(
    context: Context,
    private val lifecycle: WindowLifecycle,
) : SurfaceView(context),
    SurfaceHolder.Callback2 {

    @Volatile
    private var running = false
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
        lifecycle.create(holder.surface)
        running = true
        renderThread = Thread({
            while (running) {
                AndroidFrameLoop.tick { deltaTime ->
                    lifecycle.update(deltaTime.toFloat())
                    post { softKeyboardBridge.syncSoftKeyboardVisibility() }
                }
            }
        }, "VulkanView-Render").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        lifecycle.resize(0, 0, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
        renderThread = null
        input.clearKeys()
        lifecycle.dispose()
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
