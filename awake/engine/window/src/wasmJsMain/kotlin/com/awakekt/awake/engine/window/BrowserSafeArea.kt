/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import org.w3c.dom.HTMLCanvasElement

/**
 * What the page's safe area leaves uncovered of [canvas], in CSS pixels, which are dp: the browser's
 * `env(safe-area-inset-*)` less how far the canvas already sits inside it. The insets are 0 unless
 * the page's viewport meta tag has `viewport-fit=cover`, which lets it draw under a notch.
 */
fun currentSafeArea(canvas: HTMLCanvasElement = findCanvas()): DisplayInsets {
    val sides = safeAreaOf(canvas).split(',').map { it.toFloatOrNull() ?: 0f }
    return if (sides.size == SIDES) DisplayInsets(sides[0], sides[1], sides[2], sides[3]).clamped() else DisplayInsets.None
}

private const val SIDES = 4

/**
 * Left, top, right and bottom of [canvas] that the safe area covers, comma-separated. CSS can't hand
 * `env()` to a script, so a hidden fixed probe takes the insets as its padding and is read back.
 */
@JsFun(
    """(canvas) => {
    let probe = document.getElementById('awake-safe-area-probe');
    if (!probe) {
        probe = document.createElement('div');
        probe.id = 'awake-safe-area-probe';
        probe.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;pointer-events:none;' +
            'padding:env(safe-area-inset-top,0px) env(safe-area-inset-right,0px) env(safe-area-inset-bottom,0px) env(safe-area-inset-left,0px)';
        document.body.appendChild(probe);
    }
    const style = getComputedStyle(probe);
    const box = canvas.getBoundingClientRect();
    const left = parseFloat(style.paddingLeft) - box.left;
    const top = parseFloat(style.paddingTop) - box.top;
    const right = parseFloat(style.paddingRight) - (window.innerWidth - box.right);
    const bottom = parseFloat(style.paddingBottom) - (window.innerHeight - box.bottom);
    return [left, top, right, bottom].join(',');
}""",
)
private external fun safeAreaOf(canvas: HTMLCanvasElement): String
