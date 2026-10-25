/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts

/**
 * Which glyphs the built-in font (`UiFonts.default()`) draws, read off the font itself, and how to tell a
 * glyph it draws from one it stands in for. Shared by [BuiltInFontCoverageTest] and
 * [GlyphSpecimenBaselineTest].
 */
internal object BuiltInGlyphs {
    /** The weights the specimen and the coverage test hold the built-in font to. */
    val weights: List<FontWeight> = listOf(FontWeight.Normal, FontWeight.Bold, FontWeight.Black)

    /**
     * The set the engine declares the built-in atlas holds, [UiFonts.DEFAULT_CHARS], in its order. Each of
     * these must draw in every weight; one the font loses keeps its cell in the specimen, drawn as '?'.
     */
    val declared: List<Int> = UiFonts.DEFAULT_CHARS.codePoints().toArray().toList()

    /**
     * What a code point the font lacks draws as: '?', the bundled atlases' fallback char. It is its own
     * stand-in, so it counts as drawn when the font has a glyph for it at all.
     */
    const val STAND_IN: Int = '?'.code

    /** A noncharacter no font maps, so looking it up always returns the stand-in. */
    private const val NEVER_MAPPED: Int = 0xFFFF

    private const val LAST_CODE_POINT: Int = 0x10FFFF
    private val surrogates = 0xD800..0xDFFF

    /**
     * The specimen's cells, in order: [declared], then every other code point any weight of [font] draws,
     * ascending. A glyph the atlas gains gets a cell of its own without this test changing.
     */
    fun specimen(font: UiFont): List<Int> {
        val extra = weights.flatMap { covered(font, it) }.toSortedSet() - declared.toSet()
        return declared + extra
    }

    /**
     * Whether [font] at [weight] draws [codePoint] as a glyph of its own rather than the stand-in, in both
     * of the ways text draws: the distance-field atlas and, when the font has them, the pixel glyphs
     * small text uses ([pixelSize] device pixels).
     *
     * Asked per weight, not through [UiFont.hasGlyph], which a weighted family answers from its regular
     * face alone: a glyph missing from Bold only would pass that.
     */
    fun draws(font: UiFont, weight: FontWeight, codePoint: Int, pixelSize: Int = PROBE_PIXEL_SIZE): Boolean {
        if (codePoint == STAND_IN) return font.glyphFor(codePoint, weight) != null
        val atlas = font.glyphFor(codePoint, weight)
        val inAtlas = atlas != null && atlas != font.glyphFor(NEVER_MAPPED, weight)
        val pixels = font.pixelGlyphs
        return inAtlas && (pixels == null || pixels.glyph(codePoint, weight, pixelSize) !== pixels.glyph(NEVER_MAPPED, weight, pixelSize))
    }

    /** The code points of [declared] that [font] at [weight] does not draw ([draws]). */
    fun missing(font: UiFont, weight: FontWeight): List<Int> = declared.filterNot { draws(font, weight, it) }

    /** The code points [font] draws at [weight] in its atlas, ascending, scanning all of Unicode. */
    fun covered(font: UiFont, weight: FontWeight): List<Int> {
        val standIn = font.glyphFor(NEVER_MAPPED, weight)
        return (0..LAST_CODE_POINT).filter { codePoint ->
            codePoint !in surrogates &&
                (if (codePoint == STAND_IN) font.glyphFor(codePoint, weight) != null else font.glyphFor(codePoint, weight) != standIn)
        }
    }

    /** [codePoints] as `U+0020–U+007E, U+00B7, ...`: runs of consecutive code points collapsed. */
    fun ranges(codePoints: Collection<Int>): String {
        val runs = mutableListOf<IntRange>()
        for (codePoint in codePoints.sorted()) {
            val last = runs.lastOrNull()
            if (last != null && last.last + 1 == codePoint) runs[runs.lastIndex] = last.first..codePoint else runs += codePoint..codePoint
        }
        if (runs.isEmpty()) return "none"
        return runs.joinToString { if (it.first == it.last) hex(it.first) else "${hex(it.first)}–${hex(it.last)}" }
    }

    fun hex(codePoint: Int): String = "U+" + codePoint.toString(HEX).uppercase().padStart(HEX_DIGITS, '0')

    /** A size small text draws from pixel glyphs at. */
    private const val PROBE_PIXEL_SIZE = 12
    private const val HEX = 16
    private const val HEX_DIGITS = 4
}
