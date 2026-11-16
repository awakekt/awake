/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.image.decodeBrowserImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.render.pipeline.TextureUpload
import com.awakekt.awake.render.pipeline.TextureUploadObserver
import com.awakekt.awake.render.pipeline.TextureUploadPath
import com.awakekt.awake.render.pipeline.TextureUploads
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.webgpu.texture.mipLevelCount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * `Renderer.textureMipSkip` on the path most browser images take (#1660): a PNG or JPEG copied on the
 * GPU has its chain drawn whole, then keeps only the levels from the skip down, as a CPU upload does.
 * At skip 1 the copy is half size with one level fewer, holds what the CPU path uploads level for level,
 * and the upload observer reports the half-size image.
 */
class ExternalImageMipSkipBrowserTest {
    @Test
    fun aCopiedImageAtSkipOneIsHalfSizeAndMatchesTheCpuUpload() = runTest(timeout = TIMEOUT) {
        val engine = ImageUploadEngine()
        try {
            val renderer = engine.boot()
            renderer.textureMipSkip = 1
            decodeBrowserImage(encodedImage("image/png", SIDE, SIDE, translucent = false)).use { image ->
                val cpu = renderer.uploadTexture(TextureAsset(image.toBitmap().toRgba8Bytes(), SIDE, SIDE))
                val gpu = checkNotNull((renderer.capability(GpuImageUpload) as ExternalImageUpload).upload(image, SIDE, SIDE, TextureFiltering.Linear)) {
                    "the browser refused the copy"
                }
                assertEquals(SIDE, gpu.width, "the source size is kept")
                assertEquals(SIDE / 2, gpu.uploadedWidth, "the GPU image is half size")
                assertEquals(SIDE / 2, gpu.uploadedHeight)
                assertEquals(mipLevelCount(SIDE, SIDE) - 1, gpu.uploadedMipLevels)
                assertEquals(cpu.uploadedMipLevels, gpu.uploadedMipLevels, "the same levels as the CPU path")
                for (level in 0 until gpu.uploadedMipLevels) {
                    // Level 0 here is the source's level 1, drawn on the GPU against box-filtered on the CPU.
                    assertTexels("level $level", renderer.readLevel(cpu, level), renderer.readLevel(gpu, level), tolerance = (level + 2) / 2)
                }

                val seen = mutableListOf<TextureUpload>()
                TextureUploads.observer = TextureUploadObserver { seen += it }
                try {
                    renderer.createImageMaterial(image.asGpuImageSource(), TextureFiltering.Linear, UNIFORM_FLOATS, null, label = "copied").destroy()
                } finally {
                    TextureUploads.observer = null
                }
                val upload = seen.single()
                assertEquals(TextureUploadPath.GpuCopy, upload.path)
                assertEquals(SIDE / 2, upload.uploadedWidth, "the observer sees the half-size copy")
                println("ExternalImageMipSkip: $upload, ${upload.uploadedMipLevels} levels")
            }
            println("ExternalImageMipSkip: ${engine.errors.size} WebGPU errors ${engine.errors.take(3)}")
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            engine.destroy()
        }
    }

    private companion object {
        val TIMEOUT = 2.minutes
        const val SIDE = 64
        const val UNIFORM_FLOATS = 24
    }
}
