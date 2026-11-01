/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.loader.CookedTextures.SIZE
import com.awakekt.awake.render.texture.CompressedTextureFormat.Astc4x4
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc3Rgba
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc7Rgba
import com.awakekt.awake.render.texture.CompressedTextureFormat.Etc2Rgba8
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ktx2TextureLoaderTest {
    private val desktop = setOf(Bc7Rgba, Bc3Rgba)
    private val mobile = setOf(Astc4x4, Etc2Rgba8)

    @Test
    fun aDesktopGpuGetsColourAsSrgbBc7WithEveryMip() {
        val upload = Ktx2TextureLoader.decode(CookedTextures.bytes("rock.ktx2"), desktop, LibKtxBasisTranscoder)

        val image = assertIs<Ktx2Upload.Compressed>(upload).image
        assertEquals(Bc7Rgba, image.format)
        assertTrue(image.srgb)
        assertEquals(7, image.levels.size, "64 x 64 down to 1 x 1, each level's size checked by CompressedTextureImage")
    }

    @Test
    fun aNormalMapStaysLinearAndPrefersAstcOnMobile() {
        val normal = CookedTextures.bytes("rock_normal.ktx2")

        val bc7 = assertIs<Ktx2Upload.Compressed>(Ktx2TextureLoader.decode(normal, desktop, LibKtxBasisTranscoder)).image
        val astc = assertIs<Ktx2Upload.Compressed>(Ktx2TextureLoader.decode(normal, mobile, LibKtxBasisTranscoder)).image

        assertEquals(Bc7Rgba to false, bc7.format to bc7.srgb)
        assertEquals(Astc4x4, astc.format, "UASTC maps onto ASTC without losing quality")
    }

    @Test
    fun aGpuWithNoBlockFormatGetsRgba8CloseToTheSourceBottomRowFirst() {
        val upload = Ktx2TextureLoader.decode(CookedTextures.bytes("rock.ktx2"), emptySet(), LibKtxBasisTranscoder)

        val texture = assertIs<Ktx2Upload.Rgba>(upload).texture
        assertEquals(SIZE to SIZE, texture.width to texture.height)
        val psnr = CookedTextures.psnr(CookedTextures.sourceRgba, texture.data)
        assertTrue(psnr > MIN_ETC1S_PSNR, "ETC1S decoded is $psnr dB from the source; upside down it would be far lower")
    }

    @Test
    fun aMobileFileLoadsAsItIsWithoutATranscoder() {
        assertEquals(listOf("tex/rock.astc.ktx2", "tex/rock.etc2.ktx2", "tex/rock.ktx2"), Ktx2Formats.variants("tex/rock.ktx2", mobile))
        assertEquals(listOf("tex/rock.ktx2"), Ktx2Formats.variants("tex/rock.ktx2", desktop))

        val astc = assertIs<Ktx2Upload.Compressed>(Ktx2TextureLoader.decode(CookedTextures.bytes("rock.astc.ktx2"), mobile, transcoder = null)).image
        assertEquals(Astc4x4 to true, astc.format to astc.srgb)
        assertEquals(7, astc.levels.size)
        assertNull(Ktx2TextureLoader.decode(CookedTextures.bytes("rock.astc.ktx2"), setOf(Etc2Rgba8), null), "no ASTC decoder ships")
        assertNull(Ktx2TextureLoader.decode(CookedTextures.bytes("rock.ktx2"), mobile, null), "Basis needs a transcoder")
    }

    @Test
    fun aBasisFileStoredTopRowFirstIsDecodedAndFlippedRatherThanUploadedUpsideDown() {
        val bottomFirst = CookedTextures.bytes("rock.ktx2")
        val topFirst = bottomFirst.withoutOrientation()

        val flipped = assertIs<Ktx2Upload.Rgba>(Ktx2TextureLoader.decode(topFirst, desktop, LibKtxBasisTranscoder)).texture
        val asStored = LibKtxBasisTranscoder.transcode(bottomFirst, null).first()

        assertContentEquals(asStored.flippedRows(), flipped.data, "the same pixels, rows reversed")
    }

    /** These bytes with `KTXorientation` renamed, so the file reads as KTX2's default, top row first. */
    private fun ByteArray.withoutOrientation(): ByteArray {
        val key = "KTXorientation".encodeToByteArray()
        val at = (0..size - key.size).first { i -> key.indices.all { this[i + it] == key[it] } }
        return copyOf().also { it[at + key.size - 1] = 'X'.code.toByte() }
    }

    private fun ByteArray.flippedRows(): ByteArray {
        val row = SIZE * 4
        return ByteArray(size).also { out -> for (y in 0 until SIZE) copyInto(out, (SIZE - 1 - y) * row, y * row, (y + 1) * row) }
    }

    private companion object {
        const val MIN_ETC1S_PSNR = 30.0
    }
}
