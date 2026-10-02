/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ScrollSource
import com.awakekt.awake.engine.window.GlfwWindow

internal const val GLFW_PRESS = 1
internal const val GLFW_FOCUSED = 0x00020001

/**
 * Seam between [pollGlfwInput]/[pollGlfwTextInput]'s translation logic and the real GLFW
 * native bindings ([GlfwWindow]) -- exists so a desktopTest can fake "this key is down this
 * frame" and assert the right [com.awakekt.awake.core.input.Input] calls fire,
 * without a live window. A real GLFW window (and therefore OS-level focus behavior) still
 * can't be faked this way -- this only covers the keycode/repeat/shift translation, not
 * whether the OS actually delivers key events to the window in the first place.
 */
interface GlfwWindowInput {
    fun isKeyDown(glfwKey: Int): Boolean
    fun isMouseButtonDown(glfwButton: Int): Boolean
    fun cursorX(): Double
    fun cursorY(): Double
    fun framebufferScaleX(): Float
    fun framebufferScaleY(): Float
    fun consumeScrollDeltaY(): Double
    /** Sideways scroll since the last poll; 0 for a reader with no horizontal axis. */
    fun consumeScrollDeltaX(): Double = 0.0

    /** What sent the scroll since the last poll; [ScrollSource.Unknown] where the platform cannot tell. */
    fun consumeScrollSource(): ScrollSource = ScrollSource.Unknown
    fun isFocused(): Boolean = true

    /**
     * The system clipboard's text, or `null` when it holds none. Setting it replaces the
     * clipboard; setting `null` leaves it alone.
     */
    var clipboardText: String?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) = Unit
}

private class RealGlfwWindowInput(private val window: Long) : GlfwWindowInput {
    override fun isKeyDown(glfwKey: Int): Boolean = GlfwWindow.glfwGetKey(window, glfwKey) == GLFW_PRESS
    override fun isMouseButtonDown(glfwButton: Int): Boolean = GlfwWindow.glfwGetMouseButton(window, glfwButton) == GLFW_PRESS
    override fun cursorX(): Double = GlfwWindow.glfwGetCursorPos(window)[0]
    override fun cursorY(): Double = GlfwWindow.glfwGetCursorPos(window)[1]
    override fun consumeScrollDeltaY(): Double = GlfwWindow.glfwConsumeScrollDeltaY(window)
    override fun consumeScrollDeltaX(): Double = GlfwWindow.glfwConsumeScrollDeltaX(window)
    override fun consumeScrollSource(): ScrollSource =
        ScrollSource.entries.getOrElse(GlfwWindow.glfwConsumeScrollSource(window)) { ScrollSource.Unknown }
    override fun isFocused(): Boolean = GlfwWindow.glfwGetWindowAttrib(window, GLFW_FOCUSED) != 0
    override var clipboardText: String?
        get() = GlfwWindow.glfwGetClipboardString(window)
        set(value) {
            // The JNI wrapper rejects an empty string, and the UI never answers with one.
            if (!value.isNullOrEmpty()) GlfwWindow.glfwSetClipboardString(window, value)
        }

    override fun framebufferScaleX(): Float = framebufferScale(window).first
    override fun framebufferScaleY(): Float = framebufferScale(window).second
}

private fun framebufferScale(window: Long): Pair<Float, Float> {
    val windowWidth = GlfwWindow.glfwGetWindowWidth(window)
    val windowHeight = GlfwWindow.glfwGetWindowHeight(window)
    val framebufferWidth = GlfwWindow.glfwGetFramebufferWidth(window)
    val framebufferHeight = GlfwWindow.glfwGetFramebufferHeight(window)
    val scaleX = if (windowWidth != 0) framebufferWidth.toFloat() / windowWidth else 1f
    val scaleY = if (windowHeight != 0) framebufferHeight.toFloat() / windowHeight else 1f
    return scaleX to scaleY
}

fun glfwWindowInput(window: Long): GlfwWindowInput = RealGlfwWindowInput(window)
