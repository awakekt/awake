/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/** Captures the real instanced-cubes showcase for regressions in instance upload and binding. */
class InstancedCubesSceneFrameTest {
    @Test
    fun instancedCubeGridIsTemporallyStableAcrossFrameSlots() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = SHOWCASE_ID)
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            repeat(WARMUP_FRAMES) { app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val baseline = renderer.readPresentedPixels().data.copyOf()
            var maximumDrift = 0
            repeat(TEMPORAL_FRAMES) {
                app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
                maximumDrift = maxOf(maximumDrift, changedRgbPixels(baseline, renderer.readPresentedPixels().data))
            }
            assertTrue(maximumDrift <= MAX_TEMPORAL_DRIFT_PIXELS, "Instanced frame drift was $maximumDrift pixels")
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    @Test
    fun instancedCubeGridReachesThePresentedFrame() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = SHOWCASE_ID)
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val pixels = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, pixels.copyOf()).writePng(File(CAPTURE_PATH))

            assertTrue(
                pixels.count { (it.toInt() and 0xff) > BACKGROUND } > MINIMUM_LIT_BYTES,
                "The instanced-cubes showcase did not produce a visible presented frame.",
            )

            // A saturated clear colour distinguishes a translucent/uninitialized draw from the
            // old black background: a real transparent center box would change colour here.
            renderer.clearColor = CONTRAST_BACKGROUND
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val contrastPixels = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, contrastPixels.copyOf()).writePng(File(CONTRAST_CAPTURE_PATH))
            assertTrue(
                changedRgbPixels(pixels, contrastPixels) > MINIMUM_BACKGROUND_PIXELS,
                "The contrast background did not reach the presented frame.",
            )
            renderer.clearColor = Color.Black
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())

            ShowcaseDebugToggles.showInstanceBounds = true
            // The debug line upload is consumed by the following presentation frame.
            repeat(2) { app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val withBounds = renderer.readPresentedPixels().data
            PixelMap(WIDTH, HEIGHT, withBounds.copyOf()).writePng(File(INSTANCE_BOUNDS_CAPTURE_PATH))
            assertTrue(
                changedRgbPixels(pixels, withBounds) > MINIMUM_DIAGNOSTIC_PIXELS,
                "Instance bounds did not reach enough presented pixels to diagnose submitted instances.",
            )
        } finally {
            ShowcaseDebugToggles.showInstanceBounds = false
            app.dispose()
            renderer.destroy()
        }
    }

    private fun changedRgbPixels(before: ByteArray, after: ByteArray): Int =
        (0 until WIDTH * HEIGHT).count { pixel ->
            val offset = pixel * RGBA_CHANNELS
            before[offset] != after[offset] || before[offset + 1] != after[offset + 1] || before[offset + 2] != after[offset + 2]
        }

    private class HeadlessPlanEngine(
        lifecycle: AwakeAppLifecycle,
        plan: RenderPlan,
    ) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    private companion object {
        const val SHOWCASE_ID = "instanced-cubes"
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAME = 1f / 60f
        const val WARMUP_FRAMES = 10
        const val TEMPORAL_FRAMES = 300
        const val MAX_TEMPORAL_DRIFT_PIXELS = 100
        const val BACKGROUND = 20
        const val MINIMUM_LIT_BYTES = 1_000
        const val MINIMUM_DIAGNOSTIC_PIXELS = 1_000
        const val MINIMUM_BACKGROUND_PIXELS = 100_000
        const val RGBA_CHANNELS = 4
        val CONTRAST_BACKGROUND = Color(r = 0.85f, g = 0.05f, b = 0.75f, a = 1f)
        const val CAPTURE_PATH = "build/reports/render-captures/instanced-cubes-v2.png"
        const val CONTRAST_CAPTURE_PATH = "build/reports/render-captures/instanced-cubes-contrast.png"
        const val INSTANCE_BOUNDS_CAPTURE_PATH = "build/reports/render-captures/instanced-cubes-bounds.png"
    }
}
