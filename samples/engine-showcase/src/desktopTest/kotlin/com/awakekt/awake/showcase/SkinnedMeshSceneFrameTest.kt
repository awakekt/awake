/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.ecs.remove
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.renderer.MAX_JOINTS
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

class SkinnedMeshSceneFrameTest {
    @Test
    fun instancedSkinnedShadowsToggleChangesPresentedFrame() = runBlocking {
        ShowcaseDebugToggles.shadows = true
        val app = engineShowcaseApp(initialShowcaseId = INSTANCED_SKINNED_SHOWCASE_ID)
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            repeat(POSED_FRAMES) { app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val shadowsPixels = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, shadowsPixels.copyOf()).writePng(File(INSTANCED_SKINNED_CAPTURE_PATH))
            ShowcaseDebugToggles.shadows = false
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val unshadowedPixels = renderer.readPresentedPixels().data
            assertTrue(
                countChangedPixels(shadowsPixels, unshadowedPixels) > MINIMUM_SHADOW_PIXELS,
                "The instanced-skinned Shadows toggle did not change the presented frame.",
            )
        } finally {
            ShowcaseDebugToggles.shadows = true
            app.dispose()
            renderer.destroy()
        }
    }

    /**
     * Exercises the actual showcase scene, animation system, RenderSystem3D, and presented Vulkan
     * frame. The zero-palette negative control changes the posed image; if the renderer silently
     * drops [SkinnedPose.jointPalette], the two frames are identical and this fails. Zeroing the
     * palette changes 17,059 presented pixels; the threshold stays safely below that control.
     */
    @Test
    fun posedSkinnedMeshReachesThePresentedFrame() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = SKINNED_SHOWCASE_ID)
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            repeat(POSED_FRAMES) { app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val posedPixels = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, posedPixels.copyOf()).writePng(File(POSED_CAPTURE_PATH))

            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            var pose: SkinnedPose? = null
            runtime.world.queryEach(Name::class, SkinnedPose::class) { entity, name, candidate ->
                if (name.value == SKINNED_NODE_NAME) {
                    entity.remove<Animator>(runtime.world)
                    pose = candidate
                }
            }
            val frozenPose = assertNotNull(pose, "The skinned showcase did not attach a joint palette.")
            frozenPose.jointPalette = FloatArray(MAX_JOINTS * MATRIX_FLOATS)

            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val zeroPalettePixels = renderer.readPresentedPixels().data
            PixelMap(WIDTH, HEIGHT, zeroPalettePixels.copyOf()).writePng(File(ZERO_PALETTE_CAPTURE_PATH))
            val changedPixels = countChangedPixels(posedPixels, zeroPalettePixels)

            assertTrue(
                changedPixels > MINIMUM_SKINNING_PIXELS,
                "Only $changedPixels pixels changed after zeroing the joint palette; the skinned " +
                    "payload may not be reaching the presented frame.",
            )
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    private fun countChangedPixels(before: ByteArray, after: ByteArray): Int {
        var changedPixels = 0
        for (index in 0 until WIDTH * HEIGHT) {
            val offset = index * 4
            if (before[offset] != after[offset] || before[offset + 1] != after[offset + 1] || before[offset + 2] != after[offset + 2]) {
                changedPixels++
            }
        }
        return changedPixels
    }

    private class HeadlessPlanEngine(
        lifecycle: AwakeAppLifecycle,
        plan: RenderPlan,
    ) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    private companion object {
        const val SKINNED_SHOWCASE_ID = "skinned-mesh"
        const val INSTANCED_SKINNED_SHOWCASE_ID = "instanced-skinned"
        const val SKINNED_NODE_NAME = "skinned-mesh"
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAME = 1f / 60f
        const val POSED_FRAMES = 30
        const val MATRIX_FLOATS = 16
        const val MINIMUM_SKINNING_PIXELS = 12_000
        const val MINIMUM_SHADOW_PIXELS = 100
        const val POSED_CAPTURE_PATH = "build/reports/render-captures/skinned-mesh-posed.png"
        const val ZERO_PALETTE_CAPTURE_PATH = "build/reports/render-captures/skinned-mesh-zero-palette.png"
        const val INSTANCED_SKINNED_CAPTURE_PATH = "build/reports/render-captures/instanced-skinned.png"
    }
}
