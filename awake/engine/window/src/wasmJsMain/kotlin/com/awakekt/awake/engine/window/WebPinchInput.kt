/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent

/**
 * Binds a trackpad pinch to [input] as a typed [Input.addPinch], through [BrowserPinch].
 *
 * Beside the wheel listener in [bindWindowPointerInput], not in place of it: that one still delivers
 * a pinch's `wheel` as Ctrl held plus its scroll, and cancels it so the page does not zoom. This
 * listener only reads the same event, so it can be passive. Safari's gesture events are cancelled
 * here, non-passive, or Safari zooms the page.
 */
internal fun bindWindowPinchInput(input: Input, canvas: HTMLCanvasElement) {
    val pinch = BrowserPinch(input)
    fun point(clientX: Double, clientY: Double): Pair<Float, Float> =
        canvas.box().bufferPoint(clientX, clientY, canvas.width, canvas.height)

    window.addEventListener("wheel") { event ->
        val wheel = event as WheelEvent
        val (x, y) = point(wheel.clientX.toDouble(), wheel.clientY.toDouble())
        pinch.wheel(wheel.deltaY, wheel.deltaMode, wheel.ctrlKey, x, y)
    }
    // A pinch sets ctrlKey without a Control key down; a Ctrl+wheel has one down.
    window.addEventListener("keydown") { event ->
        if ((event as KeyboardEvent).key == "Control") pinch.controlHeld = true
    }
    window.addEventListener("keyup") { event ->
        if ((event as KeyboardEvent).key == "Control") pinch.controlHeld = false
    }
    window.addEventListener("blur") { pinch.controlHeld = false }

    // Safari's own pinch events. A GestureEvent's position is its clientX/Y where Safari gives one,
    // else the pointer's last position.
    fun gesturePoint(event: Event): Pair<Float, Float> {
        val clientX = gestureClientX(event)
        val clientY = gestureClientY(event)
        return if (clientX.isFinite() && clientY.isFinite()) point(clientX, clientY) else input.pointerX to input.pointerY
    }
    addNonPassiveListener("gesturestart") { event ->
        pinch.gestureStart()
        event.preventDefault()
    }
    addNonPassiveListener("gesturechange") { event ->
        val (x, y) = gesturePoint(event)
        pinch.gestureChange(gestureScale(event), x, y)
        event.preventDefault()
    }
    addNonPassiveListener("gestureend") { event ->
        val (x, y) = gesturePoint(event)
        pinch.gestureEnd(gestureScale(event), x, y)
        event.preventDefault()
    }
}

// Safari's GestureEvent is not in the DOM bindings: read its fields directly, NaN where it has none.
@JsFun("(event) => typeof event.scale === 'number' ? event.scale : NaN")
private external fun gestureScale(event: Event): Double

@JsFun("(event) => typeof event.clientX === 'number' ? event.clientX : NaN")
private external fun gestureClientX(event: Event): Double

@JsFun("(event) => typeof event.clientY === 'number' ? event.clientY : NaN")
private external fun gestureClientY(event: Event): Double
