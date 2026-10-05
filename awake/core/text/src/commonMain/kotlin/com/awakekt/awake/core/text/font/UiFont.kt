/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.font.FontWeight

/**
 * Specifies the texture sampling mode used by the font renderer.
 */
enum class UiFontSamplingMode {
    /** Alpha coverage sampling where alpha directly represents glyph opacity. */
    CoverageAlpha,

    /** Multi-channel or signed distance field sampling for sharp rendering across scales. */
    DistanceField,
}

/**
 * Geometric metrics and UV atlas texture coordinates for an individual glyph quad.
 *
 * @property u0 The left horizontal UV texture coordinate in the atlas.
 * @property v0 The top vertical UV texture coordinate in the atlas.
 * @property u1 The right horizontal UV texture coordinate in the atlas.
 * @property v1 The bottom vertical UV texture coordinate in the atlas.
 * @property offsetXEm Horizontal offset in em units from the pen origin to the glyph quad left edge.
 * @property offsetYEm Vertical offset in em units from the baseline origin to the glyph quad top edge.
 * @property widthEm Width of the glyph quad in em units.
 * @property heightEm Height of the glyph quad in em units.
 */
data class GlyphRect(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val offsetXEm: Float = 0f,
    val offsetYEm: Float = 0f,
    val widthEm: Float = 1f,
    val heightEm: Float = 1f,
)

/**
 * Represents a typeface and rasterized glyph atlas used for UI text rendering.
 */
interface UiFont {
    /** The sampling strategy required when rendering this font's atlas. */
    val samplingMode: UiFontSamplingMode

    /** Base cell size in pixels used for integer glyph scale stepping. */
    val cellSize: Int

    /** Multiplier step applied to text scaling increments. */
    val textScaleStep: Float

    /** Width in pixels of the underlying font atlas texture. */
    val atlasWidth: Int

    /** Height in pixels of the underlying font atlas texture. */
    val atlasHeight: Int

    /** Uncompressed RGBA pixel byte array containing the glyph atlas texture data. */
    val atlasPixelsRgba: ByteArray

    /** Ascent + descent as a multiple of the em size. 1.0 for fonts whose glyphs never exceed
     * their em box; the packed Roboto atlas reports ~1.19. */
    val lineHeightEm: Float get() = 1f

    /** Highest vertical extent of visible ink in em units above or relative to the baseline. */
    val visibleTopEm: Float
        get() = 0f

    /** Lowest vertical extent of visible ink in em units below or relative to the baseline. */
    val visibleBottomEm: Float
        get() = 1f

    /** Spread in atlas texels of the distance field encoding. */
    val distanceFieldRangePx: Float
        get() = 0f

    /** Distance from layout origin to baseline in em units. */
    val ascentEm: Float
        get() = 0.8f

    /** Distance below baseline in em units. */
    val descentEm: Float
        get() = 0.2f

    /** Capital letter height in em units. */
    val capHeightEm: Float
        get() = 0.7f

    /**
     * Resolves the atlas UV bounding rectangle and layout offsets for [char].
     *
     * @param char The character to look up.
     * @return The [GlyphRect] describing the character quad, or null if the character is missing.
     */
    fun uvFor(char: Char): GlyphRect?

    /** Glyph atlas entry for a requested weight. Single-face fonts fall back to [uvFor]; a
     * weighted family resolves the requested face and returns its atlas slice. */
    fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = uvFor(char)

    /**
     * Computes the horizontal advance distance in pixels for [char] rendered at [glyphPx].
     *
     * @param char The character to measure.
     * @param glyphPx The target font size in pixels.
     * @return The horizontal advance in pixels.
     */
    fun advanceFor(char: Char, glyphPx: Float): Float = glyphPx

    /**
     * Advance for a requested weight. A single-face atlas must keep its original metrics; it
     * cannot synthesize a different weight by widening the pen, because that changes intrinsic
     * layout and can push the trailing glyph outside its slot. Weighted families and platform
     * providers override this with measurements from a real face.
     */
    fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float = advanceFor(char, glyphPx)
}

/**
 * Sums per-glyph [advanceFor] to get the pen distance the string travels -- but a glyph's own
 * ink (its [GlyphRect.offsetXEm]/[GlyphRect.widthEm] quad) can extend past its own advance;
 * several packed fonts (e.g. the embedded Roboto data) declare advances a few percent narrower
 * than the glyph's actual right edge (positive `offsetXEm + widthEm - advance`), which every
 * *interior* glyph gets away with since the next glyph's quad simply overlaps/redraws over that
 * overhang -- only the string's trailing glyph has nothing after it to hide the clip. A caller
 * that boxes text exactly to this width (see `shadcnLabel`) would otherwise clip that last
 * glyph's overhanging pixels. Widen the result to also cover the last glyph's real right edge.
 *
 * @param label The text string to measure.
 * @param glyphPx The target font height in pixels.
 * @param weight The font weight face to use for measuring.
 * @return The total visual layout width in pixels.
 */
fun UiFont.measureTextWidth(label: String, glyphPx: Float, weight: FontWeight = FontWeight.Normal): Float {
    var width = 0f
    var lastChar: Char? = null
    label.forEach { char ->
        if (char != '\n') {
            width += advanceFor(char, glyphPx, weight)
            lastChar = char
        }
    }
    val trailingChar = lastChar ?: return width
    val glyph = glyphFor(trailingChar, weight) ?: return width
    val lastAdvance = advanceFor(trailingChar, glyphPx, weight)
    val lastGlyphRightEdge = (width - lastAdvance) + (glyph.offsetXEm + glyph.widthEm) * glyphPx
    return maxOf(width, lastGlyphRightEdge)
}
