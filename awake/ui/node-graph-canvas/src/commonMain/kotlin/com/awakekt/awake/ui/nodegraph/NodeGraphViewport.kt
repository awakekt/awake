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
 *
 * @param zoom Initial zoom factor.
 * @param panX Initial horizontal pan offset in screen pixels.
 * @param panY Initial vertical pan offset in screen pixels.
 * @property minZoom Minimum permissible zoom factor.
 * @property maxZoom Maximum permissible zoom factor.
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

    /** Current canvas zoom factor, clamped within [minZoom]..[maxZoom]. */
    var zoom: Float = zoom.coerceIn(minZoom, maxZoom)
        private set

    /** Horizontal pan offset in screen pixels. */
    var panX: Float = panX
        private set

    /** Vertical pan offset in screen pixels. */
    var panY: Float = panY
        private set

    /**
     * Converts a horizontal coordinate in canvas units to a screen pixel position.
     *
     * @param canvasX Horizontal coordinate in canvas units.
     * @param density Display density scaling factor.
     * @return Calculated horizontal position on screen in pixels.
     */
    fun toScreenX(canvasX: Float, density: Float): Float = canvasX * zoom * density + panX

    /**
     * Converts a vertical coordinate in canvas units to a screen pixel position.
     *
     * @param canvasY Vertical coordinate in canvas units.
     * @param density Display density scaling factor.
     * @return Calculated vertical position on screen in pixels.
     */
    fun toScreenY(canvasY: Float, density: Float): Float = canvasY * zoom * density + panY

    /**
     * Converts a horizontal screen pixel coordinate to canvas units.
     *
     * @param screenX Horizontal position on screen in pixels.
     * @param density Display density scaling factor.
     * @return Calculated horizontal coordinate in canvas units.
     */
    fun toCanvasX(screenX: Float, density: Float): Float = (screenX - panX) / (zoom * density)

    /**
     * Converts a vertical screen pixel coordinate to canvas units.
     *
     * @param screenY Vertical position on screen in pixels.
     * @param density Display density scaling factor.
     * @return Calculated vertical coordinate in canvas units.
     */
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
