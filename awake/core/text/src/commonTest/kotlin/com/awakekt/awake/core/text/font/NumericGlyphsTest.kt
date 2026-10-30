/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class NumericGlyphsTest {
    @Test
    fun robotoNumeralsFixtureSubstitutesTabularDigitsThroughFormatsOneTwoAndExtension() = runTest {
        val bytes = readResourceBytes("fonts/roboto-numerals.ttf")
        val loaded = UiFonts.load(bytes, "roboto-numerals.ttf", chars = " ?0123456789:")

        assertSame(loaded, loaded.withNumerals(null))
        assertEquals(889f / 2048f * 16f, loaded.advanceFor('1', 16f), 1e-4f)
        assertEquals(1191f / 2048f * 16f, loaded.advanceFor('0', 16f), 1e-4f)
        assertTrue(loaded.advanceFor('1', 16f) < loaded.advanceFor('0', 16f))

        val tabular = loaded.withNumerals(FontNumerals.Tabular)
        assertSame(tabular, loaded.withNumerals(FontNumerals.Tabular))
        assertSame(tabular, tabular.withNumerals(FontNumerals.Tabular))
        val expectedAdvance = 1151f / 2048f * 16f
        for (digit in '0'..'9') {
            assertEquals(expectedAdvance, tabular.advanceFor(digit, 16f), 1e-5f, "tabular '$digit'")
        }
        assertNotEquals(loaded.uvFor('1'), tabular.uvFor('1'), "distance-field '1' swaps to its tabular glyph")
        val defaultPixelOne = checkNotNull(loaded.pixelGlyphs?.glyph('1'.code, FontWeight.Normal, 24))
        val tabularPixelOne = checkNotNull(tabular.pixelGlyphs?.glyph('1'.code, FontWeight.Normal, 24))
        assertTrue(defaultPixelOne.atlas !== tabularPixelOne.atlas)
        assertTrue(
            tabularPixelOne.left > defaultPixelOne.left,
            "tabular '1' pixel glyph sits wider in its equal-width cell (${tabularPixelOne.left} vs ${defaultPixelOne.left})",
        )
        assertTrue(tabular.atlasPixelsRgba.isNotEmpty())
    }

    @Test
    fun builtInRobotoFacesAdvanceEveryDigitEquallyUnderTabularAndNarrowlyUnderProportional() {
        val family = UiFonts.default()
        val singleFace = UiFonts.trueSans()
        val bitmap = UiFonts.bitmap()

        assertSame(family, family.withNumerals(null))
        assertSame(singleFace, singleFace.withNumerals(null))
        assertSame(bitmap, bitmap.withNumerals(FontNumerals.Tabular))

        val tabular = family.withNumerals(FontNumerals.Tabular)
        val proportional = family.withNumerals(FontNumerals.Proportional)
        assertSame(tabular, family.withNumerals(FontNumerals.Tabular))
        assertSame(tabular, tabular.withNumerals(FontNumerals.Tabular))
        assertSame(proportional, tabular.withNumerals(FontNumerals.Proportional))

        val weights = listOf(
            FontWeight.Thin,
            FontWeight.Light,
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.Black,
        )
        for (weight in weights) {
            val zeroTabular = tabular.advanceFor('0'.code, 16f, weight)
            for (digit in '1'..'9') {
                assertEquals(zeroTabular, tabular.advanceFor(digit.code, 16f, weight), "tabular '$digit' in $weight")
            }
            val zeroProp = proportional.advanceFor('0'.code, 16f, weight)
            val oneProp = proportional.advanceFor('1'.code, 16f, weight)
            assertTrue(oneProp < zeroProp, "proportional '1' ($oneProp) is narrower than '0' ($zeroProp) in $weight")
            assertNotEquals(
                tabular.glyphFor('1'.code, weight),
                proportional.glyphFor('1'.code, weight),
                "distance-field glyph for '1' differs in $weight",
            )
            val tabPx = checkNotNull(tabular.pixelGlyphs?.glyph('1'.code, weight, 14))
            val propPx = checkNotNull(proportional.pixelGlyphs?.glyph('1'.code, weight, 14))
            assertTrue(propPx.width > 0 && tabPx.width > 0)
        }

        val fallback = UiFonts.fallback(singleFace, listOf(bitmap))
        val fallbackProp = fallback.withNumerals(FontNumerals.Proportional)
        assertSame(fallbackProp, fallback.withNumerals(FontNumerals.Proportional))
        assertSame(fallbackProp, fallbackProp.withNumerals(FontNumerals.Proportional))
        assertTrue(fallbackProp.advanceFor('1', 16f) < fallbackProp.advanceFor('0', 16f))

        val limited = family.withPixelGlyphMaxSize(0)
        val limitedProp = limited.withNumerals(FontNumerals.Proportional)
        assertSame(limitedProp, limited.withNumerals(FontNumerals.Proportional))
        assertSame(limitedProp, limitedProp.withNumerals(FontNumerals.Proportional))
        assertEquals(0, limitedProp.pixelGlyphMaxSize)
        assertTrue(limitedProp.advanceFor('1', 16f) < limitedProp.advanceFor('0', 16f))
    }

    @Test
    fun corruptGsubTableKeepsTheFontAndItsDefaultDigits() = runTest {
        val original = readResourceBytes("fonts/roboto-numerals.ttf")
        val gsubOffset = tableOffset(original, "GSUB")

        // Corrupt LookupListOffset inside the GSUB header so it points past the table end.
        val badHeader = original.copyOf()
        badHeader[gsubOffset + 8] = 0x7F
        badHeader[gsubOffset + 9] = 0xFF.toByte()
        val warnings = collectWarnings {
            val loaded = UiFonts.load(badHeader, "corrupt-gsub.ttf", chars = DIGITS)
            assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
            assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
            assertSame(loaded, loaded.withNumerals(FontNumerals.Proportional))
            assertEquals(ONE_ADVANCE, loaded.advanceFor('1', 16f), 1e-4f)
        }
        assertEquals(2, warnings.size, "one warning per feature, however often it is asked for: $warnings")
        assertTrue(warnings.all { "'GSUB' table" in it }, "each warning names the table: $warnings")
        assertTrue(warnings.any { "'tnum'" in it } && warnings.any { "'pnum'" in it }, "and the feature: $warnings")
    }

    @Test
    fun aGsubWithoutNumericFeaturesKeepsTheBaseFont() = runTest {
        val (original, _) = fixture()
        val gsub = gsub(features = listOf("liga" to listOf(0)), lookups = listOf(ligatureLookup))
        val loaded = UiFonts.load(withTable(original, "GSUB", gsub), "liga-only.ttf", chars = DIGITS)

        assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
        assertSame(loaded, loaded.withNumerals(FontNumerals.Proportional))
    }

    @Test
    fun anUnsupportedLookupTypeUnderTnumIsSkipped() = runTest {
        val (original, _) = fixture()
        val gsub = gsub(features = listOf("tnum" to listOf(0)), lookups = listOf(ligatureLookup))
        val warnings = collectWarnings {
            val loaded = UiFonts.load(withTable(original, "GSUB", gsub), "tnum-ligature.ttf", chars = DIGITS)
            assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
        }
        assertEquals(emptyList(), warnings, "a lookup type this reader does not apply is not a fault")
    }

    @Test
    fun aMalformedTnumDropsOnlyThatFeature() = runTest {
        val (original, ids) = fixture()
        // 'pnum' maps '1' to '0'; 'tnum' names lookup 5 of 1.
        val gsub = gsub(
            features = listOf("pnum" to listOf(0), "tnum" to listOf(5)),
            lookups = listOf(singleDelta(ids.one, ids.zero - ids.one)),
        )
        val warnings = collectWarnings {
            val loaded = UiFonts.load(withTable(original, "GSUB", gsub), "bad-tnum.ttf", chars = DIGITS)
            assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
            val proportional = loaded.withNumerals(FontNumerals.Proportional)
            assertTrue(proportional !== loaded)
            assertEquals(ZERO_ADVANCE, proportional.advanceFor('1', 16f), 1e-4f, "'pnum' still applies")
        }
        assertEquals(1, warnings.size, "$warnings")
        assertTrue("names lookup 5" in warnings.single() && "'tnum'" in warnings.single(), warnings.single())
    }

    @Test
    fun scriptAndLanguageSystemTablesThatManyRecordsRepeatAreEachReadOnce() = runTest {
        val (original, ids) = fixture()
        // 10 000 script records all name one script table, whose 10 000 language-system records all name one
        // language system listing 'tnum' 65 535 times: about 6.5e12 steps when walked record by record.
        val scripts = 10_000
        val langSystems = 10_000
        val gsub = gsub(
            features = listOf("tnum" to listOf(0)),
            lookups = listOf(singleDelta(ids.one, ids.zero - ids.one)),
        ) {
            u16(scripts)
            repeat(scripts) {
                tag("latn")
                u16(2 + 6 * scripts)
            }
            u16(0)
            u16(langSystems)
            repeat(langSystems) {
                tag("DEU ")
                u16(4 + 6 * langSystems)
            }
            u16(0)
            u16(0xFFFF)
            u16(0xFFFF)
            repeat(0xFFFF) { u16(0) }
        }
        val started = TimeSource.Monotonic.markNow()
        val loaded = UiFonts.load(withTable(original, "GSUB", gsub), "repeated-offsets.ttf", chars = DIGITS)
        val tabular = loaded.withNumerals(FontNumerals.Tabular)
        val elapsed = started.elapsedNow()

        assertEquals(ZERO_ADVANCE, tabular.advanceFor('1', 16f), 1e-4f, "the feature, listed once, still applies")
        assertTrue(elapsed < 10.seconds, "read in $elapsed")
    }

    @Test
    fun overlappingLanguageSystemsStopAtTheRecordLimitAndDropTheFeature() = runTest {
        val (original, ids) = fixture()
        // 2 000 distinct language-system tables, each 2 bytes after the last over a run of 0xFFFF words, so each
        // claims 65 535 feature indices: 131 million records, which no visited set shortens.
        val langSystems = 2_000
        val gsub = gsub(
            features = listOf("tnum" to listOf(0)),
            lookups = listOf(singleDelta(ids.one, ids.zero - ids.one)),
        ) {
            u16(1)
            tag("latn")
            u16(8)
            u16(0)
            u16(langSystems)
            repeat(langSystems) {
                tag("DEU ")
                u16(4 + 6 * langSystems + 2 * it)
            }
            repeat(langSystems + 3 + 0xFFFF) { u16(0xFFFF) }
        }
        val warnings = collectWarnings {
            val loaded = UiFonts.load(withTable(original, "GSUB", gsub), "overlapping.ttf", chars = DIGITS)
            assertSame(loaded, loaded.withNumerals(FontNumerals.Tabular))
        }
        assertTrue("records" in warnings.single(), warnings.single())
    }

    @Test
    fun aVariantSharesItsBaseAtlasAndBakesOnlyTheSubstitutedFigures() = runTest {
        val loaded = UiFonts.load(readResourceBytes("fonts/roboto-numerals.ttf"), "roboto-numerals.ttf", chars = DIGITS)
        val tabular = loaded.withNumerals(FontNumerals.Tabular)

        assertSame(loaded.atlasPixelsRgba, tabular.atlasPixelsRgba, "the variant's atlas is the base's, not a copy")
        assertEquals(2, tabular.atlasPages.size)
        assertSame(loaded, tabular.atlasPages[0])
        val strip = tabular.atlasPages[1]
        assertSame(loaded, tabular.fontFor(':'.code), "a glyph the two share draws from the base's texture")
        assertEquals(loaded.glyphFor(':'.code, FontWeight.Normal), tabular.glyphFor(':'.code, FontWeight.Normal))
        assertSame(strip, tabular.fontFor('1'.code), "a substituted figure draws from the strip")
        assertEquals(strip.atlasWidth * strip.atlasHeight * 4, strip.atlasPixelsRgba.size)
        val one = assertNotNull(tabular.glyphFor('1'.code, FontWeight.Normal))
        assertTrue(one.u0 >= 0f && one.u1 <= 1f && one.v0 >= 0f && one.v1 <= 1f && one.u1 > one.u0 && one.v1 > one.v0, "$one")
        assertTrue(inkIn(strip, one), "the strip holds the tabular '1' where its UVs point")
    }

    @Test
    fun familyAndFallbackVariantsDrawFromTheOriginsAtlasPlusOneFigurePage() {
        val family = UiFonts.default()
        val proportional = family.withNumerals(FontNumerals.Proportional)

        assertSame(family.atlasPixelsRgba, proportional.atlasPixelsRgba, "the family variant copies no face atlas")
        assertEquals(2, proportional.atlasPages.size, "the family's atlas, then every weight's figures on one page")
        assertSame(family, proportional.atlasPages[0])
        val figures = proportional.atlasPages[1]
        assertTrue(
            figures.atlasPixelsRgba.size * 20 < family.atlasPixelsRgba.size,
            "figure page ${figures.atlasWidth}x${figures.atlasHeight} against the family's ${family.atlasWidth}x${family.atlasHeight}",
        )
        for (weight in listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Bold, FontWeight.Black)) {
            assertSame(family, proportional.fontFor('A'.code, weight))
            assertEquals(family.glyphFor('A'.code, weight), proportional.glyphFor('A'.code, weight))
            assertSame(figures, proportional.fontFor('1'.code, weight), "'1' in $weight")
            val one = assertNotNull(proportional.glyphFor('1'.code, weight))
            assertTrue(inkIn(figures, one), "the figure page holds $weight's proportional '1' where its UVs point")
        }
        assertNotEquals(
            proportional.glyphFor('1'.code, FontWeight.Thin),
            proportional.glyphFor('1'.code, FontWeight.Black),
            "each weight's figure has its own place on the page",
        )

        val chain = UiFonts.fallback(UiFonts.trueSans(), listOf(UiFonts.bitmap()))
        val chainProportional = chain.withNumerals(FontNumerals.Proportional)
        assertSame(chain.atlasPixelsRgba, chainProportional.atlasPixelsRgba)
        assertSame(chain, chainProportional.fontFor('A'.code))
        val chainFigure = chainProportional.fontFor('1'.code)
        assertTrue(chainFigure !== chain && chainFigure in chainProportional.atlasPages)
        assertTrue(inkIn(chainFigure, assertNotNull(chainProportional.glyphFor('1'.code, FontWeight.Normal))))
        assertTrue(UiFonts.bitmap() in chainProportional.atlasPages, "the disparate face keeps its own page")
    }

    /** Whether [page]'s pixels inside [glyph]'s UVs hold any ink: a distance inside the outline. */
    private fun inkIn(page: UiFont, glyph: GlyphRect): Boolean {
        val pixels = page.atlasPixelsRgba
        val x0 = (glyph.u0 * page.atlasWidth).toInt()
        val x1 = (glyph.u1 * page.atlasWidth).toInt()
        val y0 = (glyph.v0 * page.atlasHeight).toInt()
        val y1 = (glyph.v1 * page.atlasHeight).toInt()
        return (y0 until y1).any { y ->
            (x0 until x1).any { x -> (pixels[(y * page.atlasWidth + x) * 4 + 3].toInt() and 0xFF) > INK }
        }
    }

    private inline fun collectWarnings(block: () -> Unit): List<String> {
        val warnings = mutableListOf<String>()
        val sink = LogSink { if (it.level == LogLevel.Warn) warnings += it.message }
        Log.install(sink)
        try {
            block()
        } finally {
            Log.remove(sink)
        }
        return warnings
    }

    private class GlyphIds(val zero: Int, val one: Int)

    private suspend fun fixture(): Pair<ByteArray, GlyphIds> {
        val bytes = readResourceBytes("fonts/roboto-numerals.ttf")
        val font = OpenTypeFont(bytes, "roboto-numerals.ttf")
        return bytes to GlyphIds(zero = font.glyphFor('0'.code), one = font.glyphFor('1'.code))
    }

    /** A big-endian buffer a `GSUB` table is written into. */
    private class Bytes {
        private var data = ByteArray(INITIAL_SIZE)
        var size = 0
            private set

        fun u16(value: Int) {
            ensure(2)
            data[size++] = (value ushr 8).toByte()
            data[size++] = value.toByte()
        }

        fun tag(tag: String) {
            ensure(4)
            tag.forEach { data[size++] = it.code.toByte() }
        }

        fun putU16(at: Int, value: Int) {
            data[at] = (value ushr 8).toByte()
            data[at + 1] = value.toByte()
        }

        fun toByteArray(): ByteArray = data.copyOf(size)

        private fun ensure(count: Int) {
            if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
        }

        private companion object {
            const val INITIAL_SIZE = 256
        }
    }

    /**
     * A `GSUB` table with [features] (each a tag and its lookup indices), [lookups], and a script list written
     * by [scripts], by default one script whose default language system lists every feature.
     */
    private fun gsub(
        features: List<Pair<String, List<Int>>>,
        lookups: List<Bytes.() -> Unit>,
        scripts: Bytes.() -> Unit = { defaultScriptList(features.size) },
    ): ByteArray {
        val out = Bytes()
        repeat(5) { out.u16(if (it == 0) 1 else 0) }
        val featureList = out.size
        out.putU16(6, featureList)
        out.u16(features.size)
        val records = out.size
        features.forEach { (tag, _) ->
            out.tag(tag)
            out.u16(0)
        }
        features.forEachIndexed { index, (_, lookupIndices) ->
            out.putU16(records + 6 * index + 4, out.size - featureList)
            out.u16(0)
            out.u16(lookupIndices.size)
            lookupIndices.forEach(out::u16)
        }
        val lookupList = out.size
        out.putU16(8, lookupList)
        out.u16(lookups.size)
        val lookupOffsets = out.size
        repeat(lookups.size) { out.u16(0) }
        lookups.forEachIndexed { index, write ->
            out.putU16(lookupOffsets + 2 * index, out.size - lookupList)
            out.write()
        }
        out.putU16(4, out.size)
        out.scripts()
        return out.toByteArray()
    }

    private fun Bytes.defaultScriptList(featureCount: Int) {
        u16(1)
        tag("DFLT")
        u16(8)
        u16(4)
        u16(0)
        u16(0)
        u16(0xFFFF)
        u16(featureCount)
        repeat(featureCount) { u16(it) }
    }

    /** A `SingleSubst` format 1 lookup adding [delta] to [glyph] only. */
    private fun singleDelta(glyph: Int, delta: Int): Bytes.() -> Unit = {
        u16(1)
        u16(0)
        u16(1)
        u16(8)
        u16(1)
        u16(6)
        u16(delta and 0xFFFF)
        u16(1)
        u16(1)
        u16(glyph)
    }

    /** A ligature lookup (LookupType 4) with no subtables, a type this reader does not apply. */
    private val ligatureLookup: Bytes.() -> Unit = {
        u16(4)
        u16(0)
        u16(0)
    }

    /** [font] with its [tag] table replaced by [table], appended at the end of the file. */
    private fun withTable(font: ByteArray, tag: String, table: ByteArray): ByteArray {
        val start = (font.size + 3) and 3.inv()
        val result = font.copyOf(start + table.size)
        table.copyInto(result, start)
        val record = tableRecord(font, tag)
        listOf(start, table.size).forEachIndexed { field, value ->
            repeat(4) { result[record + 8 + 4 * field + it] = (value ushr (24 - 8 * it)).toByte() }
        }
        return result
    }

    private fun tableRecord(bytes: ByteArray, tag: String): Int {
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) shl 8 or (bytes[at + 1].toInt() and 0xFF)
        return (0 until u16(4)).map { 12 + 16 * it }.single { at ->
            (0..3).all { bytes[at + it] == tag[it].code.toByte() }
        }
    }

    private fun tableOffset(bytes: ByteArray, tag: String): Int {
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) shl 8 or (bytes[at + 1].toInt() and 0xFF)
        val record = tableRecord(bytes, tag)
        return u16(record + 8) shl 16 or u16(record + 10)
    }

    private companion object {
        const val DIGITS = " ?0123456789:"
        const val ONE_ADVANCE = 889f / 2048f * 16f
        const val ZERO_ADVANCE = 1191f / 2048f * 16f
        const val INK = 0x80
    }
}
