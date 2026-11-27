/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.CompressedTextureFormat.Astc4x4
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc7Rgba
import com.awakekt.awake.render.texture.isReleased
import com.awakekt.awake.render.texture.releaseImageData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies mobile `.astc.ktx2`/`.etc2.ktx2` model image resolution without a Basis transcoder
 * (Android/iOS path), CPU byte release via [releaseImageData], and multi-layer KTX2 array decoding.
 */
class CompressedPbrAndMobileModelTest {

    @Test
    fun mobileModelImagesLoadAstcDirectlyWithoutATranscoderAndReleaseAfterUpload() = runTest {
        val astcBytes = CookedTextures.bytes("rock.astc.ktx2")
        val pngFallback = byteArrayOf(1, 2, 3, 4)
        val files = AssetSource { path ->
            when (path.value) {
                "models/rock.astc.ktx2" -> Result.success(astcBytes)
                "models/rock.png" -> Result.success(pngFallback)
                else -> Result.failure(IllegalStateException("missing $path"))
            }
        }
        // Simulate Android/iOS where platformBasisTranscoder() returns null.
        val loader = CookedTextureLoader(files) { null }
        val modelFiles = loader.withCookedImages { setOf(Astc4x4) }

        val readBytes = modelFiles.read(AssetPath("models/rock.png")).getOrThrow()
        assertContentEquals(astcBytes, readBytes, "mobile model loader must pick .astc.ktx2 when ASTC is supported")

        val source = assertNotNull(loader.decode(readBytes), "raw ASTC KTX2 must decode without a Basis transcoder")
        assertEquals(CookedTextures.SIZE, source.width)
        assertEquals(CookedTextures.SIZE, source.height)
        val compressed = assertNotNull(source.compressed(setOf(Astc4x4)))
        assertEquals(Astc4x4, compressed.format)
        assertFalse(source.isReleased)

        assertTrue(source.releaseImageData(), "releaseImageData must drop retained KTX2 mip arrays")
        assertTrue(source.isReleased)
        assertNull(source.compressed(setOf(Astc4x4)), "released source must not retain compressed mip data")
    }

    @Test
    fun basisModelImageSourceReleasesRawKtx2BytesAfterUpload() = runTest {
        val basisBytes = CookedTextures.bytes("rock.ktx2")
        val loader = CookedTextureLoader(AssetSource { Result.failure(IllegalStateException("unused")) }) {
            LibKtxBasisTranscoder
        }
        val source = assertNotNull(loader.decode(basisBytes))
        assertNotNull(source.compressed(setOf(Bc7Rgba)))
        assertFalse(source.isReleased)

        assertTrue(source.releaseImageData(), "releaseImageData must drop retained Basis KTX2 bytes")
        assertTrue(source.isReleased)
        assertNull(source.compressed(setOf(Bc7Rgba)))
    }

    @Test
    fun mobileModelImagesFallBackToSourcePngWhenDeviceFormatsExcludeAstc() = runTest {
        val astcBytes = CookedTextures.bytes("rock.astc.ktx2")
        val pngFallback = byteArrayOf(1, 2, 3, 4)
        val files = AssetSource { path ->
            when (path.value) {
                "models/rock.astc.ktx2" -> Result.success(astcBytes)
                "models/rock.png" -> Result.success(pngFallback)
                else -> Result.failure(IllegalStateException("missing $path"))
            }
        }
        val loader = CookedTextureLoader(files) { null }
        val modelFiles = loader.withCookedImages { emptySet() }

        val readBytes = modelFiles.read(AssetPath("models/rock.png")).getOrThrow()
        assertContentEquals(pngFallback, readBytes, "when no supported compressed variant matches, fall back to source image")
    }
}
