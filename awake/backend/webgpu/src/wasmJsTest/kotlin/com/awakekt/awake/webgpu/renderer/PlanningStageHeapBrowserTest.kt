/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * What [RenderSystem3D]'s own collect and compile stages allocate on wasm, a frame at a time, with a
 * still camera and with a moving one (#1621).
 *
 * A consumer replans every frame by default: `isRealtimeProvider` defaults to true, so the reuse of
 * a still frame's plan only applies to a host that opts out. A still camera therefore still pays
 * for collect and compile, which a consumer's profile put at 868 of 1,660 KB a frame with nearly
 * every draw packet reused. The scene is the JVM probe's town (`StaticScenePlanningAllocationProbeTest`):
 * [PROPS] renderers sharing [MESHES] meshes under a shadow-casting sun.
 *
 * Each figure is the median, over [MEASURED] frames, of the JS heap's growth across the stage from
 * a heap collected before the frame.
 */
class PlanningStageHeapBrowserTest {

    /** The heap at each stage's edges; reads only, so the stage is all it measures. */
    private class StageHeap : RenderDrawStages {
        var collect = 0.0
        var compile = 0.0
        private var start = 0.0

        override fun begin(stage: RenderDrawStage) {
            start = heapNow()
        }

        override fun end(stage: RenderDrawStage) {
            val grew = heapNow() - start
            when (stage) {
                RenderDrawStage.Collect -> collect = grew
                RenderDrawStage.Compile -> compile = grew
                else -> Unit
            }
        }
    }

    @Test
    fun collectAndCompileAreMeasuredWithAStillAndAMovingCamera() = runTest(timeout = 4.minutes) {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val engine = BundleBrowserEngine(1)
        try {
            val renderer = engine.boot()
            val still = measure(renderer, cameraMoves = false)
            val moving = measure(renderer, cameraMoves = true)
            println(
                "PlanningStageHeap: still collect ${still.first.toInt()} KB, compile ${still.second.toInt()} KB; " +
                    "moving collect ${moving.first.toInt()} KB, compile ${moving.second.toInt()} KB",
            )
            assertTrue(engine.errors.isEmpty(), "WebGPU validation errors: ${engine.errors}")
            assertTrue(still.first + still.second <= STILL_BUDGET_KB, "a still frame planned ${still.first + still.second} KB, over $STILL_BUDGET_KB KB")
            assertTrue(moving.first + moving.second <= MOVING_BUDGET_KB, "a moving frame planned ${moving.first + moving.second} KB, over $MOVING_BUDGET_KB KB")
        } finally {
            engine.destroy()
        }
    }

    /** The median collect and compile growth, in KB, of [MEASURED] warm frames. */
    private fun measure(renderer: Renderer, cameraMoves: Boolean): Pair<Double, Double> {
        val world = town(renderer)
        val camera = Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f))
        world.add(world.create(), camera)
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        val heap = StageHeap()
        val system = RenderSystem3D(Offscreen(renderer), drawPreparer = renderer.gpuDrawPreparer).apply { drawStages = heap }
        var frame = 0
        fun step() {
            if (cameraMoves) camera.lens.eye = Vec3f(0.05f * (frame % 200), 30f, 60f)
            system.update(world, DELTA)
            frame += 1
        }
        repeat(WARM_UP) { step() }
        val collect = DoubleArray(MEASURED)
        val compile = DoubleArray(MEASURED)
        for (i in 0 until MEASURED) {
            collectedHeap()
            step()
            collect[i] = heap.collect
            compile[i] = heap.compile
        }
        return median(collect) / KB to median(compile) / KB
    }

    /**
     * [renderer], drawing each frame into an offscreen target: the test page's canvas is never
     * configured for presenting, and only the planning before the draw is measured.
     */
    private class Offscreen(private val renderer: Renderer) : RenderRenderer by renderer {
        private val target = renderer.createRenderTarget(BundleBrowserEngine.SIZE, BundleBrowserEngine.SIZE)

        override fun draw(input: GpuPassInput) = renderer.renderToTexture(target, input)
    }

    /** The JVM probe's town: [PROPS] props on a 4 m grid, cycling through [MESHES] meshes and materials. */
    private fun town(renderer: Renderer): World {
        val world = World()
        val meshes = List(MESHES) { renderer.createMesh(MovingField.box(0.5f + it * 0.002f)) }
        val materials = List(MESHES) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val side = kotlin.math.sqrt(PROPS.toDouble()).toInt() + 1
        repeat(PROPS) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f((index % side - side / 2) * SPACING, 0f, (index / side - side / 2) * SPACING)))
            world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES]))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
        return world
    }

    private fun median(values: DoubleArray): Double = values.sortedArray()[values.size / 2]

    private companion object {
        const val PROPS = 3_000
        const val MESHES = 190
        const val SPACING = 4f
        const val DELTA = 1f / 60f
        const val WARM_UP = 30
        const val MEASURED = 15
        const val KB = 1024.0

        /** Placeholders until the first browser figures: the consumer's own was 868 KB. */
        const val STILL_BUDGET_KB = 2_048.0
        const val MOVING_BUDGET_KB = 2_048.0
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
    }
}
