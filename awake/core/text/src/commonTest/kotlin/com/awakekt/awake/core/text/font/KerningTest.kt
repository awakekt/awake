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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pair kerning as [KerningPairs] reads it: Roboto's `GPOS` `kern` feature (PairPos formats 1 and 2) for the
 * built-in family and for a loaded face, and a format 0 `kern` table.
 *
 * Expected values are in font units (Roboto has 2048 to the em) and come from the font tools, not from this
 * reader: fontTools 4.67 reading the `GPOS` and `kern` tables, and HarfBuzz 15.0 (uharfbuzz 0.57) shaping
 * each pair with and without kerning. The two agree on every one of the 114,921 pairs of the atlas's 339
 * characters, for every weight, but 14 that end in U+00AD SOFT HYPHEN, which HarfBuzz treats as a default-ignorable
 * code point and draws as nothing, so kerns none of them; the font's own tables kern those, as this reader does.
 * The pair counts below are fontTools'.
 */
class KerningTest {
    private val weights = listOf(
        Face("Roboto-Thin.ttf", FontWeight.Thin, RobotoThinUiFontData, 6957) { RobotoOutlines.thin },
        Face("Roboto-Light.ttf", FontWeight.Light, RobotoLightUiFontData, 6957) { RobotoOutlines.light },
        Face("Roboto-Regular.ttf", FontWeight.Normal, RobotoRegularUiFontData, 6957) { RobotoOutlines.regular },
        Face("Roboto-Medium.ttf", FontWeight.Medium, RobotoMediumUiFontData, 7038) { RobotoOutlines.medium },
        Face("Roboto-SemiBold.ttf", FontWeight.SemiBold, RobotoSemiBoldUiFontData, 7038) { RobotoOutlines.semiBold },
        Face("Roboto-Bold.ttf", FontWeight.Bold, RobotoBoldUiFontData, 7038) { RobotoOutlines.bold },
        Face("Roboto-Black.ttf", FontWeight.Black, RobotoBlackUiFontData, 7019) { RobotoOutlines.black },
    )

    /**
     * Sample pairs, in font units for Regular and Bold. ` T` and `Fa` come from a PairPos format 1 subtable,
     * the rest from format 2 class pairs; `//` comes from the second `kern` lookup alone, and `."` from the
     * second after the first gave its class pair 0, so lookups add up and a format 2 subtable decides a
     * covered pair even with no value for it. `HH` and `rr` are not kerned.
     */
    private val samples = mapOf(
        FontWeight.Normal to mapOf(
            "AV" to -87, "VA" to -75, "AT" to -129, "To" to -99, "Ty" to -72, "Yo" to -65, "WA" to -43,
            "LT" to -275, "P." to -324, " T" to -40, "Fa" to -34, "//" to -224, ".\"" to -170, "''" to -107,
            "HH" to 0, "rr" to 0,
            // Accented letters kern as their bases do, and have pairs of their own (#1058).
            "ÁV" to -87, "VÁ" to -75, "TÓ" to -28, "Yö" to -65, "PÅ" to -138, "Té" to -99, "Ťo" to -99, "ÇT" to -29, "ŁT" to 0,
        ),
        FontWeight.Bold to mapOf(
            "AV" to -80, "VA" to -75, "AT" to -123, "To" to -173, "Ty" to -79, "Yo" to -65, "WA" to -43,
            "LT" to -228, "P." to -378, " T" to -54, "Fa" to -34, "//" to -240, ".\"" to -248, "''" to -60,
            "HH" to 0, "rr" to 0,
            "ÁV" to -80, "VÁ" to -75, "TÓ" to -28, "Yö" to -65, "PÅ" to -165, "Té" to -92, "Ťo" to -173, "ÇT" to -29, "ŁT" to 0,
        ),
    )

    @Test
    fun builtInRobotoKernsTheFontToolsPairs() {
        val family = UiFonts.weightedSans()
        samples.forEach { (weight, pairs) ->
            pairs.forEach { (pair, units) ->
                assertEquals(units.toFloat(), family.kerningFor(pair[0].code, pair[1].code, UNITS_PER_EM, weight), "$weight '$pair'")
            }
        }
    }

    @Test
    fun everyBuiltInWeightKernsAsManyPairsAsTheFontTools() {
        weights.forEach { face ->
            assertEquals(face.pairs, builtIn(face).kerningPairCount, face.file)
        }
    }

    /** The gate for `subset_roboto_outlines.py`: the subset kerns every pair of atlas characters as the full face does. */
    @Test
    fun builtInPairsAreTheFullFacesOwn() = runTest {
        val codes = codePoints(UiFonts.DEFAULT_CHARS)
        weights.forEach { face ->
            val full = KerningPairs.read(OpenTypeFont(readResourceBytes(face.file), face.file), codes)
            val embedded = KerningPairs.read(face.outlines(), codes)
            assertEquals(face.pairs, full.size, face.file)
            for (left in codes) {
                for (right in codes) {
                    assertEquals(full.em(left, right), embedded.em(left, right), "${face.file} U+${left.toString(16)} U+${right.toString(16)}")
                }
            }
        }
    }

    @Test
    fun aLoadedFaceKernsAsTheBuiltInOne() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Medium.ttf"), "Roboto-Medium.ttf")
        val builtIn = builtIn(weights.first { it.weight == FontWeight.Medium })
        val codes = codePoints(UiFonts.DEFAULT_CHARS)
        for (left in codes) {
            for (right in codes) assertEquals(builtIn.kerningFor(left, right, 14f), loaded.kerningFor(left, right, 14f), "U+$left U+$right")
        }
        assertEquals(-83f / UNITS_PER_EM * 14f, loaded.kerningFor('A'.code, 'V'.code, 14f))
    }

    /** `roboto-kern.ttf` has no `GPOS`: its 63 pairs are a format 0 `kern` table, as fontTools wrote them. */
    @Test
    fun aKernTableFormat0IsRead() = runTest {
        val font = UiFonts.load(readResourceBytes("fonts/roboto-kern.ttf"), "roboto-kern.ttf", KERN_CHARS) as PackedUiFont
        assertEquals(63, font.kerningPairCount)
        val expected = mapOf("AV" to -87, "T." to -218, "TT" to 16, "ry" to 18, "Yo" to -65, "//" to -224, " T" to -40, "AA" to 0, "oo" to 0)
        expected.forEach { (pair, units) ->
            assertEquals(units.toFloat(), font.kerningFor(pair[0].code, pair[1].code, UNITS_PER_EM), "'$pair'")
        }
        // Each pair is the value Roboto's own GPOS gives it.
        val regular = UiFonts.trueSans()
        for (left in KERN_CHARS) {
            for (right in KERN_CHARS) {
                assertEquals(regular.kerningFor(left.code, right.code, 14f), font.kerningFor(left.code, right.code, 14f), 1e-6f, "'$left$right'")
            }
        }
    }

    @Test
    fun aFontWithoutKerningKernsNothing() = runTest {
        val font = UiFonts.load(readResourceBytes("fonts/roboto-subset.ttf"), "roboto-subset.ttf", " Hg.:?Ä") as PackedUiFont
        assertEquals(0, font.kerningPairCount)
        assertEquals(0f, UiFonts.bitmap().kerningFor('A'.code, 'V'.code, 14f))
        assertEquals(0f, UiFonts.trueSans().kerningFor('A'.code, 0x4E00, 14f), "a code point the font does not draw")
    }

    @Test
    fun aCorruptKernTableFailsTheLoadNamingIt() = runTest {
        val bytes = readResourceBytes("fonts/roboto-kern.ttf")
        val kern = tableOffset(bytes, "kern")
        // The format 0 subtable's pair count, past what the table holds.
        bytes[kern + 4 + 6] = 0x7F
        val error = assertFailsWith<IllegalArgumentException> { UiFonts.load(bytes, "broken.ttf", KERN_CHARS) }
        assertTrue("'kern' table" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aFallbackChainKernsOnlyWithinAFace() = runTest {
        val roboto = UiFonts.trueSans()
        val onlyA = UiFonts.load(readResourceBytes("fonts/roboto-kern.ttf"), "roboto-kern.ttf", "A")
        val chain = UiFonts.fallback(onlyA, listOf(roboto))
        assertEquals(0f, chain.kerningFor('A'.code, 'V'.code, 14f), "'A' draws from the primary, 'V' from the fallback")
        assertEquals(roboto.kerningFor('T'.code, 'o'.code, 14f), chain.kerningFor('T'.code, 'o'.code, 14f))
        assertTrue(chain.kerningFor('T'.code, 'o'.code, 14f) < 0f)
    }

    private fun builtIn(face: Face): PackedUiFont = PackedUiFont(face.data, lineMetrics = RobotoLineMetrics, outlines = face.outlines)

    private fun codePoints(text: String): IntArray {
        val codes = ArrayList<Int>()
        CodePoints.forEach(text) { _, code -> codes += code }
        return codes.toIntArray()
    }

    /** Where the table [tag] starts in a single-font file. */
    private fun tableOffset(bytes: ByteArray, tag: String): Int {
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF shl 8) or (bytes[at + 1].toInt() and 0xFF)
        for (index in 0 until u16(4)) {
            val record = 12 + 16 * index
            if ((0..3).all { bytes[record + it].toInt().toChar() == tag[it] }) return (u16(record + 8) shl 16) or u16(record + 10)
        }
        error("no '$tag' table")
    }

    private class Face(val file: String, val weight: FontWeight, val data: PackedUiFontData, val pairs: Int, val outlines: () -> OpenTypeFont)

    private companion object {
        /** Roboto's em, so a pair's kerning at this many pixels is its value in font units. */
        const val UNITS_PER_EM = 2048f

        /** The characters of `roboto-kern.ttf`: `make_test_fonts.py`'s KERN_TEXT. */
        const val KERN_CHARS = " AVTWYaeory.,/"
    }
}
