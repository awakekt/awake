/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

/**
 * Where the canvas is looking: zoom and pan.
 *
 * Caller-owned and held across frames with `remember`, like `ScrollState`: the canvas changes it
 * while the user pans and zooms, and layout reads it every frame.
 *
 * Canvas coordinates are the graph's own units, one `dp` at zoom 1. A canvas point `c` lands on
 * screen at `c * zoom * density + pan`, where [panX] and [panY] are pixels.
 */
class NodeGraphViewport(
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    val minZoom: Float = 0.25f,
    val maxZoom: Float = 2.5f,
) {
    init {
        require(minZoom > 0f && minZoom <= maxZoom) { "Zoom range $minZoom..$maxZoom is empty." }
    }

    var zoom: Float = zoom.coerceIn(minZoom, maxZoom)
        private set

    var panX: Float = panX
        private set

    var panY: Float = panY
        private set

    fun toScreenX(canvasX: Float, density: Float): Float = canvasX * zoom * density + panX

    fun toScreenY(canvasY: Float, density: Float): Float = canvasY * zoom * density + panY

    fun toCanvasX(screenX: Float, density: Float): Float = (screenX - panX) / (zoom * density)

    fun toCanvasY(screenY: Float, density: Float): Float = (screenY - panY) / (zoom * density)

    /** Moves the view by a screen-pixel delta. */
    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /**
     * Multiplies the zoom by [factor], clamped, keeping the canvas point under ([screenX],
     * [screenY]) where it is, so zooming follows the cursor.
     */
    fun zoomBy(factor: Float, screenX: Float, screenY: Float, density: Float) {
        val canvasX = toCanvasX(screenX, density)
        val canvasY = toCanvasY(screenY, density)
        zoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        panX = screenX - canvasX * zoom * density
        panY = screenY - canvasY * zoom * density
    }

    override fun toString(): String = "NodeGraphViewport(zoom=$zoom, pan=($panX, $panY))"
}
