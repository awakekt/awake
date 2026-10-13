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
    // the same instance. A fresh PackedUiFont per call would upload another atlas every frame --
    // which is exactly what a `TextStyle(font = UiFonts.bitmap())` built fresh every frame does
    // unless the factory itself returns the same instance back.
    private val defaultCache = mutableMapOf<Int, UiFont>()
    private val bitmapCache = mutableMapOf<Int, UiFont>()
    private val trueSansCache = mutableMapOf<Int, UiFont>()
    private val msdfCache = mutableMapOf<Int, UiFont>()

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
     * @return The cached bitmap-sampled [UiFont] instance at the requested cell size.
     */
    fun bitmap(cellSize: Int = 12): UiFont = bitmapCache.getOrPut(cellSize) { BitmapFont(cellSize = cellSize) }

    /**
     * Creates a single-weight sans-serif font instance backed by bundled regular Roboto data.
     *
     * @param cellSize The base cell size in pixels.
     * @return The cached packed sans-serif [UiFont] instance at the requested cell size.
     */
    fun trueSans(cellSize: Int = 12): UiFont = trueSansCache.getOrPut(cellSize) {
        PackedUiFont(RobotoRegularUiFontData, cellSize = cellSize)
    }

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
     * @return The cached MSDF-sampled [UiFont] instance at the requested cell size.
     */
    fun msdf(cellSize: Int = 12): UiFont = msdfCache.getOrPut(cellSize) { MsdfFont(cellSize = cellSize) }

    /** The chars [load] bakes when a caller names none: the built-in atlas's own set, printable ASCII plus • · … – — × −. */
    const val DEFAULT_CHARS: String =
        " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~•·…–—×−"

    /**
     * Loads a TrueType (`.ttf`) or OpenType (`.otf`) font from its file's bytes and bakes [chars] into a
     * distance-field atlas like the built-in Roboto's, so it draws through the same glyph shader.
     *
     * The glyph set is fixed here: a char outside [chars], or one the font lacks, draws as `'?'`.
     * Kerning is not applied, as it is not for the built-in fonts. Every target bakes the same bytes from
     * the same file. All the work happens in this call, none per frame: the default set takes tens of
     * milliseconds on desktop, so load a font while a scene loads, not during one.
     *
     * @param bytes The font file.
     * @param name Names the font in error messages.
     * @param chars The chars to bake.
     * @param cellSize The base cell size in pixels, as for [default].
     * @return A [UiFont] with its own atlas.
     * @throws IllegalArgumentException when [bytes] are not a font this reads (truncated or corrupt, a
     *   collection, WOFF, CID-keyed or CFF2; the message names the table at fault), or when its glyphs
     *   are too large or detailed to bake.
     */
    fun load(bytes: ByteArray, name: String, chars: String = DEFAULT_CHARS, cellSize: Int = 12): UiFont =
        PackedUiFont(OpenTypeUiFontData.load(bytes, name, chars), cellSize = cellSize)
}

typealias Fonts = UiFonts
