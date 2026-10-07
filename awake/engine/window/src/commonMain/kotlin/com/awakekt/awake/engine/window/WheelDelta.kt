/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * A DOM `WheelEvent` delta as [com.awakekt.awake.core.input.Input]'s scroll units: roughly one per
 * wheel notch, and signed the way desktop's GLFW callback signs them.
 *
 * The DOM and GLFW disagree on sign. A DOM delta is how far the page scrolls, positive down and
 * right; a GLFW offset is how far the wheel turned, positive for a wheel turned away from the
 * user, and both are read from the same platform event, so the sideways axis flips too. Passed
 * through unchanged, a wheel turned toward the user scrolled every Awake scroll container up in
 * the browser and down on desktop.
 *
 * Browsers also report different units by `deltaMode`: raw pixels (Chrome's default for most mice
 * and trackpads), lines (Firefox with some mice), or pages. Desktop's GLFW callback reports about
 * 1.0 per notch (line-like), so pixel and page deltas are scaled to that.
 */
internal fun domWheelToScrollDelta(delta: Double, deltaMode: Int): Float {
    val units = when (deltaMode) {
        DOM_DELTA_LINE -> delta
        DOM_DELTA_PAGE -> delta * LINES_PER_PAGE
        else -> delta / PIXELS_PER_LINE // DOM_DELTA_PIXEL
    }
    return (-units).toFloat()
}

// WheelEvent.deltaMode values, restated so this stays testable off the browser.
internal const val DOM_DELTA_LINE = 1
internal const val DOM_DELTA_PAGE = 2
private const val LINES_PER_PAGE = 32.0
private const val PIXELS_PER_LINE = 100.0
