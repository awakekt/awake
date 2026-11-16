/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ShadowArraySizing
import com.awakekt.awake.render.passes.pointShadowMatrices
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.PointShadowLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WebGPU's twin of Vulkan's `RendererShadowLayersTest` (#1688): the shadow depth array starts at the
 * three default cascades and grows once, to ten layers, when a shadowed point light appears.
 *
 * WebGPU cannot rewrite a bind group, so the growth also rebuilds every cached shadow group in place;
 * `WebGpuPointLightShadowTest` checks by picture that the growing frame samples the new texture.
 */
class WebGpuShadowLayersTest {
    @Test
    fun aSunLitSceneKeepsItsCascadesAndAPointShadowGrowsTheArrayOnce() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val ground = renderer.createMesh(generate { plane(size = 20f, colored = false) })
        val box = renderer.createMesh(generate { cube(size = 2f, colored = false) })
        try {
            val draws = listOf(RenderDrawCommand(ground, material), RenderDrawCommand(box, material, Mat4().translate(2f, 1f, 0f)))
            val sunLit = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sunLit) }
            val cascadesOnly = renderer.shadowMapBytes
            assertEquals(LAYER_BYTES * ShadowBudget.DEFAULT_CASCADES, cascadesOnly, "a sun-lit scene keeps its three cascades")

            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sunLit.withPointShadow(renderer)) }
            val withPoint = renderer.shadowMapBytes
            println("WebGpuShadowLayers: cascades only ${cascadesOnly / MIB} MiB; with a point shadow ${withPoint / MIB} MiB; all 28 layers ${LAYER_BYTES * 28 / MIB} MiB")
            assertEquals(LAYER_BYTES * POINT_LAYERS, withPoint, "four cascade slots and one point light's six faces")
            assertTrue(renderer.bufferPools.shadowBindGroups.values.all { it.isNotEmpty() }, "the shadow groups were rebuilt, not dropped")
        } finally {
            box.destroy()
            ground.destroy()
            material.destroy()
            target.destroy()
        }
    }

    /**
     * The array's life from the engine's one-texel placeholder (#1698): nothing resident while no
     * light casts, the cascades at the budget's map size from the first shadowed frame, a map-size
     * change reallocating at once, and the array freed after the idle frames.
     */
    @Test
    fun theArrayIsHeldOnlyWhileAShadowIsDrawn() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val ground = renderer.createMesh(generate { plane(size = 20f, colored = false) })
        try {
            val draws = listOf(RenderDrawCommand(ground, material))
            val sunLit = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sun) }
            val unlit = renderer.shadowMapBytes
            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sunLit) }
            val lit = renderer.shadowMapBytes
            renderer.shadowBudget = ShadowBudget(cascades = ShadowBudget.DEFAULT_CASCADES, mapSize = REDUCED_SIZE)
            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sunLit) }
            val reduced = renderer.shadowMapBytes
            repeat(ShadowArraySizing.DEFAULT_RELEASE_AFTER_FRAMES) { renderer.renderSceneToTexture(target, lens, draws, sun) }
            val released = renderer.shadowMapBytes
            println("WebGpuShadowArrayLifecycle: unlit $unlit B; lit ${lit / MIB} MiB; at $REDUCED_SIZE ${reduced / MIB} MiB; released $released B")
            assertEquals(PLACEHOLDER_BYTES, unlit, "no casting light, only the one-texel placeholder")
            assertEquals(LAYER_BYTES * ShadowBudget.DEFAULT_CASCADES, lit)
            assertEquals(REDUCED_LAYER_BYTES * ShadowBudget.DEFAULT_CASCADES, reduced, "a map-size change reallocates at once")
            assertEquals(PLACEHOLDER_BYTES, released, "freed after the idle frames")
        } finally {
            ground.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun SceneLight.withPointShadow(renderer: Renderer): SceneLight {
        val position = Vec3f(0f, 3f, 0f)
        return copy(
            points = listOf(PointLight(position = position, color = Vec3f(1f, 1f, 1f), range = RANGE, shadowBaseLayer = MAX_SHADOW_CASCADES)),
            pointShadows = listOf(
                PointShadowLight(
                    baseLayer = MAX_SHADOW_CASCADES,
                    viewProjections = pointShadowMatrices(position = position, range = RANGE, clipSpace = renderer.clipSpace).viewProjections,
                ),
            ),
        )
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES = 3
        const val RANGE = 10f
        const val POINT_LAYERS = 10
        const val MIB = 1024L * 1024L
        const val LAYER_BYTES = 2048L * 2048L * 4L
        const val REDUCED_SIZE = 1024
        const val REDUCED_LAYER_BYTES = 1024L * 1024L * 4L
        const val PLACEHOLDER_BYTES = 4L
        val sun = SceneLight(direction = Vec3f(-1f, 1f, 0f), color = Vec3f(1f, 1f, 1f))
        val lens = Lens(eye = Vec3f(0f, 8f, 8f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
    }
}
