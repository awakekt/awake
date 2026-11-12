/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import org.w3c.dom.HTMLCanvasElement

/**
 * Reports how far the on-screen keyboard rises into [canvas], in CSS pixels (dp), from
 * `visualViewport`, whenever it changes. [sync] runs each frame; a steady frame reads one number and
 * allocates nothing.
 */
internal class BrowserImeInset(private val canvas: HTMLCanvasElement, private val report: (DisplayInsets) -> Unit) {
    private var last = 0.0

    /** Reads the keyboard's height and reports it if it changed since the last call. */
    fun sync() {
        val height = keyboardHeightOf(canvas)
        if (height == last) return
        last = height
        report(DisplayInsets(bottom = height.toFloat()))
    }
}

/**
 * How much of the window's bottom the visual viewport has lost, less the gap already between the
 * canvas and that edge: 0 without `visualViewport`, and while the page is pinch-zoomed, because zoom
 * shrinks the visual viewport too and is not a keyboard.
 */
@JsFun(
    """(canvas) => {
    const viewport = window.visualViewport;
    if (!viewport || viewport.scale > 1.01) return 0;
    const lost = window.innerHeight - viewport.height - viewport.offsetTop;
    const gap = Math.max(0, window.innerHeight - canvas.getBoundingClientRect().bottom);
    return Math.max(0, Math.round(lost - gap));
}""",
)
private external fun keyboardHeightOf(canvas: HTMLCanvasElement): Double
