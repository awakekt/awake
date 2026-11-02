/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRingBuffer
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A source image loads from the KTX2 file it was cooked to, as the device takes it best (#1211). */
class CookedTextureLoaderTest {

    @Test
    fun aDeviceWithBc7GetsTheBasisFileTranscodedAndUploadedAsItIs() = runTest {
        val image = assertNotNull(CookedTextureLoader(files("textures/rock.ktx2")) { LibKtxBasisTranscoder }.load("textures/rock.png", setOf(Bc7)))
        val device = FakeDevice(FakeCompressedTextures(setOf(Bc7), accepts = true))

        device.createImageMaterial(image)

        assertEquals(Bc7, device.compressed?.uploaded?.format)
        assertNull(device.rgba, "nothing went the RGBA8 way")
    }

    @Test
    fun aDeviceThatRefusesTheBlocksGetsTheFileDecodedToRgba8() = runTest {
        val image = assertNotNull(CookedTextureLoader(files("textures/rock.ktx2")) { LibKtxBasisTranscoder }.load("textures/rock.png", setOf(Bc7)))
        val device = FakeDevice(FakeCompressedTextures(setOf(Bc7), accepts = false))

        device.createImageMaterial(image)

        val rgba = assertNotNull(device.rgba)
        assertTrue(CookedTextures.psnr(CookedTextures.sourceRgba, rgba.data) > MIN_PSNR)
    }

    @Test
    fun aDeviceWithNoBlockFormatGetsRgba8() = runTest {
        val image = assertNotNull(CookedTextureLoader(files("textures/rock.ktx2")) { LibKtxBasisTranscoder }.load("textures/rock.png", emptySet()))

        assertNull(image.compressed)
        assertTrue(CookedTextures.psnr(CookedTextures.sourceRgba, image.readRgba8()) > MIN_PSNR)
    }

    @Test
    fun aDeviceWithAstcGetsTheMobileFileWithNoTranscoder() = runTest {
        val loader = CookedTextureLoader(files("textures/rock.ktx2", "textures/rock.astc.ktx2")) { error("the ASTC file needs no transcoder") }

        val image = assertNotNull(loader.load("textures/rock.png", setOf(Astc)))

        assertEquals(Astc, image.compressed?.format)
    }

    @Test
    fun withoutACookedFileOrATranscoderTheSourceImageIsLeftToTheCaller() = runTest {
        var asked = 0
        val noTranscoder = CookedTextureLoader(files("textures/rock.ktx2")) { asked++; error("no transcoder on this page") }

        assertNull(CookedTextureLoader(files()) { LibKtxBasisTranscoder }.load("textures/rock.png", setOf(Bc7)), "nothing was cooked")
        assertNull(noTranscoder.load("textures/rock.png", setOf(Bc7)))
        assertNull(noTranscoder.load("textures/rock.png", setOf(Bc7)))
        assertEquals(1, asked, "a transcoder that failed to load isn't asked for again")
        assertNull(CookedTextureLoader(files("textures/rock.ktx2")) { LibKtxBasisTranscoder }.load("textures/rock.txt", setOf(Bc7)), "not an image")
    }

    @Test
    fun aTranscoderThatFailsWithAnyThrowableIsLoggedOnceAndLeavesEachTextureToItsSourceImage() = runTest {
        val records = LogRingBuffer()
        Log.install(records)
        try {
            // A browser's rejected script load arrives as a JsException: a Throwable, not an Exception (#1261).
            val loader = CookedTextureLoader(files("textures/rock.ktx2")) { throw NotAnException("transcoder not found") }

            assertNull(loader.load("textures/rock.png", setOf(Bc7)), "the caller decodes the source image")
            assertNull(loader.decode(CookedTextures.bytes("rock.ktx2")), "a model's image is left to its decoder")
            assertTrue(loader.withCookedImages().read(AssetPath("textures/rock.png")).isFailure, "the source image is read, and isn't here")
            assertEquals(1, records.countAtLeast(LogLevel.Error), "the failure is said once, not per texture")
        } finally {
            Log.remove(records)
        }
    }

    @Test
    fun aTranscodeThatFailsWithAnyThrowableFailsThatTextureWithAnException() = runTest {
        // A browser's failed transcode of a bad file arrives as a JsException: a Throwable, not an Exception (#1261).
        val failing = object : BasisTranscoder {
            override val formats = setOf(Bc7)

            override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> = throw NotAnException("bad file")
        }
        val loader = CookedTextureLoader(files("textures/rock.ktx2")) { failing }

        // What a caller that fails a texture on an Exception sees, as AssetSourceTextureResolver does.
        val loaded = assertFailsWith<IllegalStateException> { loader.load("textures/rock.png", setOf(Bc7)) }
        assertIs<NotAnException>(loaded.cause, "the transcoder's failure is kept")
        assertTrue("bad file" in loaded.message.orEmpty(), "and what it said")

        val modelImage = assertNotNull(loader.decode(CookedTextures.bytes("rock.ktx2")), "a model's image transcodes when its material is made")
        assertIs<NotAnException>(assertFailsWith<IllegalStateException> { modelImage.compressed(setOf(Bc7)) }.cause)
        assertIs<NotAnException>(assertFailsWith<IllegalStateException> { modelImage.readRgba8() }.cause)
    }

    @Test
    fun anErrorFromTheTranscoderIsNotWrapped() = runTest {
        val failing = object : BasisTranscoder {
            override val formats = setOf(Bc7)

            override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> = throw OutOfMemoryError("no room")
        }

        assertFailsWith<OutOfMemoryError> { CookedTextureLoader(files("textures/rock.ktx2")) { failing }.load("textures/rock.png", setOf(Bc7)) }
    }

    private class NotAnException(message: String) : Throwable(message)

    // Each of [names] holds the cooked file of the same name in test resources: `rock.ktx2` or `rock.astc.ktx2`.
    private fun files(vararg names: String) = AssetSource { path ->
        if (path.value in names) {
            Result.success(CookedTextures.bytes(path.value.substringAfterLast('/')))
        } else {
            Result.failure(NoSuchElementException(path.value))
        }
    }

    private companion object {
        const val MIN_PSNR = 30.0
        val Bc7 = CompressedTextureFormat.Bc7Rgba
        val Astc = CompressedTextureFormat.Astc4x4
    }
}
