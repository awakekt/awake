/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlasPngEncoderTest {

    @Test
    fun encodesRgba8LosslesslyAndDeterministically() {
        val width = 19
        val height = 13
        val rng = Random(1234)
        val rgba = ByteArray(width * height * 4) { rng.nextInt(256).toByte() }

        val first = encodeAtlasPng(rgba, width, height)
        val second = encodeAtlasPng(rgba, width, height)
        assertContentEquals(first, second, "PNG encoding must be deterministic")

        val decoded = ImageIO.read(ByteArrayInputStream(first))
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)

        val roundTripped = ByteArray(rgba.size)
        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = decoded.getRGB(x, y)
                roundTripped[idx++] = ((argb shr 16) and 0xFF).toByte()
                roundTripped[idx++] = ((argb shr 8) and 0xFF).toByte()
                roundTripped[idx++] = (argb and 0xFF).toByte()
                roundTripped[idx++] = ((argb ushr 24) and 0xFF).toByte()
            }
        }
        assertContentEquals(rgba, roundTripped, "ImageIO must recover bit-exact RGBA bytes")
    }

    @Test
    fun rejectsInvalidDimensionsOrPayloadSize() {
        assertFailsWith<IllegalArgumentException> { encodeAtlasPng(ByteArray(4), 0, 1) }
        assertFailsWith<IllegalArgumentException> { encodeAtlasPng(ByteArray(4), 1, 0) }
        assertFailsWith<IllegalArgumentException> { encodeAtlasPng(ByteArray(3), 1, 1) }
    }

    @Test
    fun buildFileSpecEmitsChunkedGettersAndPreservesLicenseSnippet() {
        val rgba = ByteArray(2 * 2 * 4) { (it * 17).toByte() }
        val atlasB64 = encodeAtlasPngBase64(rgba, 2, 2)
        val effectB64 = encodeEffectField(ByteArray(4) { (it * 31).toByte() }, 2)
        val atlas = AtlasResult(
            lineHeightEm = 1.171875f,
            atlasWidth = 2,
            atlasHeight = 2,
            glyphOrder = "?",
            uvBoundsPx = intArrayOf(0, 0, 2, 2),
            quadMetricsEm = floatArrayOf(0f, 0f, 1f, 1f),
            inkMetricsEm = floatArrayOf(0f, 0f, 1f, 1f),
            advancesEm = floatArrayOf(0.5f),
            encodedAtlasBase64 = atlasB64,
            effect = EffectField(2, 2, intArrayOf(0, 0, 2, 2), floatArrayOf(0f, 0f, 1f, 1f), effectB64),
        )
        val face = Face("Roboto-Test.ttf", "RobotoTestUiFontData", "Roboto Test")
        val rendered = markRobotoSnippet(buildFileSpec(atlas, face).toString())

        assertTrue("override val encodedAtlasBase64: String\n    get() =" in rendered, rendered)
        assertTrue("override val encodedEffectFieldBase64: String\n    get() =" in rendered, rendered)
        assertTrue("\"$atlasB64\"" in rendered)
        assertFalse("\"\"\"" in rendered, "generated base64 should not use indented triple-quoted strings")
        assertTrue("OFL-1.1" in rendered)
    }
}
