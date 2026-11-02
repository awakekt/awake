/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A model's images load before the device is known (#1211): the Basis Universal file an image was cooked
 * to is read in its place, and transcoded to the device's block format when its material is made.
 */
class CookedModelImagesTest {

    @Test
    fun aCookedImageIsReadFromItsBasisFileAndAnyOtherFileAsItIs() = runTest {
        val files = CookedTextureLoader(FILES) { LibKtxBasisTranscoder }.withCookedImages()

        assertContentEquals(CookedTextures.bytes("rock.ktx2"), files.read(AssetPath("textures/rock.png")).getOrThrow())
        assertContentEquals(PNG, files.read(AssetPath("textures/plain.png")).getOrThrow(), "not cooked")
        assertContentEquals(BUFFER, files.read(AssetPath("models/rock.bin")).getOrThrow(), "not an image")
    }

    @Test
    fun withoutATranscoderTheImagesAreReadAsTheyAre() = runTest {
        val files = CookedTextureLoader(FILES) { null }.withCookedImages()

        assertContentEquals(PNG, files.read(AssetPath("textures/rock.png")).getOrThrow())
    }

    @Test
    fun theBasisFileIsTranscodedToTheBlockFormatOfTheDeviceThatMakesItsMaterial() = runTest {
        val image = assertNotNull(CookedTextureLoader(FILES) { LibKtxBasisTranscoder }.decode(CookedTextures.bytes("rock.ktx2")))
        val bc7 = FakeDevice(FakeCompressedTextures(setOf(Bc7), accepts = true))
        val none = FakeDevice(null)

        bc7.createImageMaterial(image)
        none.createImageMaterial(image)

        assertEquals(CookedTextures.SIZE to CookedTextures.SIZE, image.width to image.height)
        assertEquals(Bc7, bc7.compressed?.uploaded?.format)
        assertNull(bc7.rgba, "nothing went the RGBA8 way")
        assertTrue(CookedTextures.psnr(CookedTextures.sourceRgba, assertNotNull(none.rgba).data) > MIN_PSNR, "RGBA8 where the device takes no block format")
    }

    /**
     * A transcode that fails when the material is made throws an ordinary [Exception] out of
     * `createImageMaterial`, on the block-format path and the RGBA8 one, so the model resolver that makes
     * the material fails that material alone (#1261).
     */
    @Test
    fun aTranscodeThatFailsWhenTheMaterialIsMadeThrowsAnException() = runTest {
        val image = assertNotNull(CookedTextureLoader(FILES) { FailingTranscoder }.decode(CookedTextures.bytes("rock.ktx2")))

        assertFailsWith<IllegalStateException> { FakeDevice(FakeCompressedTextures(setOf(Bc7), accepts = true)).createImageMaterial(image) }
        assertFailsWith<IllegalStateException> { FakeDevice(null).createImageMaterial(image) }
    }

    @Test
    fun aFileThatIsNotKtx2IsLeftToTheCaller() = runTest {
        assertNull(CookedTextureLoader(FILES) { LibKtxBasisTranscoder }.decode(PNG))
        assertNull(CookedTextureLoader(FILES) { null }.decode(CookedTextures.bytes("rock.ktx2")), "no transcoder here")
    }

    private companion object {
        const val MIN_PSNR = 30.0
        val Bc7 = CompressedTextureFormat.Bc7Rgba
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val BUFFER = byteArrayOf(1, 2, 3)

        /** A transcoder that takes BC7 and fails every file, as a browser's does on a bad one. */
        val FailingTranscoder = object : BasisTranscoder {
            override val formats: Set<CompressedTextureFormat> = setOf(Bc7)

            override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> =
                throw TranscodeFailure("the file is corrupt")
        }

        val FILES = AssetSource { path ->
            when (path.value) {
                "textures/rock.ktx2" -> Result.success(CookedTextures.bytes("rock.ktx2"))
                "textures/rock.png", "textures/plain.png" -> Result.success(PNG)
                "models/rock.bin" -> Result.success(BUFFER)
                else -> Result.failure(NoSuchElementException(path.value))
            }
        }
    }
}

/** What a browser's transcoder throws: a [Throwable] that is neither an [Exception] nor an [Error], as a `JsException` is. */
private class TranscodeFailure(message: String) : Throwable(message)
