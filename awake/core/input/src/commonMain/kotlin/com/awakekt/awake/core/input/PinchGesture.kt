/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.ln

/**
 * A frame's trackpad pinch: how far it zoomed and the point it zoomed about.
 *
 * A pinch is a zoom, so it has its own field: a consumer can zoom by it exactly, about where the
 * fingers are, and tell it from a wheel. Plain two-finger scrolling stays scroll.
 *
 * **Read the pinch or the scroll, never both.** In a browser the same pinch also arrives the way it
 * always has, as Ctrl held with a scroll whose [InputSnapshot.scrollDeltaY] is this [zoomDelta]; a
 * Windows touchpad sends only that, and macOS only this. So a consumer that reads [pinch][InputSnapshot.pinch]
 * zooms by it on a frame that has one and ignores that frame's scroll, and falls back to Ctrl+scroll
 * on the frames without one. The built-in cameras do exactly that.
 *
 * [scale] is a factor, so the steps of one pinch multiply: a view that scales its zoom by it each
 * frame lands where the fingers say, at any frame rate. A consumer that zooms by an additive amount
 * reads [zoomDelta] instead.
 *
 * @property scale How much the pinch zoomed this frame, as a factor. Above 1 the fingers spread
 *   (zoom in), below 1 they closed (zoom out). Always finite and positive.
 * @property focusX Horizontal position the pinch is about, in the same pixels as [InputSnapshot.pointerX].
 * @property focusY Vertical position the pinch is about, in the same pixels as [InputSnapshot.pointerY].
 */
data class PinchGesture(
    val scale: Float,
    val focusX: Float,
    val focusY: Float,
) {
    /** [scale] as an additive amount, `ln(scale)`: positive zooms in, and two steps add. */
    val zoomDelta: Float get() = ln(scale)
}
