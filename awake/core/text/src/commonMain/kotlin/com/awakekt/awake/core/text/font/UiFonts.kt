/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Factory and registry providing pre-configured standard UI font instances and families.
 */
object UiFonts {
    // Memoized: a renderer uploads one atlas per UiFont instance and keeps it, keyed by
    // identity, on the assumption that independent callers asking for "the default font" get
    // the same instance. A fresh PackedUiFont per call would upload another atlas every frame.
    private val defaultCache = mutableMapOf<Int, UiFont>()

    /**
     * Stable bundled atlas used by all targets unless a caller explicitly supplies another font.
     *
     * @param cellSize The base cell size in pixels for the requested font.
     * @return The cached default font instance at the requested cell size.
     */
    fun default(cellSize: Int = 12): UiFont = defaultCache.getOrPut(cellSize) {
        weightedSans(cellSize = cellSize)
    }

    /**
     * Creates a retro fixed-pitch bitmap font instance.
     *
     * @param cellSize The base cell size in pixels.
     * @return A bitmap-sampled [UiFont] instance.
     */
    fun bitmap(cellSize: Int = 12): UiFont = BitmapFont(cellSize = cellSize)

    /**
     * Creates a single-weight sans-serif font instance backed by bundled regular Roboto data.
     *
     * @param cellSize The base cell size in pixels.
     * @return A packed sans-serif [UiFont] instance.
     */
    fun trueSans(cellSize: Int = 12): UiFont =
        PackedUiFont(RobotoRegularUiFontData, cellSize = cellSize)

    /**
     * Bundled family with real face selection when additional generated faces are present.
     *
     * @param cellSize The base cell size in pixels.
     * @return A weighted multi-face [UiFont] family.
     */
    fun weightedSans(cellSize: Int = 12): UiFont =
        WeightedUiFont(
            mapOf(
                FontWeight.Thin to PackedUiFont(RobotoThinUiFontData, cellSize = cellSize),
                FontWeight.Light to PackedUiFont(RobotoLightUiFontData, cellSize = cellSize),
                FontWeight.Normal to PackedUiFont(RobotoRegularUiFontData, cellSize = cellSize),
                FontWeight.Medium to PackedUiFont(RobotoMediumUiFontData, cellSize = cellSize),
                FontWeight.SemiBold to PackedUiFont(RobotoSemiBoldUiFontData, cellSize = cellSize),
                FontWeight.Bold to PackedUiFont(RobotoBoldUiFontData, cellSize = cellSize),
                FontWeight.Black to PackedUiFont(RobotoBlackUiFontData, cellSize = cellSize),
            ),
        )

    /**
     * Builds a Compose-shaped font family from caller-provided weighted faces.
     *
     * Every face must expose the same glyph coordinate space and sampling mode. The family
     * combines their atlases so every weight of the family samples one texture.
     *
     * @param faces Map of font weights to corresponding [UiFont] instances.
     * @return A unified [UiFont] family supporting weight selection.
     */
    fun family(faces: Map<FontWeight, UiFont>): UiFont = WeightedUiFont(faces)

    /**
     * Creates a multi-channel signed distance field (MSDF) font instance.
     *
     * @param cellSize The base cell size in pixels.
     * @return An MSDF-sampled [UiFont] instance.
     */
    fun msdf(cellSize: Int = 12): UiFont = MsdfFont(cellSize = cellSize)
}

typealias Fonts = UiFonts
