/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Times what the main thread spends making a glTF model's material whose base colour and normal maps
 * are 1024x1024, once the model is loaded (awakekt/awake#1184):
 * - before, the CPU path every renderer took: each map read back to the CPU, its mip chain built on
 *   the CPU, and every level written to the GPU ([cpuOnly], the renderer without its [GpuImageUpload]);
 * - after, WebGPU's GPU-direct upload: each map copied with `copyExternalImageToTexture` and its mips
 *   drawn on the GPU.
 *
 * Both start from the same preloaded model, whose images `decodeImage` left with the browser. The
 * readback the CPU path does here is what the loader used to do while preloading; the decode's own
 * main-thread share is `BrowserBitmapDecodeBenchmark`'s.
 *
 * Measured on headless Chromium with SwiftShader WebGPU: production build, two runs, CPU path 389 to
 * 449 ms, GPU direct 6.1 to 6.8 ms (64 to 66x); development build 458 ms against 5.9 ms (78x). The
 * floor is the regression guard `ExternalImageUploadBrowserBenchmark` keeps, about 15% of the lowest
 * ratio: a model whose maps slip back to the CPU path measures close to 1x.
 */
class GltfTextureLoadBrowserBenchmark {
    @Test
    fun aModelsTextureSizedMaps() = runTest(timeout = 5.minutes) {
        val engine = ImageUploadEngine()
        try {
            val renderer = engine.boot()
            val resolver = GltfAssetResolver()
            val path = "models/large.gltf"
            resolver.preload(path, texturedGltf(encodedImage("image/png", SIZE, SIZE, translucent = false), encodedImage("image/png", SIZE, SIZE, translucent = false)))
            val name = resolver.materialName(path)
            val upload = renderer.capability(GpuImageUpload) as ExternalImageUpload
            var cpu = Duration.ZERO
            var gpu = Duration.ZERO
            repeat(WARM_UP + MEASURED) { run ->
                val keep = run >= WARM_UP
                cpu += timed(keep, renderer) { resolver.createMaterial(cpuOnly(renderer), name) }
                gpu += timed(keep, renderer) { resolver.createMaterial(renderer, name) }
            }
            println(
                "GltfTextureLoadBrowserBenchmark two ${SIZE}x$SIZE maps: main thread CPU path ${cpu / MEASURED}, " +
                    "GPU direct ${gpu / MEASURED} (${cpu / gpu}x)",
            )
            assertEquals(2 * (WARM_UP + MEASURED), upload.copies, "every GPU-direct material copied both maps")
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
            assertTrue(cpu / gpu >= MIN_SPEED_UP, "main thread $cpu on the CPU path, $gpu GPU direct")
        } finally {
            engine.destroy()
        }
    }

    // The material is destroyed after the GPU finished with it, outside the timing.
    private suspend fun timed(keep: Boolean, renderer: Renderer, make: () -> Material?): Duration {
        val start = TimeSource.Monotonic.markNow()
        val material = assertNotNull(make())
        val elapsed = start.elapsedNow()
        renderer.graphicsDevice.wgpuContext.device.queue.onSubmittedWorkDone().getOrThrow()
        material.destroy()
        return if (keep) elapsed else Duration.ZERO
    }

    private companion object {
        const val SIZE = 1024
        const val WARM_UP = 2
        const val MEASURED = 5
        const val MIN_SPEED_UP = 10.0
    }
}
