/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFonts
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Which code points the built-in font, `UiFonts.default()`, draws in Regular, Bold and Black, and what it
 * lacks (#1055). The lists are read off the font's atlas, scanning U+0000 to U+10FFFF, not spelled out
 * here, so a wider built-in set extends them with no change to this test.
 *
 * - Every char of [UiFonts.DEFAULT_CHARS], the set the engine declares the atlas holds, draws as its own
 *   glyph in every weight, in the atlas and as a pixel glyph; one that draws as '?' fails.
 * - Every weight covers the same code points as Regular, and each of them is its own pixel glyph too.
 * - The listing of what is covered and, per Unicode block, what is not is held against
 *   `src/desktopTest/resources/glyph-coverage/built-in-font.txt`, re-recorded with
 *   `-DAWAKE_RECORD_SNAPSHOTS=true` together with the specimen baselines when the set changes.
 *
 * Since #1058 the set is U+0020–U+007E (printable ASCII), U+00A0–U+017F (Latin-1 Supplement and Latin Extended-A),
 * U+2013–U+2014, U+2018–U+201A, U+201C–U+201E, U+2020–U+2022, U+2026, U+2030, U+2032–U+2033, U+2039–U+203A,
 * U+20AC, U+2122 and U+2212: 339 code points, the same in all three weights. Before it, on f79bb9dd1, it was
 * the 102 of printable ASCII, U+00B7, U+00D7, U+2013, U+2014, U+2022, U+2026 and U+2212, and every accented
 * letter, curly quote and currency sign but $ drew as '?'. It still lacks Greek, Cyrillic, Vietnamese's stacked
 * accents, arrows, most symbols and U+FFFD; those draw as '?'.
 *
 * Negative control, recorded on f79bb9dd1: with '~' replaced by U+00A0 in `RobotoBoldUiFontData.glyphOrder`,
 * [everyDeclaredGlyphDrawsInEveryWeight] failed with "Bold no longer draws U+007E at 12 px; it draws it as
 * '?'", [everyWeightCoversWhatRegularCovers] and [coverageListingMatchesTheRecordedOne] failed on Bold's
 * "U+0020–U+007D, U+00A0, ...", and every specimen sheet grew a seventh row for the U+00A0 Bold gained.
 * On 8a5791fc1, while the specimen's cells were the declared set alone, the same edit changed the four Bold
 * sheets in cell 94, '~', at each size and nowhere else. [aGlyphMissingFromOneWeightIsCaught] keeps a control in the suite.
 */
class BuiltInFontCoverageTest {
    private val font = UiFonts.default()
    private val coverage by lazy { BuiltInGlyphs.weights.associateWith { BuiltInGlyphs.covered(font, it) } }

    @Test
    fun everyDeclaredGlyphDrawsInEveryWeight() {
        for (weight in BuiltInGlyphs.weights) {
            for (pixelSize in listOf(12, 18, 24)) {
                val lost = BuiltInGlyphs.declared.filterNot { BuiltInGlyphs.draws(font, weight, it, pixelSize) }
                assertEquals(
                    emptyList(),
                    lost.map(BuiltInGlyphs::hex),
                    "${name(weight)} no longer draws ${BuiltInGlyphs.ranges(lost)} at $pixelSize px; it draws it as '?'",
                )
            }
        }
    }

    @Test
    fun everyWeightCoversWhatRegularCovers() {
        val regular = coverage.getValue(FontWeight.Normal)
        for (weight in BuiltInGlyphs.weights) {
            val covered = coverage.getValue(weight)
            assertEquals(BuiltInGlyphs.ranges(regular), BuiltInGlyphs.ranges(covered), "${name(weight)} against Regular")
            val notPixel = covered.filterNot { cp -> listOf(12, 18, 24).all { BuiltInGlyphs.draws(font, weight, cp, it) } }
            assertEquals(emptyList(), notPixel.map(BuiltInGlyphs::hex), "${name(weight)}: covered in the atlas, '?' as a pixel glyph")
        }
    }

    @Test
    fun coverageListingMatchesTheRecordedOne() {
        val actual = listing()
        val file = File(LISTING_PATH)
        if (System.getProperty(RECORD_PROPERTY) != null) {
            file.parentFile?.mkdirs()
            file.writeText(actual)
            println("RECORDED $LISTING_PATH")
            return
        }
        val expected = javaClass.getResourceAsStream(LISTING_RESOURCE)?.use { it.readBytes().decodeToString() }
            ?: fail("No $LISTING_PATH. Record it with -D$RECORD_PROPERTY=true and commit it.")
        assertEquals(expected, actual, "The built-in font's coverage changed; re-record $LISTING_PATH once the change is intended")
    }

    /**
     * The check itself fails on a family missing one glyph in one weight: '~' baked into Regular and Black
     * but not Bold. `hasGlyph`, which asks the regular face, would miss it.
     */
    @Test
    fun aGlyphMissingFromOneWeightIsCaught() {
        fun load(file: String, chars: String) = UiFonts.load(runBlocking { readResourceBytes(file) }, file, chars)
        val all = UiFonts.DEFAULT_CHARS
        val family = UiFonts.family(
            mapOf(
                FontWeight.Normal to load("Roboto-Regular.ttf", all),
                FontWeight.Bold to load("Roboto-Bold.ttf", all.replace("~", "")),
                FontWeight.Black to load("Roboto-Black.ttf", all),
            ),
        )
        val missing = BuiltInGlyphs.weights.associate { name(it) to BuiltInGlyphs.missing(family, it).map(BuiltInGlyphs::hex) }
        assertEquals(mapOf("Regular" to emptyList(), "Bold" to listOf("U+007E"), "Black" to emptyList()), missing)
        assertEquals(true, family.hasGlyph('~'), "hasGlyph asks the regular face only")
    }

    /** Covered code points per weight, then each reference block's covered count and what it lacks. */
    private fun listing(): String = buildString {
        appendLine("# The built-in font, UiFonts.default(): code points each weight draws as its own glyph.")
        appendLine("# Generated by BuiltInFontCoverageTest; re-record with -D$RECORD_PROPERTY=true.")
        appendLine()
        for ((weight, covered) in coverage) {
            appendLine("${name(weight)}: ${covered.size} code points: ${BuiltInGlyphs.ranges(covered)}")
        }
        appendLine()
        appendLine("# Per block, out of every code point in it, assigned or not; everything lacking draws as '?'.")
        val regular = coverage.getValue(FontWeight.Normal).toSet()
        for (block in BLOCKS) {
            val lacks = block.range.filterNot { it in regular }
            val range = "${BuiltInGlyphs.hex(block.range.first)}–${BuiltInGlyphs.hex(block.range.last)}"
            appendLine("${block.name} ($range): ${block.range.count() - lacks.size} of ${block.range.count()}")
            if (lacks.isNotEmpty()) appendLine("  lacks ${BuiltInGlyphs.ranges(lacks)}")
            val shown = lacks.filter { it in block.printable }
            if (shown.isNotEmpty()) appendLine("  ${shown.joinToString("") { String(Character.toChars(it)) }}")
        }
    }

    private class Block(val name: String, val range: IntRange, val printable: List<Int> = emptyList())

    private fun name(weight: FontWeight): String = when (weight) {
        FontWeight.Bold -> "Bold"
        FontWeight.Black -> "Black"
        else -> "Regular"
    }

    private companion object {
        const val RECORD_PROPERTY = "AWAKE_RECORD_SNAPSHOTS"
        const val LISTING_RESOURCE = "/glyph-coverage/built-in-font.txt"
        const val LISTING_PATH = "src/desktopTest/resources$LISTING_RESOURCE"

        /**
         * The Unicode blocks a UI is most likely to need, so the listing says how far the subset reaches
         * into each. Only code points stable since Unicode 1.1 are printed as characters, so the listing
         * reads the same whatever Unicode version the test JVM knows.
         */
        val BLOCKS = listOf(
            Block("Basic Latin", 0x0000..0x007F),
            Block("Latin-1 Supplement", 0x0080..0x00FF, printable = (0x00A1..0x00FF) - 0x00AD),
            Block("Latin Extended-A", 0x0100..0x017F, printable = (0x0100..0x017F).toList()),
            Block("Greek and Coptic", 0x0370..0x03FF),
            Block("Cyrillic", 0x0400..0x04FF),
            Block("General Punctuation", 0x2000..0x206F),
            Block("Currency Symbols", 0x20A0..0x20CF),
            Block("Letterlike Symbols", 0x2100..0x214F),
            Block("Arrows", 0x2190..0x21FF),
            Block("Mathematical Operators", 0x2200..0x22FF),
            Block("Specials", 0xFFF0..0xFFFF),
        )
    }
}
