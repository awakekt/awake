/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.depthFogContentFeature
import com.awakekt.awake.asset.shaderpack.outlineContentFeature
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.ShadowArrayResidency
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WebGPU counterpart of Vulkan's `RendererSceneDepthResidencyTest`: verifies dynamic
 * `needsSceneDepth` pass skipping, independent lazy 1x1 residency for `sceneDepthTarget` and
 * `maskTarget`, and `Depth16Unorm` target sizing.
 */
class WebGpuSceneDepthResidencyTest {

    @Test
    fun sceneDepthAndMaskFollowDynamicDemandWithD16AndLazyResidency() = runBlocking {
        var fogVisible = false
        val plan = EngineShowcaseRenderPlan.copy(
            contentFeatures = listOf(
                depthFogContentFeature(Color(0.5f, 0.6f, 0.7f, 0.02f)) { fogVisible },
                outlineContentFeature(),
            ),
        )
        WebGpuUncapturedErrors.drain()
        val engine = HeadlessWebGpuEngine(buildResidencyLifecycle(), plan)
        try {
            val renderer = engine.boot(HeadlessSurface(SIZE, SIZE)) as Renderer
            val sceneDepthPass = requireNotNull(renderer.sceneDepthPass)
            val maskPass = requireNotNull(renderer.maskPass)
            val idleInput = GpuPassInput(
                viewProjection = Mat4(),
                cameraEye = Vec3f(0f, 0f, 5f),
                passUniforms = FloatArray(0),
                resolvedPath = true,
            )
            val maskedInput = idleInput.copy(
                maskPasses = listOf(GpuSubPass(target = null, targetLayer = 0, viewProjection = Mat4())),
            )
            fun drawAndPresent(input: GpuPassInput) {
                renderer.draw(input)
                engine.present()
            }

            assertEquals(ShadowDepthFormat.Depth16Unorm, sceneDepthPass.depthTarget.format)
            assertEquals(ShadowDepthFormat.Depth16Unorm, maskPass.depthTarget.format)
            assertEquals(2L, renderer.sceneDepthTargetBytes, "dormant 1x1 D16 sceneDepthTarget is 2 bytes")
            assertEquals(2L * MASK_TARGET_LAYER_COUNT, renderer.maskTargetBytes, "dormant 1x1x3 D16 maskTarget is 6 bytes")

            drawAndPresent(idleInput)
            val scenePassesAfterInit = sceneDepthPass.passesRecorded
            val maskPassesAfterInit = maskPass.passesRecorded
            repeat(3) { drawAndPresent(idleInput) }
            assertFalse(sceneDepthPass.depthTarget.resident)
            assertFalse(maskPass.depthTarget.resident)
            assertEquals(scenePassesAfterInit, sceneDepthPass.passesRecorded, "idle frames must record 0 scene-depth passes")
            assertEquals(maskPassesAfterInit, maskPass.passesRecorded, "idle frames must record 0 mask passes")

            fogVisible = true
            drawAndPresent(idleInput)
            assertTrue(sceneDepthPass.depthTarget.resident)
            assertEquals(SIZE.toLong() * SIZE.toLong() * 2L, renderer.sceneDepthTargetBytes)
            assertFalse(maskPass.depthTarget.resident)

            fogVisible = false
            repeat(ShadowArrayResidency.DEFAULT_RELEASE_AFTER_FRAMES * 2) { drawAndPresent(idleInput) }
            assertFalse(sceneDepthPass.depthTarget.resident)
            assertEquals(2L, renderer.sceneDepthTargetBytes)

            drawAndPresent(maskedInput)
            assertTrue(maskPass.depthTarget.resident)
            assertEquals(SIZE.toLong() * SIZE.toLong() * MASK_TARGET_LAYER_COUNT * 2L, renderer.maskTargetBytes)
            assertFalse(sceneDepthPass.depthTarget.resident)
        } finally {
            engine.destroy()
            WebGpuUncapturedErrors.failIfAny("WebGpuSceneDepthResidencyTest")
        }
    }

    private fun buildResidencyLifecycle(): AwakeAppLifecycle {
        val settings = RenderQualitySettings().apply { shadowDepthFormat = ShadowDepthFormat.Depth16Unorm }
        return AppSpecBuilder().apply {
            window {
                title = "scene-depth-residency"
                size(SIZE, SIZE)
            }
            service(RenderQualitySettings::class, settings)
        }.build().createLifecycle()
    }

    private companion object {
        const val SIZE = 128
    }
}
