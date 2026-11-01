/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
