/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Where a desktop window's UI density, its framebuffer pixels per dp, comes from. Set it with
 * [DesktopWindowOptions.displayScale].
 */
enum class DesktopDisplayScale {
    /**
     * The OS display scale of the monitor the window is on, followed as it changes: 1.25 and 1.5 at
     * Windows' 125% and 150%, 2 on a Retina panel, `Xft.dpi` over 96 on X11. A browser does the same
     * through `devicePixelRatio`, so a desktop build and a web build lay the UI out alike.
     */
    System,

    /** One framebuffer pixel per dp on every display, whatever the OS scale: the UI draws at native pixels. */
    Unscaled,
}

/**
 * The UI density for a window in [mode]: framebuffer pixels per dp.
 *
 * Where the platform scales window coordinates itself (macOS Retina, Wayland), the framebuffer is
 * already a multiple of the window and that [framebufferRatio] is the density. Where window
 * coordinates are pixels (Windows, whose GLFW process is per-monitor DPI aware, and X11), the ratio is
 * 1 and the OS scale is only in the [contentScale]. A scale GLFW cannot tell (0, NaN) reads as 1.
 */
internal fun desktopUiDensity(mode: DesktopDisplayScale, contentScale: Float, framebufferRatio: Float): Float = when {
    mode == DesktopDisplayScale.Unscaled -> 1f
    framebufferRatio.isFinite() && framebufferRatio > SCALED_FRAMEBUFFER -> framebufferRatio
    contentScale.isFinite() && contentScale > 0f -> contentScale
    else -> 1f
}

/** Above this the framebuffer is a scaled multiple of the window, not the same pixels with rounding. */
private const val SCALED_FRAMEBUFFER = 1.01f

/** What [DisplayDensity] reads from a window; [glfwDisplayScaleReader] is the GLFW one. */
internal interface DisplayScaleReader {
    /** Whether the content scale changed since the last call (the content scale callback fired). */
    fun consumeChanged(): Boolean

    /** The OS display scale; 0 when unknown. */
    fun contentScale(): Float

    /** Framebuffer width over window width; 0 while the window has no size (minimised). */
    fun framebufferRatio(): Float
}

/**
 * Reports [mode]'s UI density to [onDensity]: at once, then whenever the content scale callback says
 * it changed. A window library built before content scale was bound reports nothing, and the backend's
 * own framebuffer-over-window ratio stays the density, as it was.
 */
internal class DisplayDensity(
    private val mode: DesktopDisplayScale,
    private val reader: DisplayScaleReader?,
    private val onDensity: (Float) -> Unit,
) {
    private var reported = Float.NaN
    private var pending = true

    init {
        check()
    }

    fun check() {
        val source = reader ?: return
        if (source.consumeChanged()) pending = true
        if (pending) read(source)
    }

    private fun read(source: DisplayScaleReader) {
        val ratio = source.framebufferRatio()
        // A minimised window has no size to read a ratio from; read it again once it has one.
        if (ratio <= 0f && !reported.isNaN()) return
        pending = false
        val density = desktopUiDensity(mode, source.contentScale(), ratio)
        if (density != reported) {
            reported = density
            onDensity(density)
        }
    }
}

/**
 * The GLFW [DisplayScaleReader] of [window], with its content scale callback set; `null` when the
 * window library predates content scale.
 */
internal fun glfwDisplayScaleReader(window: Long): DisplayScaleReader? = try {
    GlfwWindow.glfwSetContentScaleCallback(window)
    GlfwDisplayScaleReader(window)
} catch (_: UnsatisfiedLinkError) {
    null
}

private class GlfwDisplayScaleReader(private val window: Long) : DisplayScaleReader {
    override fun consumeChanged(): Boolean = GlfwWindow.glfwConsumeContentScaleChanged(window)

    override fun contentScale(): Float = GlfwWindow.glfwGetWindowContentScale(window)

    override fun framebufferRatio(): Float {
        val width = GlfwWindow.glfwGetWindowWidth(window)
        return if (width > 0) GlfwWindow.glfwGetFramebufferWidth(window).toFloat() / width else 0f
    }
}
