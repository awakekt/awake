/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The generator's vertical metrics come from the font file, so every machine gets the same ones. These
 * pin the parsing against hand-built fonts, and the committed atlas against the real font files.
 */
class FontVerticalMetricsTest {

    /** The smallest font [FontVerticalMetrics.parse] accepts: an sfnt header, a directory, `head` and `hhea`. */
    private fun fontBytes(
        unitsPerEm: Int = 1000,
        ascender: Int = 800,
        descender: Int = -200,
        lineGap: Int = 90,
        tables: List<String> = listOf("head", "hhea"),
        version: Int = 0x00010000,
    ): ByteArray {
        val directoryEnd = 12 + 16 * tables.size
        val buffer = ByteBuffer.allocate(directoryEnd + 64 * tables.size)
        buffer.putInt(version)
        buffer.putShort(tables.size.toShort())
        buffer.position(12)
        tables.forEachIndexed { index, tag ->
            val offset = directoryEnd + 64 * index
            tag.forEach { buffer.put(it.code.toByte()) }
            buffer.putInt(0) // checksum, which nothing here reads
            buffer.putInt(offset)
            buffer.putInt(64)
            if (tag == "head") buffer.putShort(offset + 18, unitsPerEm.toShort())
            if (tag == "hhea") {
                buffer.putShort(offset + 4, ascender.toShort())
                buffer.putShort(offset + 6, descender.toShort())
                buffer.putShort(offset + 8, lineGap.toShort())
            }
        }
        return buffer.array()
    }

    @Test
    fun itReadsUnitsPerEmAndTheHheaAscenderDescenderAndLineGap() {
        val metrics = FontVerticalMetrics.parse(fontBytes(unitsPerEm = 2048, ascender = 1900, descender = -500, lineGap = 0))

        assertEquals(2048, metrics.unitsPerEm)
        assertEquals(1900, metrics.ascender)
        assertEquals(-500, metrics.descender)
        assertEquals(0, metrics.lineGap)
    }

    @Test
    fun ascentAndDescentScaleToASizeAsPositiveDistances() {
        val metrics = FontVerticalMetrics.parse(fontBytes(unitsPerEm = 1000, ascender = 800, descender = -200))

        assertEquals(12.8f, metrics.ascent(16f), 1e-4f)
        assertEquals(3.2f, metrics.descent(16f), 1e-4f)
    }

    @Test
    fun anOpenTypeFontWithCffOutlinesIsAccepted() {
        val metrics = FontVerticalMetrics.parse(fontBytes(version = 0x4F54544F)) // "OTTO"

        assertEquals(800, metrics.ascender)
    }

    @Test
    fun bytesThatAreNotAFontAreRefusedWithTheFilesName() {
        val error = assertFailsWith<IllegalArgumentException> {
            FontVerticalMetrics.parse(ByteArray(64) { 1 }, name = "photo.png")
        }

        assertTrue("photo.png" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aFontMissingATableSaysWhichOne() {
        val noHhea = assertFailsWith<IllegalArgumentException> { FontVerticalMetrics.parse(fontBytes(tables = listOf("head")), "a.ttf") }
        val noHead = assertFailsWith<IllegalArgumentException> { FontVerticalMetrics.parse(fontBytes(tables = listOf("hhea")), "a.ttf") }

        assertTrue("'hhea'" in noHhea.message.orEmpty(), noHhea.message)
        assertTrue("'head'" in noHead.message.orEmpty(), noHead.message)
    }

    @Test
    fun aTruncatedFileIsRefusedNotReadPastItsEnd() {
        val whole = fontBytes()

        assertFailsWith<IllegalArgumentException> { FontVerticalMetrics.parse(whole.copyOf(8), "short.ttf") }
        assertFailsWith<IllegalArgumentException> { FontVerticalMetrics.parse(whole.copyOf(40), "cut.ttf") }
    }

    @Test
    fun aZeroUnitsPerEmIsRefused() {
        assertFailsWith<IllegalArgumentException> { FontVerticalMetrics.parse(fontBytes(unitsPerEm = 0)) }
    }

    /**
     * The whole point: the committed atlas must agree with the font files, on every machine. Each generated
     * object's `lineHeightEm` is the font's own ascender plus descender over its units per em, which is what
     * the generator now computes, and which Windows's AWT line metrics (the `OS/2` win values) did not give.
     */
    @Test
    fun theCommittedAtlasLineHeightIsWhatEachFontFileSays() {
        val faces = mapOf(
            "Thin" to "Roboto-Thin.ttf",
            "Light" to "Roboto-Light.ttf",
            "Regular" to "Roboto-Regular.ttf",
            "Medium" to "Roboto-Medium.ttf",
            "SemiBold" to "Roboto-SemiBold.ttf",
            "Bold" to "Roboto-Bold.ttf",
            "Black" to "Roboto-Black.ttf",
        )
        faces.forEach { (face, file) ->
            val metrics = FontVerticalMetrics.read(File(FONT_DIR, file))
            val generated = File(GENERATED_DIR, "Roboto${face}UiFontData.kt").readText()
            val committed = Regex("""lineHeightEm: Float = ([0-9.]+)f""").find(generated)?.groupValues?.get(1)?.toFloat()

            val expected = (metrics.ascent(RENDER_SIZE) + metrics.descent(RENDER_SIZE)) / OVERSAMPLE / LOGICAL_CELL
            assertEquals(expected, committed, "$face: the committed line height is not what $file's hhea says")
        }
    }

    private companion object {
        // Gradle runs a module's tests from the module directory, as the generator itself is run.
        const val FONT_DIR = "../../core/text/src/commonMain/resources/fonts"
        const val GENERATED_DIR = "../../core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/font"

        // The generator's own constants: an em of 16 logical pixels, rasterised at twice that.
        const val LOGICAL_CELL = 16
        const val OVERSAMPLE = 2
        const val RENDER_SIZE = (LOGICAL_CELL * OVERSAMPLE).toFloat()
    }
}
