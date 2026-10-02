/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.host.NativeLibrary

/**
 * The desktop window and its input, through GLFW in `libawake-window`.
 *
 * Every `window` is the handle [glfwCreateWindow] returns. Call everything on the thread that
 * runs [glfwPollEvents]. A GPU backend creates its own surface from that handle; nothing here
 * knows about a GPU API.
 */
// One external function per GLFW call it binds; splitting it would only scatter the JNI surface.
@Suppress("TooManyFunctions")
object GlfwWindow {
    init {
        NativeLibrary.load("awake-window", GlfwWindow::class.java)
    }

    /** Must be called once before any other function here. Returns `false` on failure. */
    external fun glfwInit(): Boolean
    external fun glfwTerminate()
    external fun glfwWindowHint(hint: Int, value: Int)
    external fun glfwCreateWindow(width: Int, height: Int, title: String): Long
    external fun glfwDestroyWindow(window: Long)

    /**
     * Brings [window] to the front with OS input focus. A bare JVM process with no app bundle
     * can otherwise leave the window frontmost but unfocused, so keys and scroll never reach it.
     */
    external fun glfwFocusWindow(window: Long)
    external fun glfwWindowShouldClose(window: Long): Boolean
    external fun glfwPollEvents()
    external fun glfwGetFramebufferWidth(window: Long): Int
    external fun glfwGetFramebufferHeight(window: Long): Int

    /** Size in logical points; the framebuffer is a device-pixel multiple of it on HiDPI. */
    external fun glfwGetWindowWidth(window: Long): Int
    external fun glfwGetWindowHeight(window: Long): Int

    /** `GLFW_PRESS` (1) while [key] is held on [window], else `GLFW_RELEASE` (0). Polled per frame. */
    external fun glfwGetKey(window: Long, key: Int): Int
    external fun glfwGetMouseButton(window: Long, button: Int): Int

    /** The system clipboard's text, or `null` when it holds no text. */
    external fun glfwGetClipboardString(window: Long): String?
    external fun glfwSetClipboardString(window: Long, text: String)

    /** Cursor position in logical points, as `[x, y]`. */
    external fun glfwGetCursorPos(window: Long): DoubleArray

    /**
     * Starts accumulating [window]'s scroll ticks for the `glfwConsumeScroll*` reads. Call once,
     * after [glfwCreateWindow] and before the first [glfwPollEvents].
     */
    external fun glfwSetScrollCallback(window: Long)

    /** Vertical scroll since the last call, then reset to 0. */
    external fun glfwConsumeScrollDeltaY(window: Long): Double

    /** Horizontal scroll since the last call, then reset to 0. */
    external fun glfwConsumeScrollDeltaX(window: Long): Double

    /**
     * What sent the scroll since the last call, then reset: the ordinal of
     * `com.awakekt.awake.core.input.ScrollSource`. Only macOS can tell a trackpad from a wheel.
     */
    external fun glfwConsumeScrollSource(window: Long): Int
    external fun glfwSetCursorShape(window: Long, shape: Int)
    external fun glfwGetWindowAttrib(window: Long, attrib: Int): Int
}
