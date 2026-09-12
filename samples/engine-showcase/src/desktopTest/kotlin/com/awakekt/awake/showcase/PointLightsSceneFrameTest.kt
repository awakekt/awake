/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Captures the point-light sample and verifies that authored point shadows reach the frame. */
class PointLightsSceneFrameTest {
    @Test
    fun pointLightsCastShadows() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "point-lights")
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            app.ready(renderer)
            repeat(4) { app.update(1f / 60f, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val pixels = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, pixels).writePng(File(CAPTURE_PATH))
            assertTrue(pixels.any { (it.toInt() and 0xff) > 24 }, "Point-light sample rendered no lit pixels")
            ShowcaseDebugToggles.shadows = false
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val unshadowed = renderer.readPresentedPixels().data
            PixelMap(WIDTH, HEIGHT, unshadowed.copyOf()).writePng(File(UNSHADOWED_CAPTURE_PATH))
            assertTrue(
                changedScenePixels(pixels, unshadowed) > MINIMUM_SHADOW_PIXELS,
                "Disabling point-light shadows did not change the presented point-light sample.",
            )
        } finally {
            ShowcaseDebugToggles.shadows = true
            app.dispose()
            renderer.destroy()
        }
    }

    private fun changedScenePixels(before: ByteArray, after: ByteArray): Int =
        (200 until 770).sumOf { x ->
            (200 until HEIGHT).count { y ->
                val offset = (y * WIDTH + x) * 4
                before[offset] != after[offset] ||
                    before[offset + 1] != after[offset + 1] ||
                    before[offset + 2] != after[offset + 2]
            }
        }

    private class HeadlessPlanEngine(
        lifecycle: com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle,
        plan: com.awakekt.awake.asset.shaders.RenderPlan,
    ) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface) = createBackendResources(surface).renderer
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val CAPTURE_PATH = "build/reports/render-captures/point-lights-after.png"
        const val UNSHADOWED_CAPTURE_PATH = "build/reports/render-captures/point-lights-after-shadows-off.png"
        const val MINIMUM_SHADOW_PIXELS = 100
    }
}
