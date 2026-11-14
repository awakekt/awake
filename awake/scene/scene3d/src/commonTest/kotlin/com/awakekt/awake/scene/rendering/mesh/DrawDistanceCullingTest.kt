/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

/** A renderer past its own draw distance is culled; one inside it, or with none, is drawn. */
class DrawDistanceCullingTest {
    private val clutter = mesh()
    private val landmark = mesh()
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    @Test
    fun clutterEndsAtItsDistanceWhileALandmarkWithoutOneStillDraws() {
        val world = World()
        // The camera stands at z 100 looking down -z: one pebble 20 away, one 80 away, a tower 80 away.
        place(world, z = 80f, MeshRenderer(clutter, material, maxDrawDistance = 40f))
        place(world, z = 20f, MeshRenderer(clutter, material, maxDrawDistance = 40f))
        place(world, z = 20f, MeshRenderer(landmark, material))

        val drawn = collect(world)

        assertEquals(listOf(clutter, landmark), drawn.map { it.mesh }, "the far pebble is not drawn, not even as a shadow caster")
    }

    @Test
    fun theDistanceIsFromTheEyeToTheEntity() {
        val world = World()
        place(world, z = 61f, MeshRenderer(clutter, material, maxDrawDistance = 40f)) // 39 from the eye
        place(world, z = 59f, MeshRenderer(landmark, material, maxDrawDistance = 40f)) // 41 from the eye

        assertEquals(listOf(clutter), collect(world).map { it.mesh })
    }

    private fun place(world: World, z: Float, renderer: MeshRenderer) {
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(0f, 0f, z)))
        world.add(entity, renderer)
        world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
    }

    private fun collect(world: World): List<com.awakekt.awake.render.passes.RenderDrawCommand> {
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 100f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 1_000f))
        TransformSystem().update(world, 0f)
        val compiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = compiler.prepare(world, camera)
        val collector = SceneDrawCollector(compiler)
        return collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f) + collector.collectAfterParticles(world, culling, camera)
    }

    private fun mesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
}
