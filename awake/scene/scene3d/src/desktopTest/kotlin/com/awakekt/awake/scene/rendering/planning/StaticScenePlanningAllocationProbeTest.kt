/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.planning

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What planning a still town allocates per frame: [RenderSystem3D] over renderers that share a
 * few hundred meshes, one material each, under a shadow-casting sun, as a consumer's streamed
 * props are. The preparer stands in for a backend's and allocates nothing of its own, so every
 * byte measured is the engine's: extraction, culling, folding, sorting and pass compilation.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class StaticScenePlanningAllocationProbeTest {

    @Test
    fun aStillFrameAllocatesTheSameSmallAmountWhateverTheRendererCount() {
        val small = bytesPerFrame(renderers = 1_000, cameraMoves = false)
        val large = bytesPerFrame(renderers = 3_000, cameraMoves = false)
        println("Still frame: 1,000 renderers $small B/frame, 3,000 renderers $large B/frame")
        assertTrue(large <= STILL_FRAME_BUDGET, "A still frame of 3,000 renderers allocated $large B, over $STILL_FRAME_BUDGET B")
        assertTrue(large - small <= GROWTH_TOLERANCE, "Garbage grew with the renderer count: $small B for 1,000, $large B for 3,000")
    }

    @Test
    fun aCameraOnlyMoveAllocatesNoMoreThanAStillFrame() {
        val moving = bytesPerFrame(renderers = 3_000, cameraMoves = true)
        println("Camera-only move: 3,000 renderers $moving B/frame")
        assertTrue(moving <= STILL_FRAME_BUDGET, "A camera move over 3,000 renderers allocated $moving B, over $STILL_FRAME_BUDGET B")
    }

    /** The median of [WINDOWS] windows, each divided by its frames: a JIT hiccup lands in one window. */
    private fun bytesPerFrame(renderers: Int, cameraMoves: Boolean): Long {
        val renderer = NoopRenderer()
        val world = townOf(renderer, renderers)
        val camera = Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f))
        world.add(world.create(), camera)
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        val system = RenderSystem3D(renderer, drawPreparer = CachingPreparer())
        var frame = 0
        val step = {
            if (cameraMoves) camera.lens.eye = Vec3f(0.01f * (frame % 200), 30f, 60f)
            system.update(world, DELTA)
            frame += 1
        }
        repeat(WARMUP_FRAMES) { step() }
        val windows = LongArray(WINDOWS) {
            val before = threadBean.currentThreadAllocatedBytes
            repeat(FRAMES_PER_WINDOW) { step() }
            (threadBean.currentThreadAllocatedBytes - before) / FRAMES_PER_WINDOW
        }
        return windows.sorted()[WINDOWS / 2]
    }

    /** [count] props on a 4 m grid, cycling through [MESHES] meshes, each with its own material. */
    private fun townOf(renderer: NoopRenderer, count: Int): World {
        val world = World()
        val meshes = List(MESHES) { renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor)) }
        val materials = List(MESHES) { renderer.createMaterial() }
        val side = kotlin.math.sqrt(count.toDouble()).toInt() + 1
        repeat(count) { index ->
            val entity = world.create()
            val x = (index % side - side / 2) * SPACING
            val z = (index / side - side / 2) * SPACING
            world.add(entity, Transform(position = Vec3f(x, 0f, z)))
            world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES]))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
        return world
    }

    /** One resolved draw per mesh, built once: a backend's own allocation is not measured here. */
    private class CachingPreparer : GpuDrawPreparer {
        private val byMesh = IdentityHashMap<Mesh, GpuResolvedDraw>()

        override fun prepare(
            request: com.awakekt.awake.render.command.GpuDrawRequest,
            sourceIndex: Int,
            context: com.awakekt.awake.render.command.GpuDrawPreparationContext,
        ): GpuResolvedDraw = byMesh.getOrPut(request.mesh as Mesh) {
            GpuResolvedDraw(pipeline = Handle, materialBinding = Handle, vertexBuffer = Handle, indexBuffer = null, elementCount = 36)
        }

        override fun canInstance(format: VertexFormat): Boolean = true
    }

    private object Handle : PipelineHandle, MaterialBinding, BufferHandle

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val MESHES = 190
        const val SPACING = 4f
        const val DELTA = 1f / 60f
        const val WARMUP_FRAMES = 60
        const val WINDOWS = 5
        const val FRAMES_PER_WINDOW = 20

        /**
         * A constant set by the frame's output, not its renderers: the pass lists, sort arrays
         * and casters of about 190 folded draws across the main pass and four cascades, about
         * 29.5 KB on the JVM. Main allocated 176 KB a frame for 1,000 renderers and 280 KB for 3,000.
         */
        const val STILL_FRAME_BUDGET = 40_000L
        const val GROWTH_TOLERANCE = 2_048L
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
    }
}
