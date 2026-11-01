/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.ktx.loader.Ktx2TextureLoader
import com.awakekt.awake.asset.ktx.loader.Ktx2Upload
import com.awakekt.awake.asset.ktx.loader.LibKtxBasisTranscoder
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.pipeline.compressedTextureFormats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureFiltering
import org.junit.AfterClass
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A block-compressed texture draws the same as the same pixels uploaded as RGBA8 (#1211).
 *
 * `cooked/rock.ktx2` is a 64 x 64 ETC1S texture the cook wrote (see `CookedTextures` in
 * awake:asset:texture-ktx-loader). It is drawn twice on the textured PBR plane: transcoded to each
 * block format the device samples, and decoded to RGBA8 by the same transcoder. The two frames are
 * compared as a picture.
 *
 * Measured on lavapipe, which takes the BC family only: BC3 draws 45.3 dB from the RGBA8 frame and
 * BC7 36.0 dB (Basis Universal's ETC1S-to-BC7 path is the coarser of the two). Sampled as their sRGB
 * variants, which Awake's shaders would decode a second time, both fall to 15.5 dB.
 */
class CompressedTextureParityTest {

    @Test
    fun aBlockCompressedTextureDrawsAsItsRgba8PixelsDoOnVulkan() {
        val renderer = session().renderer
        val formats = renderer.compressedTextureFormats
        assertTrue(CompressedTextureFormat.Bc7Rgba in formats, "lavapipe enables textureCompressionBC; this device takes $formats")
        val reference = renderer.draw(Ktx2TextureLoader.decode(ROCK, emptySet(), LibKtxBasisTranscoder))
        assertTrue(reference.drawnPixels() > MIN_DRAWN_PIXELS, "the RGBA8 plane drew ${reference.drawnPixels()} pixels")

        for (format in formats.filter { it in LibKtxBasisTranscoder.formats }) {
            val upload = assertIs<Ktx2Upload.Compressed>(Ktx2TextureLoader.decode(ROCK, setOf(format), LibKtxBasisTranscoder))
            val pixels = renderer.draw(upload)
            val psnr = psnr(reference, pixels)
            assertTrue(psnr > MIN_PSNR, "$format draws ${"%.1f".format(psnr)} dB from the RGBA8 frame")
        }
    }

    private fun Renderer.draw(upload: Ktx2Upload?): ByteArray {
        val material = when (upload) {
            is Ktx2Upload.Compressed -> assertNotNull(
                capability(GpuCompressedTextures)?.createMaterial(upload.image, TextureFiltering.Linear, MaterialUniformLayouts.PbrTextured.total),
                "the device refused ${upload.image.format}",
            )
            is Ktx2Upload.Rgba -> createMaterial(texture = upload.texture, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = PbrTextureSet())
            null -> error("the file didn't load")
        }
        return try {
            renderTexturedMaterialScene(material)
        } finally {
            material.destroy()
        }
    }

    private fun ByteArray.drawnPixels(): Int = indices.step(4).count { (this[it].toInt() and 0xFF) + (this[it + 1].toInt() and 0xFF) > DRAWN }

    /** Peak signal-to-noise ratio of [frame] against [reference] over red, green and blue, in dB. */
    private fun psnr(reference: ByteArray, frame: ByteArray): Double {
        var squared = 0.0
        for (i in reference.indices) {
            if (i % 4 == 3) continue
            val error = (reference[i].toInt() and 0xFF) - (frame[i].toInt() and 0xFF)
            squared += error * error
        }
        val mean = squared / (reference.size / 4 * 3)
        return if (mean == 0.0) Double.POSITIVE_INFINITY else 10 * log10(255.0 * 255.0 / mean)
    }

    private companion object {
        val ROCK: ByteArray = requireNotNull(CompressedTextureParityTest::class.java.getResourceAsStream("/cooked/rock.ktx2")).use { it.readBytes() }
        private var vulkan: HeadlessRenderSession? = null

        fun session(): HeadlessRenderSession = vulkan ?: openHeadlessScene(HeadlessUiBackend.Vulkan).also { vulkan = it }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            vulkan?.close()
            vulkan = null
        }

        const val MIN_PSNR = 30.0
        const val MIN_DRAWN_PIXELS = 1000
        const val DRAWN = 40
    }
}
