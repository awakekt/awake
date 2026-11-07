/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Reports the dots per inch of the monitor [window] is on to [onDpi] when it changes: at once, then
 * every [CHECK_EVERY] frames, as a window dragged to another monitor needs. A window library built
 * before it could tell reports nothing.
 */
internal class MonitorDpi(private val window: Long, private val onDpi: (Float) -> Unit) {
    private var frames = 0
    private var reported = Float.NaN

    init {
        check(force = true)
    }

    fun check(force: Boolean = false) {
        if (!force && ++frames % CHECK_EVERY != 0) return
        val dpi = try {
            GlfwWindow.glfwGetWindowDpi(window)
        } catch (_: UnsatisfiedLinkError) {
            0f
        }
        if (dpi != reported) {
            reported = dpi
            onDpi(dpi)
        }
    }

    private companion object {
        /** About a second at 60 frames a second: soon enough after a drag, and a negligible cost. */
        const val CHECK_EVERY = 60
    }
}
