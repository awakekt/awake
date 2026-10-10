/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.TouchEvent
import org.w3c.dom.TouchList
import org.w3c.dom.events.Event

/**
 * Passes every finger on a touch screen to [input] as a touch, so the UI sees multi-finger gestures;
 * the first finger also drives the primary pointer (see [Input.setTouch]).
 *
 * Each event is cancelled. Otherwise the browser follows a tap with emulated mouse events, which
 * would press the UI a second time, and pinches zoom the page instead of reaching the app.
 */
internal fun bindWindowTouchInput(input: Input, canvas: HTMLCanvasElement) {
    fun pass(list: TouchList, down: Boolean) {
        val box = canvas.box()
        for (i in 0 until list.length) {
            val touch = list.item(i) ?: continue
            val (x, y) = box.bufferPoint(touch.clientX.toDouble(), touch.clientY.toDouble(), canvas.width, canvas.height)
            input.setTouch(touch.identifier.toLong(), x, y, down)
        }
    }

    fun listen(type: String, lifts: Boolean) = addNonPassiveListener(type) { event ->
        val touches = event as TouchEvent
        // A lift reports the fingers that left; the rest stay where they were.
        if (lifts) pass(touches.changedTouches, down = false) else pass(touches.touches, down = true)
        event.preventDefault()
    }
    listen("touchstart", lifts = false)
    listen("touchmove", lifts = false)
    listen("touchend", lifts = true)
    listen("touchcancel", lifts = true)
}

// Non-passive, or the browser ignores preventDefault on touchmove and scrolls or zooms the page.
@JsFun("(type, listener) => window.addEventListener(type, listener, { passive: false })")
private external fun addNonPassiveListener(type: String, listener: (Event) -> Unit)
