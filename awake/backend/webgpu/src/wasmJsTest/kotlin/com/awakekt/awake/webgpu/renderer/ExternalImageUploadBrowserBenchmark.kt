/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.image.BrowserImage
import com.awakekt.awake.core.image.decodeBrowserImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.webgpu.texture.Texture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Times what the main thread spends turning a decoded 1024x1024 image into a mipmapped texture, the
 * step after the decode `BrowserBitmapDecodeBenchmark` times (awakekt/awake#1184):
 * - step 1, the CPU path: the canvas readback (`BrowserImage.toBitmap`, as `createBitmap` reads),
 *   `toRgba8Bytes`, then the upload, whose CPU `mipChain` and per-level `writeTexture` follow;
 * - step 2, GPU direct: `copyExternalImageToTexture`, then the mips drawn on the GPU.
 *
 * Only the main thread is timed. The GPU's own work is not, and the queue is drained between runs so
 * neither path pays for the other's.
 *
 * Measured on headless Chromium with SwiftShader WebGPU, two runs each: production build, CPU path
 * 199 to 237 ms, GPU direct 2.4 to 3.0 ms (78 to 84x); development build 181 to 197 ms, 2.5 to 3.1 ms
 * (64 to 74x). With the decode's own main-thread share (12.8 to 16.2 ms GPU-bound, against 37 to 50 ms
 * for step 1, on the same machine) a mipmapped 1024x1024 texture holds the main thread for about 16 to
 * 19 ms in all, where step 1 held it for about 240 ms.
 *
 * The floor is a regression guard at about 15% of the lowest ratio measured: a readback or a CPU mip
 * chain slipping back into the GPU path brings it close to 1x.
 */
class ExternalImageUploadBrowserBenchmark {
    @Test
    fun aTextureSizedUpload() = runTest(timeout = 5.minutes) {
        val png = encodedImage("image/png", SIZE, SIZE, translucent = false)
        val engine = ImageUploadEngine()
        val renderer = engine.boot()
        val upload = renderer.capability(GpuImageUpload) as ExternalImageUpload
        var cpu = Duration.ZERO
        var gpu = Duration.ZERO
        try {
            repeat(WARM_UP + MEASURED) { run ->
                val keep = run >= WARM_UP
                decodeBrowserImage(png).use { image ->
                    cpu += timed(keep, renderer) { cpuUpload(renderer, image) }
                    gpu += timed(keep, renderer) { upload.upload(image, image.width, image.height, TextureFiltering.Linear) }
                }
            }
        } finally {
            engine.destroy()
        }
        println(
            "ExternalImageUploadBrowserBenchmark ${SIZE}x$SIZE mipmapped: main thread CPU path ${cpu / MEASURED}, " +
                "GPU direct ${gpu / MEASURED} (${cpu / gpu}x)",
        )
        assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        assertTrue(cpu / gpu >= MIN_SPEED_UP, "main thread $cpu on the CPU path, $gpu GPU direct")
    }

    private fun cpuUpload(renderer: Renderer, image: BrowserImage): Texture =
        renderer.uploadTexture(TextureAsset(image.toBitmap().toRgba8Bytes(), image.width, image.height, filtering = TextureFiltering.Linear))

    // Each texture is released after the GPU finished with it, outside the timing.
    private suspend fun timed(keep: Boolean, renderer: Renderer, block: () -> Texture): Duration {
        val start = TimeSource.Monotonic.markNow()
        val texture = block()
        val elapsed = start.elapsedNow()
        renderer.graphicsDevice.wgpuContext.device.queue.onSubmittedWorkDone().getOrThrow()
        renderer.textureResources.release(texture)
        return if (keep) elapsed else Duration.ZERO
    }

    private companion object {
        const val SIZE = 1024
        const val WARM_UP = 2
        const val MEASURED = 5
        const val MIN_SPEED_UP = 10.0
    }
}
