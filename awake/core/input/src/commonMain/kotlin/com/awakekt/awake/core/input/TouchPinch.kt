/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.hypot

/**
 * Turns two fingers on a touch screen into [Input.addPinch] steps, for a touch bridge whose platform
 * reports fingers but no pinch.
 *
 * After each platform touch event the bridge passes the first two fingers still down to [track], or
 * calls [end] when fewer than two are down. A bridge whose platform reports only the fingers that
 * changed, such as UIKit's, calls [update] instead, which reads every finger [Input.setTouch] holds.
 *
 * Each step's scale is the fingers' distance now over their distance at the last event, about their
 * midpoint, in the same pixels as [Input.setTouch]. The steps of one pinch multiply, so the frame's
 * [PinchGesture] lands where the fingers say at any event rate.
 *
 * A new pair (a finger put down or lifted, so the two first fingers change) only sets the baseline:
 * it gives no step, so the pinch never jumps. Fingers that do not change their distance give none either.
 */
class TouchPinch {
    private var tracking = false
    private var firstId = 0L
    private var secondId = 0L
    private var lastSpan = 0f

    /** Takes the two fingers [firstId] at ([firstX], [firstY]) and [secondId] at ([secondX], [secondY]). */
    fun track(
        input: Input,
        firstId: Long,
        firstX: Float,
        firstY: Float,
        secondId: Long,
        secondX: Float,
        secondY: Float,
    ) {
        val span = hypot(secondX - firstX, secondY - firstY)
        val samePair = tracking && firstId == this.firstId && secondId == this.secondId
        if (samePair && lastSpan >= MIN_SPAN && span >= MIN_SPAN) {
            input.addPinch(span / lastSpan, (firstX + secondX) / 2f, (firstY + secondY) / 2f)
        }
        tracking = true
        this.firstId = firstId
        this.secondId = secondId
        lastSpan = span
    }

    /**
     * Takes the first two fingers down in [input], in the order they touched down, after a touch
     * event's [Input.setTouch] calls; ends the pinch when fewer than two are down.
     */
    fun update(input: Input) {
        input.stepTouchPinch(this)
    }

    /** Fewer than two fingers are down: the pinch, if there was one, is over. */
    fun end() {
        tracking = false
    }
}

// Two fingers closer than a pixel are one point; a ratio against that distance means nothing.
private const val MIN_SPAN = 1f
