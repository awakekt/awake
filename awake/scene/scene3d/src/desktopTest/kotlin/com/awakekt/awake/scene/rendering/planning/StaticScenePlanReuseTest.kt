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
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A realtime game's still town is planned once: frames where nothing a draw reads changed hand
 * the last frame back without preparing a draw, and a change anywhere plans the next one in full.
 */
class StaticScenePlanReuseTest {
    private val renderer = NoopRenderer()
    private val world = World()
    private val camera = Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f))
    private val preparer = CountingPreparer()
    private val system = RenderSystem3D(renderer, drawPreparer = preparer)
    private val props = town(3_000)

    @Test
    fun aStillTownPreparesNothingAfterItsFirstFrame() {
        frame()
        assertTrue(preparer.prepared > 0)
        val planned = preparer.prepared

        repeat(10) { frame() }

        assertEquals(planned, preparer.prepared, "ten still frames of 3,000 renderers prepared nothing")
    }

    @Test
    fun aMovedPropOrCameraPlansTheNextFrameAndThenSettles() {
        frame()
        val planned = preparer.prepared

        world.get<Transform>(props[42])!!.position = Vec3f(1f, 0f, 1f)
        frame()
        assertTrue(preparer.prepared > planned, "a moved prop is drawn where it now is")
        val afterMove = preparer.prepared
        frame()
        assertEquals(afterMove, preparer.prepared, "and then the town is still again")

        camera.lens.eye = Vec3f(0f, 31f, 60f)
        frame()
        assertTrue(preparer.prepared > afterMove, "a moved camera plans the frame")
    }

    private val transforms = TransformSystem()

    /** A game's frame: transforms resolved, then the scene planned and drawn. */
    private fun frame() {
        transforms.update(world, DELTA)
        system.update(world, DELTA)
    }

    private fun town(count: Int): List<Entity> {
        world.add(world.create(), camera)
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        val meshes = List(MESHES) { renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor)) }
        val materials = List(MESHES) { renderer.createMaterial() }
        val side = kotlin.math.sqrt(count.toDouble()).toInt() + 1
        return List(count) { index ->
            world.create().also { entity ->
                world.add(entity, Transform(position = Vec3f((index % side - side / 2) * 4f, 0f, (index / side - side / 2) * 4f)))
                world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES]))
                world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
            }
        }
    }

    /** Counts what it prepares; its frames may be drawn again, as WebGPU's may. */
    private class CountingPreparer : GpuDrawPreparer {
        var prepared = 0
        private val handle = object : PipelineHandle, MaterialBinding, BufferHandle {}

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw {
            prepared += 1
            return GpuResolvedDraw(pipeline = handle, materialBinding = handle, vertexBuffer = handle, indexBuffer = null, elementCount = 36)
        }

        override fun canInstance(format: VertexFormat): Boolean = true

        override val redrawsPreparedFrames: Boolean get() = true
    }

    private companion object {
        const val MESHES = 190
        const val DELTA = 1f / 60f
    }
}
