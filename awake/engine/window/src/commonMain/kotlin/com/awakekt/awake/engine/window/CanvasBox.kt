/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * A browser canvas's box on the page, in CSS pixels, as `getBoundingClientRect` reports it.
 *
 * The canvas shows its pixel buffer stretched over this box, and the box is whatever the page's CSS
 * makes it, not the window: `100vh` on a phone is taller than `window.innerHeight` while the
 * browser's toolbar shows. So the buffer is sized from the box, and a pointer is measured from it.
 */
internal data class CanvasBox(val left: Double, val top: Double, val width: Double, val height: Double) {

    /** The buffer that puts one buffer pixel on each device pixel of this box, at [density]. */
    fun bufferSize(density: Double): Pair<Int, Int> =
        (width * density).toInt().coerceAtLeast(1) to (height * density).toInt().coerceAtLeast(1)

    /**
     * Where the page point [clientX], [clientY] lands in a [bufferWidth] by [bufferHeight] buffer
     * shown over this box: measured from the box's corner and scaled by buffer pixels per CSS pixel
     * on each axis, so it holds even when the buffer doesn't match the box yet.
     */
    fun bufferPoint(clientX: Double, clientY: Double, bufferWidth: Int, bufferHeight: Int): Pair<Float, Float> {
        // A canvas that isn't displayed has an empty box: its points land on its corner.
        val scaleX = if (width > 0.0) bufferWidth / width else 0.0
        val scaleY = if (height > 0.0) bufferHeight / height else 0.0
        return ((clientX - left) * scaleX).toFloat() to ((clientY - top) * scaleY).toFloat()
    }
}
