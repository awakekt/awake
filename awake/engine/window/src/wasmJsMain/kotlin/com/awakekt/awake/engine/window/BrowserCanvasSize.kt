/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

/**
 * The pixel width and height [canvas]'s buffer needs: its box on the page at the device pixel ratio.
 * The box, not the window, since page CSS can make the two differ.
 */
fun currentCanvasSize(canvas: HTMLCanvasElement = findCanvas()): Pair<Int, Int> =
    currentCanvasSize(canvas, maxPixelRatio = null)

/**
 * The pixel width and height [canvas]'s buffer needs at [currentCanvasPixelRatio]: the device pixel
 * ratio, capped at [maxPixelRatio] (`null` for no cap).
 */
fun currentCanvasSize(canvas: HTMLCanvasElement, maxPixelRatio: Float?): Pair<Int, Int> =
    canvas.box().bufferSize(currentCanvasPixelRatio(maxPixelRatio))

/**
 * The pixel ratio a canvas buffer is drawn at, and the UI's density with it: the device pixel ratio,
 * capped at [maxPixelRatio] (`null` for no cap).
 */
fun currentCanvasPixelRatio(maxPixelRatio: Float?): Double =
    canvasPixelRatio(currentWindowDensity(), maxPixelRatio)

/** Returns the current browser window device pixel ratio. */
fun currentWindowDensity(): Double {
    val scale = window.devicePixelRatio
    return if (scale.isFinite() && scale > 0.0) scale else 1.0
}

/** Sets the internal pixel buffer dimensions of [canvas]. */
fun syncCanvasSize(canvas: HTMLCanvasElement, width: Int, height: Int) {
    canvas.width = width
    canvas.height = height
}
