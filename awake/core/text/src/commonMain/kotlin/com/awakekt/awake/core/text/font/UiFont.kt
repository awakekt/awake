/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.unicode.CodePoints
import com.awakekt.awake.core.text.unicode.GraphemeClusters

/**
 * Specifies the texture sampling mode used by the font renderer.
 */
enum class UiFontSamplingMode {
    /** Alpha coverage sampling where alpha directly represents glyph opacity. */
    CoverageAlpha,

    /** Multi-channel or signed distance field sampling for sharp rendering across scales. */
    DistanceField,

    /** Full-colour RGBA sampling for colour emoji and pre-rendered graphic glyphs. */
    ColorRgba,
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

    /**
     * Whether this font draws [char] itself, rather than a stand-in such as `'?'`.
     *
     * A `Char` holds only the Basic Multilingual Plane, so this cannot be asked about an emoji or a
     * CJK Extension B ideograph; the code point overload can, and is the one [UiFonts.fallback]
     * resolves faces by.
     *
     * @param char The character to look up.
     * @return True when the font has its own glyph for [char].
     */
    fun hasGlyph(char: Char): Boolean = uvFor(char) != null

    /**
     * Whether this font draws the Unicode code point [codePoint] itself, rather than a stand-in.
     *
     * A [UiFonts.fallback] chain asks each face in turn and draws the code point from the first
     * that has it. The default answers [hasGlyph] for a code point in the Basic Multilingual Plane
     * and false for any other, or for a surrogate; a font whose glyphs reach past U+FFFF overrides it.
     *
     * @param codePoint The code point to look up, such as `0x1F600` for 😀.
     * @return True when the font has its own glyph for [codePoint].
     */
    fun hasGlyph(codePoint: Int): Boolean = codePoint.toBmpCharOrNull()?.let(::hasGlyph) ?: false

    /** Glyph atlas entry for a requested weight. Single-face fonts fall back to [uvFor]; a
     * weighted family resolves the requested face and returns its atlas slice. */
    fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = uvFor(char)

    /**
     * Glyph atlas entry for the code point [codePoint] at a requested weight, or the font's
     * stand-in when it has no glyph of its own (see [hasGlyph]).
     *
     * The default looks a Basic Multilingual Plane code point up as a `Char`, and anything else as
     * U+FFFD REPLACEMENT CHARACTER, which a font without it draws as its own stand-in.
     */
    fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        glyphFor(codePoint.toBmpCharOrNull() ?: REPLACEMENT_CHARACTER, weight)

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

    /**
     * Advance in pixels of the code point [codePoint] at [glyphPx] and a requested weight: the
     * advance of the glyph [glyphFor] returns for it, stand-in included.
     */
    fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float =
        advanceFor(codePoint.toBmpCharOrNull() ?: REPLACEMENT_CHARACTER, glyphPx, weight)

    /**
     * Resolves the underlying [UiFont] that provides the atlas for [codePoint] at [weight].
     *
     * Single-face fonts and families sharing an atlas return 	his.
     * A fallback chain containing faces with disparate sampling modes (such as colour emoji)
     * returns the specific face that owns the glyph.
     */
    fun fontFor(codePoint: Int, weight: FontWeight = FontWeight.Normal): UiFont = this
}

/** U+FFFD, what a code point a `Char` cannot hold is looked up as. */
private const val REPLACEMENT_CHARACTER = '\uFFFD'

/** This code point as a `Char`, or null outside the Basic Multilingual Plane or for a surrogate. */
private fun Int.toBmpCharOrNull(): Char? =
    if (this in 0..0xFFFF && this !in SURROGATES) toChar() else null

private val SURROGATES = 0xD800..0xDFFF

/**
 * Walks the glyphs this font draws for one grapheme cluster, `text[start until end]`, and returns
 * the cluster's advance in pixels at [glyphPx].
 *
 * [action] gets each glyph's code point and its pen offset from the cluster's start. The first code
 * point always draws, as the font's stand-in when it has no glyph for it, and a cluster whose first
 * code point is missing draws nothing else: an emoji sequence this font cannot draw is one stand-in,
 * however many code points it joins. After a first code point the font has, each further one draws
 * only if the font has it too, so a combining mark sits after its base and a joiner or variation
 * selector the font lacks draws nothing.
 *
 * Measurement, painting, the caret and hit testing all step through text one cluster at a time with
 * this, so none of them can split a cluster or disagree about its width. [start] and [end] must be
 * cluster boundaries ([GraphemeClusters]).
 */
inline fun UiFont.forEachGlyphInCluster(
    text: CharSequence,
    start: Int,
    end: Int,
    glyphPx: Float,
    weight: FontWeight = FontWeight.Normal,
    action: (codePoint: Int, penX: Float) -> Unit,
): Float {
    val first = CodePoints.at(text, start)
    action(first, 0f)
    var pen = advanceFor(first, glyphPx, weight)
    var index = start + CodePoints.charCount(first)
    if (index >= end || !hasGlyph(first)) return pen
    while (index < end) {
        val codePoint = CodePoints.at(text, index)
        if (hasGlyph(codePoint)) {
            action(codePoint, pen)
            pen += advanceFor(codePoint, glyphPx, weight)
        }
        index += CodePoints.charCount(codePoint)
    }
    return pen
}

/** The advance in pixels of the grapheme cluster `text[start until end]`; see [forEachGlyphInCluster]. */
fun UiFont.clusterAdvance(
    text: CharSequence,
    start: Int,
    end: Int,
    glyphPx: Float,
    weight: FontWeight = FontWeight.Normal,
): Float = forEachGlyphInCluster(text, start, end, glyphPx, weight) { _, _ -> }

/**
 * Sums each grapheme cluster's advance ([forEachGlyphInCluster]) to get the pen distance the
 * string travels -- but a glyph's own ink (its [GlyphRect.offsetXEm]/[GlyphRect.widthEm] quad)
 * can extend past its own advance; several packed fonts (e.g. the embedded Roboto data) declare advances a few percent narrower
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
    var lastCodePoint = -1
    var lastPen = 0f
    GraphemeClusters.forEach(label) { start, end ->
        if (label[start] != '\n' && label[end - 1] != '\n') {
            val clusterStart = width
            width += forEachGlyphInCluster(label, start, end, glyphPx, weight) { codePoint, penX ->
                lastCodePoint = codePoint
                lastPen = clusterStart + penX
            }
        }
    }
    if (lastCodePoint < 0) return width
    val glyph = glyphFor(lastCodePoint, weight) ?: return width
    val lastGlyphRightEdge = lastPen + (glyph.offsetXEm + glyph.widthEm) * glyphPx
    return maxOf(width, lastGlyphRightEdge)
}
