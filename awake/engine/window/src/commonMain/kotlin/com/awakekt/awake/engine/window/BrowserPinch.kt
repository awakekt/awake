/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import kotlin.math.exp

/**
 * A browser's trackpad pinch as [Input.addPinch], in the browser bridge's buffer pixels.
 *
 * Chrome, Edge and Firefox send a pinch as `wheel` events with `ctrlKey` set, though no Control key
 * is down. The browser's wheel listener already delivers each as Ctrl held plus its scroll, the way
 * a Windows touchpad's pinch arrives on desktop, and this adds the same step as a typed pinch.
 * A wheel with Control really held is a Ctrl+wheel, not a pinch; the bridge reads [controlHeld] from
 * the keyboard to tell the two apart.
 *
 * Safari sends `gesturestart`, `gesturechange` and `gestureend` instead, with the pinch's scale
 * since it began, and those arrive as the typed pinch only.
 *
 * Kept off the DOM types so it is tested in `commonTest`; the browser bridge feeds it the event fields.
 */
internal class BrowserPinch(private val input: Input) {
    /** Whether a Control key is physically held, as the page's `keydown`/`keyup` say. */
    var controlHeld: Boolean = false

    private var gestureActive = false
    private var gestureScale = 1.0

    /** Takes one `wheel` event at ([x], [y]): a pinch if it has [ctrlKey] and Control is not held. */
    fun wheel(deltaY: Double, deltaMode: Int, ctrlKey: Boolean, x: Float, y: Float) {
        // A Safari that also sent a ctrl-wheel during its gesture would pinch twice.
        if (ctrlKey && !controlHeld && !gestureActive) input.addPinch(domPinchWheelScale(deltaY, deltaMode), x, y)
    }

    /** Safari's `gesturestart`: a pinch begins at scale 1. */
    fun gestureStart() {
        gestureActive = true
        gestureScale = 1.0
    }

    /** Safari's `gesturechange`: [scale] is the pinch's whole scale since it began, at ([x], [y]). */
    fun gestureChange(scale: Double, x: Float, y: Float) {
        if (!gestureActive) gestureStart()
        if (!scale.isFinite() || scale <= 0.0) return
        input.addPinch((scale / gestureScale).toFloat(), x, y)
        gestureScale = scale
    }

    /** Safari's `gestureend`, whose [scale] is the pinch's final one. */
    fun gestureEnd(scale: Double, x: Float, y: Float) {
        if (gestureActive) gestureChange(scale, x, y)
        gestureActive = false
    }
}

/**
 * The zoom factor of one pinch `wheel` event.
 *
 * Chromium encodes a pinch step's scale `s` as a pixel `deltaY` of `-100 * ln(s)`, so this inverts
 * that exactly: spread fingers send a negative delta and zoom in. It is `exp` of the event's
 * [domWheelToScrollDelta], so the scroll the wheel listener adds for the same event is this step's
 * [com.awakekt.awake.core.input.PinchGesture.zoomDelta]. Firefox's pixel deltas are close enough to
 * read the same way, and a step is clamped to [MIN_PINCH_STEP, MAX_PINCH_STEP] so a line- or
 * page-mode delta cannot jump the zoom.
 */
internal fun domPinchWheelScale(deltaY: Double, deltaMode: Int): Float =
    exp(domWheelToScrollDelta(deltaY, deltaMode).toDouble()).toFloat().coerceIn(MIN_PINCH_STEP, MAX_PINCH_STEP)

private const val MIN_PINCH_STEP = 0.5f
private const val MAX_PINCH_STEP = 2f
