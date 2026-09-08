/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

class HeightfieldSceneFrameTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    /**
     * Every authored object stands where the scene put it, not stacked at the origin.
     *
     * The heightfield showcase draws nine cubes on a terrain, and the failure this guards is one
     * that leaves the frame looking plausible: with no `TransformSystem` in the frame, every
     * `Transform.worldMatrix` stays identity and all nine draw on top of each other at the world
     * origin. What you get is a terrain and *one* cube -- which reads as "the boxes have not fallen
     * yet" rather than as a missing system.
     *
     * Counted as cube pixels, because the cube mesh is vertex-coloured and the terrain is not: the
     * terrain's own ramp is green-dominant everywhere, so a pixel whose red or blue rivals its
     * green came from a cube. Measured with the transforms propagated: well over 12k such pixels.
     * Measured with `TransformSystem` removed: **65** -- the nine cubes collapse onto each other at
     * the origin, where the terrain hides all but a sliver of the one that remains. That is the
     * number this guards against, and it is far lower than a guess would have put it.
     */
    @Test
    fun everyObjectStandsWhereTheSceneAuthoredIt() = runBlocking {
        val engine = HeadlessPlanEngine(headlessLifecycle(), EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            renderer.shadowsEnabled = true
            val scene = SceneLoader.instantiate(
                SceneLoader.loadFromResource("assets/examples/heightfield-terrain.scene.json"),
            )
            val terrain = renderer.createMesh(TerrainExampleAsset.geometry)
            val cube = renderer.createMesh(generate { cube(size = 1f, colored = true) })
            val material = renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
            scene.attachRenderableComponents { request ->
                MeshRenderer(if (request.meshRenderer.mesh == "heightfield-terrain") terrain else cube, material)
            }
            val transformSystem = TransformSystem()
            val renderSystem = RenderSystem(renderer)
            transformSystem.update(scene.world, FRAME)
            repeat(4) { renderSystem.update(scene.world, FRAME) }

            val pixels = (renderer as VulkanRenderer).readPresentedPixels().data
            var cubePixels = 0
            for (index in 0 until WIDTH * HEIGHT) {
                val r = pixels[index * 4].toInt() and 0xFF
                val g = pixels[index * 4 + 1].toInt() and 0xFF
                val b = pixels[index * 4 + 2].toInt() and 0xFF
                // Terrain is green-dominant by construction; a cube face is not.
                if (g > BACKGROUND && (r >= g || b >= g)) cubePixels++
            }
            terrain.destroy()
            cube.destroy()
            material.destroy()
            assertTrue(
                cubePixels > MIN_CUBE_PIXELS,
                "only $cubePixels cube pixels are on screen, against about $STACKED_CUBE_PIXELS " +
                    "for every object stacked at the origin -- the scene's transforms are not " +
                    "reaching the frame.",
            )
        } finally {
            renderer.destroy()
        }
    }

    private fun headlessLifecycle(): AwakeAppLifecycle = appSpec {
        window {
            title = "heightfield-capture"
            size(960, 540)
        }
    }.createLifecycle()

    private class HeadlessPlanEngine(
        lifecycle: AwakeAppLifecycle,
        plan: RenderPlan,
    ) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAME = 1f / 60f

        /** Above the cleared sky, so background pixels are not counted as anything. */
        const val BACKGROUND = 20

        /** Comfortably under what a correct frame draws, comfortably over the broken one's 65. */
        const val MIN_CUBE_PIXELS = 12000

        /**
         * Measured with `TransformSystem` removed: nine cubes stacked at the origin, nearly all of
         * it behind the terrain. Recorded because the failure is quiet -- a frame with a terrain
         * and no objects reads as "the boxes have not fallen yet".
         */
        const val STACKED_CUBE_PIXELS = 65
    }
}
