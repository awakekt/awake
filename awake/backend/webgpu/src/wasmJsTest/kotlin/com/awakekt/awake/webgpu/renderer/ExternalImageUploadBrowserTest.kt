/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.image.decodeBrowserImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.webgpu.texture.mipLevelCount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/**
 * A texture copied from a browser image on the GPU holds what the CPU decode uploads for the same file:
 * the same orientation (bottom row first), the same straight-alpha colour, and mips within rounding.
 * Each texture is read back level by level and compared texel by texel, under the CI browser's
 * SwiftShader WebGPU (awakekt/awake#1184).
 *
 * Measured on headless Chromium with SwiftShader:
 * - level 0 is exact for the opaque PNG, the sRGB JPEG and the translucent PNG. The fixtures are
 *   encoded by a 2D canvas, so a translucent file's colour already survives the premultiply that the
 *   CPU readback applies; on such a file the two paths agree to the bit.
 * - mip level n is within `(n + 1) / 2` per channel: 1, 1, 2, 2, 2, 3, 3, 4 measured for levels 1 to 8.
 *   The GPU's bilinear average rounds where the CPU box filter truncates, and each level is built from
 *   the one above, so the gap grows by about half a unit a level.
 *
 * Negative controls, measured: `flipY: false` in the copy fails all three at the bottom row (the JPEG's
 * texel 0: GPU (2,0,15,255), CPU (134,125,126,255)); `premultipliedAlpha: true` fails the translucent PNG
 * at texel 2 (GPU (0,2,2,2), CPU (0,255,255,2)); skipping the GPU mip passes fails level 1 at texel 0
 * (GPU (0,0,0,0), CPU (130,251,3,255)). A texture without `CopySrc` reads back as zeros on both sides,
 * which is why every case also asserts no validation error.
 */
class ExternalImageUploadBrowserTest {
    @Test
    fun anAlphaPngMatchesTheCpuUpload() = runTest(timeout = TIMEOUT) {
        compare("alpha PNG 256x256", encodedImage("image/png", 256, 256, translucent = true), TextureFiltering.BaseLevelLinear)
    }

    @Test
    fun anSrgbJpegMatchesTheCpuUpload() = runTest(timeout = TIMEOUT) {
        compare("sRGB JPEG 256x128", encodedImage("image/jpeg", 256, 128, translucent = false), TextureFiltering.Nearest)
    }

    @Test
    fun aMipmappedTextureMatchesTheCpuMipChain() = runTest(timeout = TIMEOUT) {
        compare("mipmapped PNG 256x256", encodedImage("image/png", 256, 256, translucent = false), TextureFiltering.Linear)
    }

    // The capability copies a BrowserImage, and declines a source with nothing to copy, which then goes
    // through readRgba8: the selection rule createImageMaterial documents.
    @Test
    fun theCapabilityCopiesBrowserImagesAndDeclinesTheRest() = runTest(timeout = TIMEOUT) {
        withRenderer { renderer ->
            val upload = assertNotNull(renderer.capability(GpuImageUpload))
            decodeBrowserImage(encodedImage("image/png", 8, 8, translucent = false)).use { image ->
                val source = image.asGpuImageSource()
                assertNotNull(upload.createMaterial(source, TextureFiltering.Linear, UNIFORM_FLOATS, null)).destroy()
                var readBack = 0
                val cpuOnly = object : GpuImageSource {
                    override val width = 8
                    override val height = 8
                    override val externalImage: Any? = null
                    override fun readRgba8(): ByteArray = source.readRgba8().also { readBack++ }
                }
                assertNull(upload.createMaterial(cpuOnly, TextureFiltering.Linear, UNIFORM_FLOATS, null))
                renderer.createImageMaterial(cpuOnly, uniformFloatCount = UNIFORM_FLOATS).destroy()
                assertEquals(1, readBack, "the fallback reads the pixels once")
                image.close()
                assertNull(upload.createMaterial(source, TextureFiltering.Linear, UNIFORM_FLOATS, null), "a closed image has nothing to copy")
            }
            assertEquals(emptyList(), renderer.errors(), "WebGPU validation errors")
        }
    }

    private suspend fun compare(name: String, file: ByteArray, filtering: TextureFiltering) = withRenderer { renderer ->
        decodeBrowserImage(file).use { image ->
            val cpu = renderer.uploadTexture(TextureAsset(image.toBitmap().toRgba8Bytes(), image.width, image.height, filtering = filtering))
            val gpu = (renderer.capability(GpuImageUpload) as ExternalImageUpload).upload(image, image.width, image.height, filtering)
            val levels = if (filtering == TextureFiltering.Linear) mipLevelCount(image.width, image.height) else 1
            for (level in 0 until levels) {
                val want = renderer.readLevel(cpu, level)
                val got = renderer.readLevel(gpu, level)
                assertTexels("$name level $level", want, got, tolerance = (level + 1) / 2)
            }
        }
        assertEquals(emptyList(), renderer.errors(), "$name: WebGPU validation errors")
    }

    private var engine: ImageUploadEngine? = null

    private suspend fun withRenderer(block: suspend (Renderer) -> Unit) {
        val booted = ImageUploadEngine()
        engine = booted
        try {
            block(booted.boot())
        } finally {
            booted.destroy()
        }
    }

    private fun Renderer.errors(): List<String> = engine?.errors.orEmpty()

    private companion object {
        val TIMEOUT = 2.minutes
        const val UNIFORM_FLOATS = 24
    }
}
