/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * The GLFW [window]'s [Fullscreen]: borderless fullscreen on the monitor under the window's centre,
 * at that monitor's current video mode, so entering never switches the display mode. Leaving puts
 * the window back where it was and at the size it had. The swapchain follows through the window's
 * framebuffer size, as it does for any resize.
 *
 * [window] is the handle `runDesktopWindow` passes to its `onCreate`. Call this, and every
 * [Fullscreen] member, on the thread that runs the frame loop: GLFW allows window calls only there.
 * [desktopHostActions] with a window fills [HostActions.fullscreen] with it.
 */
fun glfwFullscreen(window: Long): Fullscreen {
    require(window != 0L) { "glfwFullscreen needs a created window, not a null handle." }
    return GlfwFullscreen(NativeGlfwFullscreenWindow(window))
}

/**
 * The GLFW calls [GlfwFullscreen] makes, behind a seam so a desktopTest can check the bounds it
 * keeps without a live window, as [GlfwWindowInput] does for input.
 */
internal interface GlfwFullscreenWindow {
    /** Whether the window is on a monitor now. */
    fun isFullscreen(): Boolean

    /** The windowed position and size, as `[x, y, width, height]`. */
    fun bounds(): IntArray

    /** Moves the window onto its monitor; false when there is no monitor or video mode to use. */
    fun goFullscreen(): Boolean

    /** Takes the window off its monitor, to [x], [y] sized [width] by [height]. */
    fun goWindowed(x: Int, y: Int, width: Int, height: Int)
}

/** [GlfwFullscreenWindow] over the real [GlfwWindow] bindings. */
private class NativeGlfwFullscreenWindow(private val window: Long) : GlfwFullscreenWindow {
    override fun isFullscreen(): Boolean = GlfwWindow.glfwIsWindowFullscreen(window)

    override fun bounds(): IntArray = GlfwWindow.glfwGetWindowBounds(window)

    override fun goFullscreen(): Boolean = GlfwWindow.glfwSetWindowFullscreen(window)

    override fun goWindowed(x: Int, y: Int, width: Int, height: Int) =
        GlfwWindow.glfwSetWindowWindowed(window, x, y, width, height)
}

/**
 * [Fullscreen] over a [GlfwFullscreenWindow]. It keeps the windowed bounds from when it was made
 * and from each [enter], so [exit] restores them; a minimised window's empty size is never kept.
 */
internal class GlfwFullscreen(private val window: GlfwFullscreenWindow) : Fullscreen {
    private var windowed: IntArray? = if (window.isFullscreen()) null else usable(window.bounds())

    override val isActive: Boolean
        get() = window.isFullscreen()

    override fun enter(): Boolean {
        if (isActive) return true
        usable(window.bounds())?.let { windowed = it }
        return window.goFullscreen()
    }

    override fun exit() {
        if (!isActive) return
        val (x, y, width, height) = windowed ?: FALLBACK_BOUNDS
        window.goWindowed(x, y, width, height)
    }

    private fun usable(bounds: IntArray): IntArray? =
        bounds.takeIf { it.size == BOUNDS_SIZE && it[WIDTH] > 0 && it[HEIGHT] > 0 }?.copyOf()

    private companion object {
        const val BOUNDS_SIZE = 4
        const val WIDTH = 2
        const val HEIGHT = 3

        /** Where a window that never had windowed bounds goes when it leaves fullscreen. */
        val FALLBACK_BOUNDS = intArrayOf(100, 100, 1280, 720)
    }
}
