/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input

/**
 * Reads the first connected controller the browser maps to the standard layout into [input]'s
 * controller buttons, through the Gamepad API. The Gamepad API has no events for buttons, so a host
 * calls this once a frame. A browser lists a controller only once a button on it has been pressed
 * while the page is open, and one in another layout is not read.
 */
internal fun pollBrowserGamepad(input: Input): Unit = applyGamepadBits(browserGamepadButtons(), input, StandardGamepadLayout)

// Bit i is the standard layout's buttons[i], or -1 (NO_GAMEPAD) with no standard controller.
@JsFun(
    """() => {
    const pads = navigator.getGamepads ? navigator.getGamepads() : [];
    for (const pad of pads) {
        if (!pad || !pad.connected || pad.mapping !== "standard") continue;
        let bits = 0;
        for (let i = 0; i < pad.buttons.length && i < 31; i++) if (pad.buttons[i].pressed) bits |= 1 << i;
        return bits;
    }
    return -1;
}""",
)
private external fun browserGamepadButtons(): Int
