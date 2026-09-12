/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.ecs.update
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
            val renderSystem = RenderSystem3D(renderer)
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

    /**
     * Runs the app that users actually launch: its scene loader, fixed Jolt systems, render
     * system, and presented Vulkan frame all participate. After four seconds, three boxes are
     * resting at y=1.28, 1.35 and 1.54; the fourth has reached the deliberately active goal
     * zone and is collected. The frame differs from the first falling frame at 299,173 pixels.
     *
     * The zero-delta control changes zero pixels. It would also be zero if the fixed physics
     * schedule stopped advancing, whereas the settled-frame measurement proves that its changing
     * transforms reached the pixels that an application presents.
     */
    @Test
    fun liveShowcasePhysicsKeepsFallingBoxesAboveTheRenderedTerrain() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "heightfield-terrain")
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            val vulkanRenderer = renderer as VulkanRenderer
            app.ready(renderer)
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val fallingPixels = vulkanRenderer.readPresentedPixels().data.copyOf()
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val frozenPixels = vulkanRenderer.readPresentedPixels().data
            repeat(PHYSICS_STEPS - 1) { app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }

            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            val fallingHeights = mutableMapOf<String, Float>()
            runtime.world.queryEach(Name::class, Transform::class) { _, name, transform ->
                if (name.value.startsWith("falling-box-")) fallingHeights[name.value] = transform.position.y
            }
            val settledPixels = vulkanRenderer.readPresentedPixels().data
            PixelMap(WIDTH, HEIGHT, settledPixels.copyOf()).writePng(File(CAPTURE_PATH))
            val frozenChanges = countChangedPixels(fallingPixels, frozenPixels)
            val changedPixels = countChangedPixels(fallingPixels, settledPixels)

            assertEquals(4, fallingHeights.size + TerrainPhysicsExampleDriver.collected)
            assertEquals(1, TerrainPhysicsExampleDriver.collected)
            assertTrue(fallingHeights.values.all { it > MINIMUM_RESTING_Y })
            assertEquals(0, frozenChanges, "a zero-delta frame moved pixels before physics could advance")
            assertTrue(
                changedPixels > MINIMUM_SETTLED_FRAME_CHANGES,
                "only $changedPixels pixels changed after physics settled; measured correct scene has 299173",
            )
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    /**
     * The developer-facing terrain probe must reach the presented frame, not merely construct a
     * line list. It overlays the cyan render grid, magenta collision samples, yellow normals, and
     * RGB local axes over an otherwise identical zero-delta frame.
     */
    @Test
    fun terrainDiagnosticOverlayReachesThePresentedFrame() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "heightfield-terrain")
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val withoutDiagnostics = renderer.readPresentedPixels().data.copyOf()

            ShowcaseDebugToggles.showTerrainDiagnostics = true
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val withDiagnostics = renderer.readPresentedPixels().data
            PixelMap(WIDTH, HEIGHT, withDiagnostics.copyOf()).writePng(File(DIAGNOSTIC_CAPTURE_PATH))

            assertTrue(
                countChangedPixels(withoutDiagnostics, withDiagnostics) > MINIMUM_DIAGNOSTIC_PIXELS,
                "Terrain diagnostics did not reach enough presented pixels to be usable.",
            )
        } finally {
            ShowcaseDebugToggles.showTerrainDiagnostics = false
            app.dispose()
            renderer.destroy()
        }
    }

    /**
     * Separates the two rendering controls most likely to make a correctly-authored terrain look
     * inside-out. Disabling shadows changes thousands of pixels in the real showcase scene; removing
     * terrain back-face culling must not change camera-facing geometry. This guards both the
     * debugger override and the Vulkan front-face convention.
     */
    @Test
    fun heightfieldShadowAndCullingControlsReachThePresentedFrame() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "heightfield-terrain")
        val engine = HeadlessPlanEngine(app, EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT)) as VulkanRenderer
        try {
            app.ready(renderer)
            app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val baseline = renderer.readPresentedPixels().data.copyOf()

            ShowcaseDebugToggles.shadows = false
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val unshadowed = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, unshadowed).writePng(File(UNSHADOWED_CAPTURE_PATH))

            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            runtime.world.queryEach(Name::class, MeshRenderer::class) { entity, name, _ ->
                if (name.value == "heightfield-terrain") {
                    runtime.world.update<MeshRenderer>(entity) { it.copy(cullMode = CullMode.None) }
                }
            }
            ShowcaseDebugToggles.shadows = true
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val uncullled = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, uncullled).writePng(File(UNCULLED_CAPTURE_PATH))

            val shadowChanges = countChangedPixels(baseline, unshadowed)
            val cullingChanges = countChangedPixels(baseline, uncullled)
            assertTrue(
                shadowChanges > MINIMUM_SHADOW_TOGGLE_PIXELS,
                "Disabling shadows changed only $shadowChanges pixels; the debug override was probably ignored.",
            )
            assertEquals(0, cullingChanges, "Terrain culling changed $cullingChanges pixels.")
        } finally {
            ShowcaseDebugToggles.shadows = true
            app.dispose()
            renderer.destroy()
        }
    }

    private fun countChangedPixels(before: ByteArray, after: ByteArray): Int {
        var changedPixels = 0
        // The showcase now renders live FPS/draw counters over the left and right edges. Those
        // labels intentionally change when a zero-delta frame is submitted or culling is toggled;
        // these assertions measure the 3D scene, so exclude the two UI columns.
        for (y in 0 until HEIGHT) {
            for (x in SCENE_X_START until SCENE_X_END) {
                val offset = (y * WIDTH + x) * 4
                if (before[offset] != after[offset] || before[offset + 1] != after[offset + 1] || before[offset + 2] != after[offset + 2]) {
                    changedPixels++
                }
            }
        }
        return changedPixels
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
        const val SCENE_X_START = 200
        const val SCENE_X_END = 770
        const val FRAME = 1f / 60f
        const val PHYSICS_STEPS = 240
        const val MINIMUM_RESTING_Y = 1.1f
        const val MINIMUM_SETTLED_FRAME_CHANGES = 50_000
        const val CAPTURE_PATH = "build/reports/render-captures/heightfield-live-physics.png"
        const val DIAGNOSTIC_CAPTURE_PATH = "build/reports/render-captures/heightfield-diagnostics.png"
        const val MINIMUM_DIAGNOSTIC_PIXELS = 1_000

        // The correctly wound cube now culls its formerly inward faces, reducing the terrain's
        // visible shadow footprint. Five thousand changed pixels still leaves a wide margin over
        // a no-op toggle while measuring the actual corrected scene rather than its old defect.
        const val MINIMUM_SHADOW_TOGGLE_PIXELS = 5_000
        const val UNSHADOWED_CAPTURE_PATH = "build/reports/render-captures/heightfield-unshadowed.png"
        const val UNCULLED_CAPTURE_PATH = "build/reports/render-captures/heightfield-unculled.png"

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
