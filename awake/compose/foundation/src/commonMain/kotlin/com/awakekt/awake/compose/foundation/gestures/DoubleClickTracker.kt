/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

/**
 * Decides whether a click completes a double click: a second click within [maxIntervalSeconds] of
 * the first and within [slopPx] of where it landed.
 *
 * Plain state with no UI dependency, for any surface that already turns presses into clicks: a
 * canvas element, a list row, a hit test in a 3D view. The caller supplies the time, so a tracker
 * runs on whichever clock the caller's frame runs on and is trivial to test.
 *
 * A double click consumes both clicks: a third quick click starts a new pair rather than firing a
 * second double click, which is what a desktop file list does.
 *
 * @property maxIntervalSeconds Longest gap between the two clicks.
 * @property slopPx Furthest the second click may land from the first, in the caller's units.
 */
class DoubleClickTracker(
    val maxIntervalSeconds: Float = DEFAULT_MAX_INTERVAL_SECONDS,
    val slopPx: Int = DEFAULT_SLOP_PX,
) {
    private var firstSeconds = Float.NaN
    private var firstX = 0
    private var firstY = 0

    /**
     * Records a click at ([x], [y]) at [nowSeconds] and returns true when it completes a double
     * click. A click that does not becomes the first of the next pair.
     */
    fun onClick(nowSeconds: Float, x: Int, y: Int): Boolean {
        val elapsed = nowSeconds - firstSeconds
        // NaN (no first click) fails every comparison, so no separate armed flag is needed.
        val inTime = elapsed >= 0f && elapsed <= maxIntervalSeconds
        val dx = x - firstX
        val dy = y - firstY
        if (inTime && dx * dx + dy * dy <= slopPx * slopPx) {
            reset()
            return true
        }
        firstSeconds = nowSeconds
        firstX = x
        firstY = y
        return false
    }

    /** Forgets a pending first click, for instance when the target changes under the pointer. */
    fun reset() {
        firstSeconds = Float.NaN
    }

    /** Defaults matching what text fields here use for double-click word selection. */
    companion object {
        /** Seconds between the two clicks of a double click. */
        const val DEFAULT_MAX_INTERVAL_SECONDS: Float = 0.4f

        /** Pixels the second click may drift from the first. */
        const val DEFAULT_SLOP_PX: Int = 8
    }
}
