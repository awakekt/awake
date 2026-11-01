/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ktx2ReaderTest {

    @Test
    fun anEtc1sTextureReadsAsBasisLzWithItsFiveLevels() {
        val texture = Ktx2Reader.read(Ktx2Fixtures.etc1s)

        assertEquals(0, texture.vkFormat, "a Basis Universal texture has no format until it's transcoded")
        assertEquals(16, texture.width)
        assertEquals(16, texture.height)
        assertEquals(Ktx2Encoding.Etc1s, texture.encoding)
        assertEquals(Ktx2Supercompression.BasisLz, texture.supercompression)
        assertTrue(texture.srgb, "written from an sRGB format")
        assertTrue(texture.needsTranscoding)
        assertEquals(5, texture.levels.size)
        assertTrue(texture.levels.all { it.data.isNotEmpty() }, "every level holds data")
        assertTrue(texture.supercompressionGlobalData.isNotEmpty(), "BasisLZ carries its global data")
    }

    @Test
    fun aUastcTextureReadsAsZstandardAndLinear() {
        val texture = Ktx2Reader.read(Ktx2Fixtures.uastcZstd)

        assertEquals(Ktx2Encoding.Uastc, texture.encoding)
        assertEquals(Ktx2Supercompression.Zstandard, texture.supercompression)
        assertFalse(texture.srgb, "written from a linear format")
        assertEquals(1, texture.levels.size)
        // One 16 x 16 UASTC level is 16 blocks of 16 bytes once Zstandard is undone.
        assertEquals(256L, texture.levels[0].uncompressedByteLength)
        assertTrue(texture.supercompressionGlobalData.isEmpty())
    }

    @Test
    fun aRawTextureGivesBackThePixelsItWasWrittenWith() {
        val texture = Ktx2Reader.read(Ktx2Fixtures.rawRgba8)

        assertEquals(VK_FORMAT_R8G8B8A8_UNORM, texture.vkFormat)
        assertEquals(Ktx2Encoding.Raw, texture.encoding)
        assertEquals(Ktx2Supercompression.None, texture.supercompression)
        assertFalse(texture.needsTranscoding)
        assertEquals(listOf(64L, 16L, 4L), texture.levels.map { it.uncompressedByteLength })
        for ((level, size) in listOf(4, 2, 1).withIndex()) {
            assertContentEquals(gradient(size, level), texture.levels[level].data, "level $level")
        }
        assertTrue("KTXwriter" in texture.keyValues, "libktx names itself in the key/value data")
    }

    @Test
    fun aPngIsNotTakenForKtx2() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
        assertFalse(Ktx2Reader.isKtx2(png))
        assertTrue(Ktx2Reader.isKtx2(Ktx2Fixtures.etc1s))
        assertEquals("identifier", assertFailsWith<Ktx2FormatException> { Ktx2Reader.read(png) }.location)
    }

    @Test
    fun aMalformedFileFailsWithWhereAndWhy() {
        val cases = mapOf(
            "identifier" to Ktx2Fixtures.rawRgba8.copyOf(40),
            "header.faceCount" to Ktx2Fixtures.rawRgba8.withU32(FACE_COUNT, 2),
            "header.levelCount" to Ktx2Fixtures.rawRgba8.withU32(LEVEL_COUNT, 40),
            "header.supercompressionScheme" to Ktx2Fixtures.rawRgba8.withU32(SUPERCOMPRESSION, 9),
            "levels[0]" to Ktx2Fixtures.rawRgba8.withU32(LEVEL_INDEX + 8, 100_000),
            "kvd" to Ktx2Fixtures.rawRgba8.withU32(KVD_LENGTH, 100_000),
        )
        for ((location, bytes) in cases) {
            val failure = assertFailsWith<Ktx2FormatException>(location) { Ktx2Reader.read(bytes) }
            assertEquals(location, failure.location, failure.message)
        }
    }

    /** The pattern [Ktx2Fixtures] wrote at [level]: red along x, green along y, blue level * 50. */
    private fun gradient(size: Int, level: Int) = ByteArray(size * size * 4).also { pixels ->
        for (i in 0 until size * size) {
            val x = i % size
            val y = i / size
            pixels[i * 4] = (x * 255 / maxOf(1, size - 1)).toByte()
            pixels[i * 4 + 1] = (y * 255 / maxOf(1, size - 1)).toByte()
            pixels[i * 4 + 2] = (level * 50).toByte()
            pixels[i * 4 + 3] = 255.toByte()
        }
    }

    private fun ByteArray.withU32(at: Int, value: Int) = copyOf().also { bytes ->
        for (byte in 0 until 4) bytes[at + byte] = (value shr (8 * byte)).toByte()
    }

    private companion object {
        const val VK_FORMAT_R8G8B8A8_UNORM = 37
        const val FACE_COUNT = 36
        const val LEVEL_COUNT = 40
        const val SUPERCOMPRESSION = 44
        const val KVD_LENGTH = 60
        const val LEVEL_INDEX = 80
    }
}
