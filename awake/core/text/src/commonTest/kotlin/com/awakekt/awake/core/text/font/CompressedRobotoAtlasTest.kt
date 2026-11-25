/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.core.text.font

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Verifies lossless PNG compression of the 7 built-in Roboto MTSDF atlases and backwards-compatible
 * raw/PNG decoding in [PackedUiFontData.atlasBytes].
 */
class CompressedRobotoAtlasTest {

    private data class ExpectedFace(
        val label: String,
        val data: PackedUiFontData,
        val expectedRgbaSha256: String,
    )

    private val faces = listOf(
        ExpectedFace("Thin", RobotoThinUiFontData, "1669612d50fdd2fea92ad3776479ef1acaca38297b3d6048d1118689dab3c982"),
        ExpectedFace("Light", RobotoLightUiFontData, "c5417c6e18ca88cee46cf26676ba3bc6850134cc0e28749ee54cddff44d8bc79"),
        ExpectedFace("Regular", RobotoRegularUiFontData, "ea5069fa4fe1b2dc66299f52fb3589db0ccd256b7dcaf902950d8468fd65bc48"),
        ExpectedFace("Medium", RobotoMediumUiFontData, "6bb37a1f0380ef10bc9bda1f100f06bc485ea84db443683c3060e2dbec57d22a"),
        ExpectedFace("SemiBold", RobotoSemiBoldUiFontData, "308fa6bfa61e98f673344cbff2a5a8a77d659805455bb31b4b4ec373a487e9a8"),
        ExpectedFace("Bold", RobotoBoldUiFontData, "2f33f09a035f7c463292fcfdcdcbd1ed259dfb2780009e0a0f04f420b25d4051"),
        ExpectedFace("Black", RobotoBlackUiFontData, "035b7c6a59c962aa4995613010f4a148b0fb3f744bf2ef06a49cb84b2c17e68a"),
    )

    @Test
    fun allSevenRobotoAtlasesDecodeLosslesslyToPinnedRgbaSha256() {
        val mark = TimeSource.Monotonic.markNow()
        for (face in faces) {
            val data = face.data
            assertFalse(data.encodedAtlasBase64.any(Char::isWhitespace), "${face.label} atlas base64 should have no whitespace")
            assertFalse(data.encodedEffectFieldBase64.any(Char::isWhitespace), "${face.label} effect base64 should have no whitespace")
            assertTrue(PngDecoder.isPng(decodeBase64(data.encodedAtlasBase64)), "${face.label} atlas payload should be PNG-encoded")

            val rgba = data.atlasBytes()
            val expectedBytes = data.atlasWidth * data.atlasHeight * data.atlasChannels
            assertEquals(expectedBytes, rgba.size, "${face.label} decoded byte count")
            assertEquals(face.expectedRgbaSha256, sha256Hex(rgba), "${face.label} decoded RGBA must be bit-exact")

            val effect = assertNotNull(data.effectFieldBytes(), "${face.label} effect field")
            assertEquals(data.effectAtlasWidth * data.effectAtlasHeight, effect.size, "${face.label} effect field byte count")
        }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        println("Decoded all 7 PNG-compressed Roboto MTSDF atlases + effect fields in ${elapsedMs}ms")
    }

    @Test
    fun atlasBytesHandlesBothPngAndLegacyRawBase64() {
        // 1x1 RGBA pixel (10, 20, 30, 40)
        val rawRgba = byteArrayOf(10, 20, 30, 40)
        val rawB64 = Base64.Default.encode(rawRgba)
        val rawWithWhitespace = "  ${rawB64.substring(0, 4)}\n  ${rawB64.substring(4)}  "

        val legacyRgba = fakeFontData(width = 1, height = 1, channels = 4, encoded = rawB64)
        assertContentEquals(rawRgba, legacyRgba.atlasBytes())

        val legacyRgbaWrapped = fakeFontData(width = 1, height = 1, channels = 4, encoded = rawWithWhitespace)
        assertContentEquals(rawRgba, legacyRgbaWrapped.atlasBytes())

        // 2x2 single-channel coverage alpha
        val rawAlpha = byteArrayOf(0, 85, 170.toByte(), 255.toByte())
        val legacyAlpha = fakeFontData(width = 2, height = 2, channels = 1, encoded = Base64.Default.encode(rawAlpha))
        assertContentEquals(rawAlpha, legacyAlpha.atlasBytes())

        // 1x1 PNG-encoded RGBA pixel (10, 20, 30, 40) with whitespace around base64
        val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mPgEpHTAAAAzQBltceWUgAAAABJRU5ErkJggg=="
        val pngWrapped = "  ${pngBase64.substring(0, 40)}\n  ${pngBase64.substring(40)}  "
        val pngData = fakeFontData(width = 1, height = 1, channels = 4, encoded = pngWrapped)
        assertContentEquals(rawRgba, pngData.atlasBytes())

        // Dimension mismatch between PNG IHDR and PackedUiFontData metadata fails fast
        val wrongDims = fakeFontData(width = 2, height = 2, channels = 4, encoded = pngBase64)
        assertFailsWith<IllegalArgumentException> { wrongDims.atlasBytes() }
    }

    private fun fakeFontData(width: Int, height: Int, channels: Int, encoded: String): PackedUiFontData =
        object : PackedUiFontData {
            override val name: String = "Test"
            override val baseCellSize: Int = 16
            override val lineHeightEm: Float = 1f
            override val textScaleStep: Float = 0.25f
            override val atlasWidth: Int = width
            override val atlasHeight: Int = height
            override val samplingMode: UiFontSamplingMode =
                if (channels == 4) UiFontSamplingMode.DistanceField else UiFontSamplingMode.CoverageAlpha
            override val fallbackChar: Char = '?'
            override val atlasChannels: Int = channels
            override val encodedAtlasBase64: String = encoded
            override val glyphOrder: String = "?"
            override val uvBoundsPx: IntArray = intArrayOf(0, 0, width, height)
            override val quadMetricsEm: FloatArray = floatArrayOf(0f, 0f, 1f, 1f)
            override val advancesEm: FloatArray = floatArrayOf(1f)
        }
}

/** Pure multiplatform FIPS 180-4 SHA-256 hex digest for bit-exact atlas assertions in commonTest. */
@Suppress("LongMethod")
private fun sha256Hex(input: ByteArray): String {
    val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    val bitLen = input.size.toLong() * 8L
    val paddedLen = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLen)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLen - 1 - i] = ((bitLen ushr (i * 8)) and 0xFFL).toByte()
    }

    val w = IntArray(64)
    var offset = 0
    while (offset < paddedLen) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                ((padded[j + 1].toInt() and 0xFF) shl 16) or
                ((padded[j + 2].toInt() and 0xFF) shl 8) or
                (padded[j + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
        offset += 64
    }

    val hex = "0123456789abcdef"
    val out = CharArray(64)
    val words = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
    for (wi in words.indices) {
        val word = words[wi]
        for (nib in 0 until 8) {
            out[wi * 8 + nib] = hex[(word ushr ((7 - nib) * 4)) and 0xF]
        }
    }
    return out.concatToString()
}
