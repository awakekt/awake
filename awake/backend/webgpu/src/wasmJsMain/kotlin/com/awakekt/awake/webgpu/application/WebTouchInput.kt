/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.core.input.Input
import org.w3c.dom.Element
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
internal fun bindWindowTouchInput(input: Input) {
    fun pass(list: TouchList, target: Element?, down: Boolean) {
        val density = currentWindowDensity()
        val bounds = target?.getBoundingClientRect()
        for (i in 0 until list.length) {
            val touch = list.item(i) ?: continue
            val x = (touch.clientX.toDouble() - (bounds?.left ?: 0.0)) * density
            val y = (touch.clientY.toDouble() - (bounds?.top ?: 0.0)) * density
            input.setTouch(touch.identifier.toLong(), x.toFloat(), y.toFloat(), down)
        }
    }

    fun listen(type: String, lifts: Boolean) = addNonPassiveListener(type) { event ->
        val touches = event as TouchEvent
        val target = event.target as? Element
        // A lift reports the fingers that left; the rest stay where they were.
        if (lifts) pass(touches.changedTouches, target, down = false) else pass(touches.touches, target, down = true)
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
