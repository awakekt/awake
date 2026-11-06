/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

/**
 * The page's [Fullscreen], through the browser's Fullscreen API on `document.documentElement`, or
 * null when `document.fullscreenEnabled` is false: an `<iframe>` without `allow="fullscreen"`, or
 * Safari on iPhone, which has no element fullscreen. Older Safari's `webkit` names are used where
 * the standard ones are missing.
 *
 * The whole page goes fullscreen, not only the canvas, so DOM layered over the canvas (the text
 * input bridge, a loading screen) stays visible. The canvas resizes as it does for any window
 * resize.
 *
 * [Fullscreen.enter] catches the browser's refusal (no user gesture, a permission policy), so a
 * refused request never reaches the console as an unhandled rejection.
 */
fun browserFullscreen(): Fullscreen? = if (fullscreenEnabled()) BrowserFullscreen else null

private object BrowserFullscreen : Fullscreen {
    override val isActive: Boolean
        get() = fullscreenActive()

    override fun enter(): Boolean = isActive || requestFullscreen()

    override fun exit() {
        if (isActive) exitFullscreen()
    }
}

private fun fullscreenEnabled(): Boolean = js(
    """{
        const d = document;
        return !!(d.fullscreenEnabled || d.webkitFullscreenEnabled);
    }""",
)

private fun fullscreenActive(): Boolean = js(
    """{
        const d = document;
        return !!(d.fullscreenElement || d.webkitFullscreenElement);
    }""",
)

private fun requestFullscreen(): Boolean = js(
    """{
        const e = document.documentElement;
        try {
            if (e.requestFullscreen) {
                const p = e.requestFullscreen();
                if (p && p.catch) p.catch(() => {});
                return true;
            }
            if (e.webkitRequestFullscreen) {
                e.webkitRequestFullscreen();
                return true;
            }
        } catch (_) {}
        return false;
    }""",
)

private fun exitFullscreen(): Unit = js(
    """{
        const d = document;
        try {
            if (d.exitFullscreen) {
                const p = d.exitFullscreen();
                if (p && p.catch) p.catch(() => {});
            } else if (d.webkitExitFullscreen) {
                d.webkitExitFullscreen();
            }
        } catch (_) {}
    }""",
)
