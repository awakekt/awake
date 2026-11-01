/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.ktx.loader.Ktx2Formats
import com.awakekt.awake.asset.ktx.loader.Ktx2TextureLoader
import com.awakekt.awake.asset.ktx.loader.Ktx2Upload
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.pipeline.compressedTextureFormats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.runBlocking
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The mobile half of #1211 on Android Vulkan: the ASTC and ETC2 files the cook writes load as they are,
 * with no transcoder, and draw like the same pixels in RGBA8. The emulator's software Vulkan enables
 * both families, which lavapipe, where the desktop tests run, has neither of.
 *
 * Each texture fills a [SIZE]-square target through the UI's textured quad, and is compared, as PSNR,
 * with the source pattern drawn the same way from RGBA8, so the path's own filtering cancels out.
 */
class CompressedTextureDeviceTest {

    @Test
    fun theCookedAstcAndEtc2FilesDrawAsTheirSourceDoes() = runBlocking {
        val renderer = Engine().boot()
        try {
            val formats = renderer.compressedTextureFormats
            assertTrue(Astc in formats && Etc2 in formats, "the emulator's Vulkan enables ASTC and ETC2; this one takes $formats")
            assertTrue(Ktx2Formats.variants("rock.ktx2", formats).first() == "rock.astc.ktx2", "ASTC is the cooked file tried first")
            val reference = renderer.draw(renderer.createMaterial(texture = TextureAsset(CookedMobileTextures.sourceRgba, SIZE, SIZE)))

            for ((format, file) in listOf(Astc to CookedMobileTextures.astc, Etc2 to CookedMobileTextures.etc2)) {
                val upload = assertIs<Ktx2Upload.Compressed>(Ktx2TextureLoader.decode(file, formats, transcoder = null), "$format")
                val material = assertNotNull(
                    renderer.capability(GpuCompressedTextures)?.createMaterial(upload.image, TextureFiltering.Linear, GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT),
                    "the device refused $format",
                )
                val psnr = psnr(reference, renderer.draw(material))
                println("CompressedTextureDeviceTest: $format draws ${"%.1f".format(psnr)} dB from the RGBA8 source")
                assertTrue(psnr > MIN_PSNR, "$format draws ${"%.1f".format(psnr)} dB from the RGBA8 source")
            }
        } finally {
            renderer.destroy()
        }
    }

    /** [material] drawn over a [SIZE]-square target and read back; [material] is destroyed after. */
    private suspend fun Renderer.draw(material: Material): ByteArray {
        val target = createRenderTarget(SIZE, SIZE)
        return try {
            drawUiToTexture(target, listOf(UiDrawPrimitive.Texture(x = 0f, y = 0f, w = SIZE.toFloat(), h = SIZE.toFloat(), material = material)))
            readPixels(target).data
        } finally {
            target.destroy()
            material.destroy()
        }
    }

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

    private class Engine : VulkanEngine(appSpec { window { size(SIZE, SIZE) } }.createLifecycle(), EngineShowcaseRenderPlan) {
        suspend fun boot() = createBackendResources(HeadlessSurface(SIZE, SIZE)).renderer
    }

    private companion object {
        const val SIZE = CookedMobileTextures.SIZE
        const val MIN_PSNR = 30.0
        val Astc = CompressedTextureFormat.Astc4x4
        val Etc2 = CompressedTextureFormat.Etc2Rgba8
    }
}
