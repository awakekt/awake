/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Boots the showcase's actual plan through WebGpuEngine, including its content features and
 * scene-depth pass. The fixture imports the plan's source, never a second list of pipelines.
 * Desktop wgpu-native exercises the production WebGPU bootstrap; browser host behavior is
 * covered separately by the Wasm browser suite.
 *
 * Measured on desktop: center green 239 with the plan's fog, 250 without content features;
 * 1,024 pixels differ by more than five channel levels. Negative control: removing the
 * production plan's content features gives green 250 and zero changed pixels, failing the
 * content assertion. This catches a bootstrap or offscreen recorder that omits plan features.
 * A second control removes the geometry submission: both center values and the changed-pixel
 * count become zero, failing the lit-geometry assertion.
 */
class WebGpuProductionPlanTest {
    @Test
    fun productionPlanRendersLitGeometryAndDepthFogOffscreen() = runBlocking {
        val baseline = capture(EngineShowcaseRenderPlan.copy(contentFeatures = emptyList()))
        val production = capture(EngineShowcaseRenderPlan)
        val center = (SIZE / 2 * SIZE + SIZE / 2) * CHANNELS
        val baselineGreen = baseline[center + 1].toInt() and 0xFF
        val productionGreen = production[center + 1].toInt() and 0xFF
        val changed = (0 until SIZE * SIZE).count { pixel ->
            (0 until RGB_CHANNELS).any { channel ->
                val index = pixel * CHANNELS + channel
                kotlin.math.abs((production[index].toInt() and 0xFF) - (baseline[index].toInt() and 0xFF)) > CHANNEL_TOLERANCE
            }
        }
        println("Production plan: center green=$productionGreen, geometry-only=$baselineGreen, content-changed pixels=$changed")
        assertTrue(baselineGreen > MIN_LIT_CHANNEL, "Production geometry must receive direct light: $baselineGreen")
        assertTrue(productionGreen > MIN_LIT_CHANNEL, "Content features must preserve the lit geometry: $productionGreen")
        assertTrue(changed > MIN_CHANGED_PIXELS, "The production content features must affect the offscreen frame: $changed pixels")
    }

    private suspend fun capture(plan: RenderPlan): ByteArray = withProductionPlan(plan) { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(generate { cube(size = 2f) })
        val material = renderer.createMaterial(LitShadowUniformLayout)
        try {
            val camera = Lens(eye = Vec3f(0f, 0f, 20f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = 2f
            }
            renderer.renderSceneToTexture(
                target,
                camera,
                listOf(RenderDrawCommand(mesh, material)),
                SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
            )
            renderer.readPixels(target).data
        } finally {
            renderer.waitIdle()
            material.destroy()
            mesh.destroy()
            target.destroy()
        }
    }

    /** Uses normal engine initialization and disposal, so the pipeline registry owns teardown. */
    private suspend fun <T> withProductionPlan(plan: RenderPlan, block: suspend (Renderer) -> T): T {
        val session = webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
        try {
            return block(session.renderer)
        } finally {
            session.close()
        }
    }

    private companion object {
        const val SIZE = 64
        const val CHANNELS = 4
        const val RGB_CHANNELS = 3
        const val CHANNEL_TOLERANCE = 5
        const val MIN_LIT_CHANNEL = 100
        const val MIN_CHANGED_PIXELS = 500
    }
}
