/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.unicode.CodePoints
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The gate for `tools/fonts-tooling/subset_roboto_outlines.py`: the outlines [RobotoOutlines] embeds are the
 * Roboto faces' own, glyph for glyph, so pixel glyphs draw the font the atlases were made from.
 */
class RobotoOutlinesTest {
    private val faces = mapOf(
        "Roboto-Thin.ttf" to { RobotoOutlines.thin },
        "Roboto-Light.ttf" to { RobotoOutlines.light },
        "Roboto-Regular.ttf" to { RobotoOutlines.regular },
        "Roboto-Medium.ttf" to { RobotoOutlines.medium },
        "Roboto-SemiBold.ttf" to { RobotoOutlines.semiBold },
        "Roboto-Bold.ttf" to { RobotoOutlines.bold },
        "Roboto-Black.ttf" to { RobotoOutlines.black },
    )

    @Test
    fun everyAtlasGlyphHasTheFacesOwnOutlineAndAdvance() = runTest {
        faces.forEach { (file, embedded) ->
            val full = OpenTypeFont(readResourceBytes(file), file)
            val subset = embedded()
            assertEquals(full.unitsPerEm, subset.unitsPerEm, file)
            assertEquals(full.ascender, subset.ascender, file)
            assertEquals(full.descender, subset.descender, file)
            var index = 0
            while (index < UiFonts.DEFAULT_CHARS.length) {
                val code = CodePoints.at(UiFonts.DEFAULT_CHARS, index)
                val fullGlyph = full.glyphFor(code)
                val subsetGlyph = subset.glyphFor(code)
                val what = "$file U+${code.toString(16)}"
                assertEquals(full.advance(fullGlyph), subset.advance(subsetGlyph), what)
                val want = full.outline(fullGlyph)
                val got = subset.outline(subsetGlyph)
                assertEquals(want.size, got.size, "$what segments")
                want.indices.forEach { segment ->
                    assertEquals(want[segment].isLine, got[segment].isLine, "$what segment $segment")
                    assertContentEquals(want[segment].points, got[segment].points, "$what segment $segment")
                }
                index += CodePoints.charCount(code)
            }
        }
    }
}
