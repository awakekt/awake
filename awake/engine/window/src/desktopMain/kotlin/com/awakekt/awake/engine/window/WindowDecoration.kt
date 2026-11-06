/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.math.roundToInt

/** `GLFW_DECORATED`, the window attribute and creation hint for the OS title bar and border. */
internal const val GLFW_DECORATED = 0x00020005

/**
 * Whether a desktop window has the OS title bar and border. Choose it when the window opens, with the
 * [runDesktopWindow] that takes one, or change it later with [setWindowDecoration].
 */
enum class WindowDecoration {
    /** The OS title bar and border: the player moves, resizes and closes the window with them. */
    System,

    /**
     * No title bar or border (undecorated, or frameless). The OS gives the player no way to move the
     * window, so a game that wants it movable draws its own drag area and drives a [WindowDrag];
     * resizing from the edges is not offered.
     */
    None,
}

/** [window]'s [WindowDecoration] now. Call on the frame loop's thread, as every GLFW window call. */
fun windowDecoration(window: Long): WindowDecoration {
    require(window != 0L) { "windowDecoration needs a created window, not a null handle." }
    val decorated = GlfwWindow.glfwGetWindowAttrib(window, GLFW_DECORATED) != 0
    return if (decorated) WindowDecoration.System else WindowDecoration.None
}

/**
 * Shows or hides [window]'s title bar and border. The content area keeps its size, and the
 * swapchain follows any framebuffer change as it does for a resize. A fullscreen window has none
 * either way, and leaves fullscreen with the decoration set here.
 */
fun setWindowDecoration(window: Long, decoration: WindowDecoration) {
    require(window != 0L) { "setWindowDecoration needs a created window, not a null handle." }
    GlfwWindow.glfwSetWindowDecorated(window, decoration == WindowDecoration.System)
}

/**
 * Moves the GLFW [window] while the player drags it by the game's own drag area, as a frameless
 * ([WindowDecoration.None]) window needs. See [WindowDrag].
 */
fun glfwWindowDrag(window: Long): WindowDrag {
    require(window != 0L) { "glfwWindowDrag needs a created window, not a null handle." }
    return WindowDrag(NativeGlfwDragWindow(window))
}

/**
 * Moves a window by the pointer. Call [update] once a frame on the frame loop's thread with
 * whether the player is dragging: the primary button went down on the game's drag area (its title
 * bar, say) and is still held. The window follows the pointer from where it was grabbed. A
 * fullscreen window does not move, and on Wayland, where no app may place its window, nothing moves.
 *
 * The engine decides nothing about where the drag area is: the game hit-tests its own UI.
 */
class WindowDrag internal constructor(private val window: GlfwDragWindow) {
    private var grabX = 0.0
    private var grabY = 0.0
    private var grabbed = false

    /** Moves the window after the pointer while [dragging]; letting go ends the drag. */
    fun update(dragging: Boolean) {
        if (!dragging || window.isFullscreen()) {
            grabbed = false
            return
        }
        val (x, y) = window.cursor()
        if (!grabbed) {
            grabX = x
            grabY = y
            grabbed = true
            return
        }
        val dx = (x - grabX).roundToInt()
        val dy = (y - grabY).roundToInt()
        if (dx == 0 && dy == 0) return
        val bounds = window.bounds()
        window.moveTo(bounds[0] + dx, bounds[1] + dy)
    }
}

/** The GLFW calls [WindowDrag] makes, behind a seam so a desktopTest can drive it without a window. */
internal interface GlfwDragWindow {
    /** Whether the window is on a monitor now. */
    fun isFullscreen(): Boolean

    /** The cursor in the window's content area, as `[x, y]` in screen coordinates. */
    fun cursor(): DoubleArray

    /** The window's position and size, as `[x, y, width, height]`. */
    fun bounds(): IntArray

    /** Moves the window's content area to [x], [y]. */
    fun moveTo(x: Int, y: Int)
}

/** [GlfwDragWindow] over the real [GlfwWindow] bindings. */
private class NativeGlfwDragWindow(private val window: Long) : GlfwDragWindow {
    override fun isFullscreen(): Boolean = GlfwWindow.glfwIsWindowFullscreen(window)

    override fun cursor(): DoubleArray = GlfwWindow.glfwGetCursorPos(window)

    override fun bounds(): IntArray = GlfwWindow.glfwGetWindowBounds(window)

    override fun moveTo(x: Int, y: Int) = GlfwWindow.glfwSetWindowPos(window, x, y)
}
