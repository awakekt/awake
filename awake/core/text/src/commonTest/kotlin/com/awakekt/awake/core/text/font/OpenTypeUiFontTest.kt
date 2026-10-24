/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [UiFonts.load] against what `:awake:ui:font-atlas-generator` baked from the same Roboto files.
 *
 * The generator measured each glyph through FreeType at 16 px, which holds a coordinate to 1/64 px,
 * so its ink boxes sit up to 3/2048 em from the outline's exact extent; the loader measures the
 * outline itself. The line, the cells and the atlas size come from the font's own integers and match
 * exactly.
 */
class OpenTypeUiFontTest {

    private val faces = mapOf(
        "Roboto-Thin.ttf" to RobotoThinUiFontData,
        "Roboto-Light.ttf" to RobotoLightUiFontData,
        "Roboto-Regular.ttf" to RobotoRegularUiFontData,
        "Roboto-Medium.ttf" to RobotoMediumUiFontData,
        "Roboto-SemiBold.ttf" to RobotoSemiBoldUiFontData,
        "Roboto-Bold.ttf" to RobotoBoldUiFontData,
        "Roboto-Black.ttf" to RobotoBlackUiFontData,
    )

    @Test
    fun defaultCharsAreTheBuiltInAtlasSet() {
        assertEquals(RobotoRegularUiFontData.glyphOrder, UiFonts.DEFAULT_CHARS)
    }

    @Test
    fun everyRobotoFaceLaysOutAsTheGeneratorDid() = runTest {
        faces.forEach { (file, baked) ->
            val loaded = OpenTypeUiFontData.load(readResourceBytes(file), file, UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
            assertEquals(baked.glyphOrder, loaded.glyphOrder, file)
            assertEquals(baked.atlasWidth to baked.atlasHeight, loaded.atlasWidth to loaded.atlasHeight, file)
            assertEquals(baked.lineHeightEm, loaded.lineHeightEm, file)
            // A sample rect snaps the ink box outwards to whole texels, so the 1/1024 em can tip one edge by a texel.
            assertTrue(baked.uvBoundsPx.indices.all { abs(baked.uvBoundsPx[it] - loaded.uvBoundsPx[it]) <= 1 }, "$file sample rects")
            assertWithin(baked.quadMetricsEm, loaded.quadMetricsEm, 1f / 32 + 1e-6f, "$file quads")
            assertWithin(baked.advancesEm, loaded.advancesEm, ADVANCE_TOLERANCE_EM, "$file advances")
            assertWithin(baked.inkMetricsEm, loaded.inkMetricsEm, INK_TOLERANCE_EM, "$file ink")
        }
    }

    @Test
    fun regularMetricsMatchTheBakedFont() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf") as PackedUiFont
        val baked = UiFonts.trueSans() as PackedUiFont
        assertEquals(baked.lineHeightEm, loaded.lineHeightEm)
        assertEquals(baked.ascentEm, loaded.ascentEm, INK_TOLERANCE_EM)
        assertEquals(baked.descentEm, loaded.descentEm, INK_TOLERANCE_EM)
        assertEquals(baked.capHeightEm, loaded.capHeightEm, INK_TOLERANCE_EM)
        // ':' is a composite in Roboto: two periods placed by offsets.
        for (char in "Hg.:") {
            val want = baked.inkFor(char)!!
            val got = loaded.inkFor(char)!!
            listOf(want.offsetXEm to got.offsetXEm, want.offsetYEm to got.offsetYEm, want.widthEm to got.widthEm, want.heightEm to got.heightEm)
                .forEach { (expected, actual) -> assertEquals(expected, actual, INK_TOLERANCE_EM, "'$char' ink") }
            assertEquals(baked.advanceFor(char, 16f), loaded.advanceFor(char, 16f), 16 * ADVANCE_TOLERANCE_EM, "'$char' advance")
        }
    }

    /**
     * The generator's A channel is msdfgen's true signed distance, the same quantity the loader writes.
     * They part only where Roboto's contours overlap, and there msdfgen's A dips towards the edge value
     * inside the glyph (its RGB, which the shader draws, does not).
     */
    @Test
    fun fieldMatchesMsdfgenTrueDistance() = runTest {
        val loaded = OpenTypeUiFontData.load(readResourceBytes("Roboto-Regular.ttf"), "Regular", UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
        val mine = loaded.atlasBytes()
        val theirs = RobotoRegularUiFontData.atlasBytes()
        var close = 0
        for (texel in 0 until mine.size / 4) {
            val value = mine[texel * 4 + 3]
            assertTrue(mine[texel * 4] == value && mine[texel * 4 + 1] == value && mine[texel * 4 + 2] == value, "texel $texel channels differ")
            if (abs((value.toInt() and 0xFF) - (theirs[texel * 4 + 3].toInt() and 0xFF)) <= 2) close++
        }
        val share = close.toDouble() / (mine.size / 4)
        assertTrue(share >= 0.997, "only ${share * 100}% of texels within 2/255 of msdfgen's true distance")
    }

    /** Roboto's 'H' is two stems and a bar that overlaps both; a field built per contour draws a seam where they meet. */
    @Test
    fun overlappingContoursLeaveNoSeam() = runTest {
        val loaded = OpenTypeUiFontData.load(readResourceBytes("Roboto-Regular.ttf"), "Regular", UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
        val atlas = loaded.atlasBytes()
        val cell = loaded.glyphOrder.indexOf('H')
        val cellX = cell % 16 * (loaded.atlasWidth / 16)
        val cellY = cell / 16 * (loaded.atlasHeight / 7)
        // The bar spans cell rows 22.7 to 25.2 and joins the stems at columns 11.7 and 23.1.
        val bar = (cellX + 9 until cellX + 26).map { x -> atlas[((cellY + 24) * loaded.atlasWidth + x) * 4].toInt() and 0xFF }
        assertTrue(bar.all { it >= 200 }, "the bar's middle row dips towards the edge: $bar")
    }

    /** A hash of the Regular atlas, pinned so every target that runs this proves it bakes the same bytes. */
    @Test
    fun everyTargetBakesTheSameBytes() = runTest {
        val loaded = OpenTypeUiFontData.load(readResourceBytes("Roboto-Regular.ttf"), "Regular", UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
        var hash = FNV_OFFSET
        loaded.atlasBytes().forEach { hash = (hash xor (it.toLong() and 0xFF)) * FNV_PRIME }
        assertEquals(REGULAR_ATLAS_HASH, hash)
    }

    /** [UiFonts.load]'s own resolution, pinned the same way: 64 texels per em with an 8-texel range. */
    @Test
    fun everyTargetBakesTheSameRuntimeBytes() = runTest {
        val loaded = OpenTypeUiFontData.load(readResourceBytes("Roboto-Regular.ttf"), "Regular", UiFonts.DEFAULT_CHARS)
        assertEquals(64, loaded.bake.texelsPerEm)
        assertEquals(8f, loaded.distanceFieldRangePx)
        var hash = FNV_OFFSET
        loaded.atlasBytes().forEach { hash = (hash xor (it.toLong() and 0xFF)) * FNV_PRIME }
        assertEquals(REGULAR_RUNTIME_ATLAS_HASH, hash)
    }

    /**
     * Twice the texels per em measures the same glyphs: the ink, the advances and the line are the
     * outline's, whatever the resolution, and a quad moves by at most one generator texel.
     */
    @Test
    fun runtimeBakeMeasuresAsTheGeneratorLayoutDoes() = runTest {
        val bytes = readResourceBytes("Roboto-Regular.ttf")
        val coarse = OpenTypeUiFontData.load(bytes, "Regular", UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
        val fine = OpenTypeUiFontData.load(bytes, "Regular", UiFonts.DEFAULT_CHARS)
        assertEquals(coarse.lineHeightEm, fine.lineHeightEm)
        assertContentEquals(coarse.inkMetricsEm, fine.inkMetricsEm)
        assertContentEquals(coarse.advancesEm, fine.advancesEm)
        // The quad is the ink plus the bleed, snapped out to whole texels: a generator texel either way.
        assertWithin(coarse.quadMetricsEm, fine.quadMetricsEm, 1f / 32 + 1e-6f, "quads")
    }

    /** A glyph set too large for the requested resolution's atlas bakes coarser rather than failing to load. */
    @Test
    fun aGlyphSetTooLargeForItsAtlasBakesCoarser() = runTest {
        val bytes = readResourceBytes("fonts/roboto-subset.ttf")
        assertEquals(64, OpenTypeUiFontData.load(bytes, "ttf", FIXTURE_CHARS).bake.texelsPerEm)
        // At 4096 texels per em even eight glyphs need an atlas over 8192 pixels wide.
        val oversized = DistanceFieldBake(4096, 8)
        assertEquals(48, OpenTypeUiFontData.load(bytes, "ttf", FIXTURE_CHARS, oversized).bake.texelsPerEm)
    }

    @Test
    fun cffOutlinesDrawWhatTrueTypeOutlinesDraw() = runTest {
        val chars = " Hg.:?Ä"
        val trueType = OpenTypeUiFontData.load(readResourceBytes("fonts/roboto-subset.ttf"), "ttf", chars)
        val cff = OpenTypeUiFontData.load(readResourceBytes("fonts/roboto-subset.otf"), "otf", chars)
        assertEquals(chars, trueType.glyphOrder)
        assertEquals(trueType.glyphOrder, cff.glyphOrder)
        assertContentEquals(trueType.uvBoundsPx, cff.uvBoundsPx)
        assertWithin(trueType.inkMetricsEm, cff.inkMetricsEm, CFF_TOLERANCE_EM, "ink")
        assertContentEquals(trueType.advancesEm, cff.advancesEm)
        val a = trueType.atlasBytes()
        val b = cff.atlasBytes()
        assertTrue(a.indices.all { abs((a[it].toInt() and 0xFF) - (b[it].toInt() and 0xFF)) <= 1 }, "CFF and TrueType fields differ")
    }

    @Test
    fun compositeGlyphsJoinTheirComponents() = runTest {
        val font = UiFonts.load(readResourceBytes("fonts/roboto-subset.ttf"), "ttf", chars = "Ä") as PackedUiFont
        // 'Ä' is 'A' (29, 0) to (1310, 1456) plus a dieresis reaching y 1789, in Roboto's 2048 units.
        val ink = font.inkFor('Ä')!!
        assertEquals(29 / 2048f, ink.offsetXEm, 1e-6f)
        assertEquals((1900 - 1789) / 2048f, ink.offsetYEm, 1e-6f)
        assertEquals((1310 - 29) / 2048f, ink.widthEm, 1e-6f)
        assertEquals(1789 / 2048f, ink.heightEm, 1e-6f)
    }

    /** The fixture's U+25A0 is a 600 by 500 rectangle drawn with all four flex operators, under hints and masks. */
    @Test
    fun cffFlexAndHintOperatorsDrawTheirShape() = runTest {
        val font = UiFonts.load(readResourceBytes("fonts/roboto-subset.otf"), "otf", chars = "■") as PackedUiFont
        val ink = font.inkFor('■')!!
        assertEquals(100 / 2048f, ink.offsetXEm, 1e-6f)
        assertEquals((1900 - 500) / 2048f, ink.offsetYEm, 1e-6f)
        assertEquals(600 / 2048f, ink.widthEm, 1e-6f)
        assertEquals(500 / 2048f, ink.heightEm, 1e-6f)
        assertEquals(800 / 2048f * 16f, font.advanceFor('■', 16f), 1e-4f)
    }

    @Test
    fun charsTheFontLacksFallBackToQuestionMark() = runTest {
        val font = UiFonts.load(readResourceBytes("fonts/roboto-subset.ttf"), "ttf", chars = "HЖ")
        assertEquals(font.uvFor('?'), font.uvFor('Ж'))
        assertEquals(font.uvFor('?'), font.uvFor('g'))
        assertTrue(font.uvFor('H') != font.uvFor('?'))
    }

    @Test
    fun truncatedFontsAreRejected() = runTest {
        for (file in listOf("fonts/roboto-subset.ttf", "fonts/roboto-subset.otf", "Roboto-Regular.ttf")) {
            val bytes = readResourceBytes(file)
            for (length in listOf(0, 3, 11, 12, 40, 200, bytes.size / 3, bytes.size / 2, bytes.size - 9)) {
                val error = assertRejected(bytes.copyOf(length), file)
                assertTrue(error.message.orEmpty().startsWith(file), "cut to $length bytes, the message does not name the font: ${error.message}")
            }
        }
    }

    @Test
    fun errorsNameTheTable() = runTest {
        val bytes = readResourceBytes("fonts/roboto-subset.ttf")
        val cut = assertRejected(bytes.copyOf(bytes.size - 9), "ttf").message.orEmpty()
        assertTrue(Regex("^ttf: '[a-zA-Z/ 2]{4}' table lies outside the file$").matches(cut), cut)
        val noCmap = bytes.copyOf().also { renameTable(it, "cmap", "xxxx") }
        assertEquals("ttf has no 'cmap' table", assertRejected(noCmap, "ttf").message)
    }

    @Test
    fun garbageIsRejected() {
        val random = kotlin.random.Random(773)
        repeat(200) {
            val bytes = random.nextBytes(random.nextInt(0, 400))
            if (it % 2 == 0 && bytes.size >= 4) intArrayOf(0x00, 0x01, 0x00, 0x00).forEachIndexed { at, b -> bytes[at] = b.toByte() }
            assertRejected(bytes, "random bytes $it")
        }
    }

    /**
     * Corrupt bytes anywhere in a font may still read, but never fail any other way than [IllegalArgumentException].
     * Most rounds read every glyph's outline; every tenth bakes the atlas too, which is what takes the time. The
     * bake here is at the generator's resolution, which parsing does not depend on: at [UiFonts.load]'s four times
     * the texels, eighty bakes passed the browser test runner's two-second limit on a CI runner. The next test
     * covers the runtime resolution and the pixel glyphs.
     */
    @Test
    fun corruptFontsFailOnlyWithIllegalArgument() = runTest {
        val random = kotlin.random.Random(1773)
        for (file in listOf("fonts/roboto-subset.ttf", "fonts/roboto-subset.otf")) {
            val original = readResourceBytes(file)
            repeat(400) { round ->
                val bytes = original.copyOf()
                repeat(1 + round % 4) { bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte() }
                val failure = runCatching {
                    if (round % 10 == 0) {
                        OpenTypeUiFontData.load(bytes, file, FIXTURE_CHARS, DistanceFieldBake.GENERATOR)
                    } else {
                        val font = OpenTypeFont(bytes, file)
                        FIXTURE_CHARS.forEach { font.advance(font.glyphFor(it.code)) }
                        repeat(font.glyphCount) { font.outline(it) }
                    }
                }.exceptionOrNull()
                assertTrue(failure == null || failure is IllegalArgumentException, "$file mutation $round threw $failure")
            }
        }
    }

    /**
     * The same, through [UiFonts.load] at its own resolution, and through the exact-size glyphs of every font that
     * still loads: corrupt outlines rasterize, or fail with [IllegalArgumentException], and nothing else.
     */
    @Test
    fun corruptFontsBakeAndRasterizeOrFailOnlyWithIllegalArgument() = runTest {
        val random = kotlin.random.Random(2773)
        for (file in listOf("fonts/roboto-subset.ttf", "fonts/roboto-subset.otf")) {
            val original = readResourceBytes(file)
            repeat(CORRUPT_RUNTIME_ROUNDS) { round ->
                val bytes = original.copyOf()
                repeat(1 + round % 4) { bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte() }
                val failure = runCatching {
                    val glyphs = UiFonts.load(bytes, file, chars = FIXTURE_CHARS).pixelGlyphs
                    FIXTURE_CHARS.forEach { glyphs?.glyph(it.code, FontWeight.Normal, 12) }
                }.exceptionOrNull()
                assertTrue(failure == null || failure is IllegalArgumentException, "$file mutation $round threw $failure")
            }
        }
    }

    private fun assertRejected(bytes: ByteArray, name: String): IllegalArgumentException {
        val failure = runCatching { UiFonts.load(bytes, name) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "$name (${bytes.size} bytes): expected IllegalArgumentException, got $failure")
        return failure
    }

    private fun renameTable(bytes: ByteArray, from: String, to: String) {
        val count = (bytes[4].toInt() and 0xFF) shl 8 or (bytes[5].toInt() and 0xFF)
        for (index in 0 until count) {
            val record = 12 + 16 * index
            if ((0..3).all { bytes[record + it] == from[it].code.toByte() }) to.forEachIndexed { at, c -> bytes[record + at] = c.code.toByte() }
        }
    }

    private fun assertWithin(expected: FloatArray, actual: FloatArray, tolerance: Float, what: String) {
        assertEquals(expected.size, actual.size, what)
        val worst = expected.indices.maxOfOrNull { abs(expected[it] - actual[it]) } ?: 0f
        assertTrue(worst <= tolerance, "$what: off by $worst, over $tolerance")
    }

    private companion object {
        /** FreeType rounds a coordinate to 1/64 px at the generator's 16 px, 1/2048 em. A composite's height stacks three
         * roundings (each part's extent and its offset), and the generated source keeps six decimals. */
        const val INK_TOLERANCE_EM = 3f / 2048 + 1e-6f
        const val ADVANCE_TOLERANCE_EM = 1e-5f

        /** The CFF fixture writes coordinates in 16.16 fixed point: 1/65536 of a font unit. */
        const val CFF_TOLERANCE_EM = 1e-6f
        const val FIXTURE_CHARS = " Hg.:?Ä■"

        /** Corrupt fonts baked at the runtime resolution, per fixture: few, as each bake is the expensive part. */
        const val CORRUPT_RUNTIME_ROUNDS = 12
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val REGULAR_ATLAS_HASH = 4211560484354822813L
        const val REGULAR_RUNTIME_ATLAS_HASH = 7093501371065988845L
    }
}
