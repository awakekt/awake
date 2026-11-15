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
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import com.awakekt.awake.scene.rendering.spatial.SpatialIndexSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A renderer added without [MeshBounds], as a cell streamer or a game's own code adds one, is
 * culled by the bounds of its mesh (#1621).
 *
 * Measured on main: a portrait looking away from [CELLS] additive transparent cells drew all of
 * them, as the main view did, because an entity with no bounds was never culled.
 */
class UnboundedRendererCullingTest {
    private val bounded = mesh(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)))
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    /** Looks down -z at the cells. */
    private val main = Camera(Lens(eye = Vec3f(0f, 0f, 50f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 500f))

    /** Stands in front of the cells looking the other way, as a HUD portrait faces its subject. */
    private val portrait = Camera(Lens(eye = Vec3f(0f, 0f, 60f), center = Vec3f(0f, 0f, 70f), fovYRadians = 0.5f, near = 0.1f, far = 30f))

    @Test
    fun aViewLookingAwayDrawsNoneOfTheCellsAddedWithoutBounds() {
        val world = World()
        repeat(CELLS) { place(world, x = it * 4f - CELLS * 2f, MeshRenderer(bounded, material, transparent = true, additive = true)) }

        val seen = collect(world, main).size
        val behind = collect(world, portrait).size

        println("Cells added without bounds: main view $seen, a portrait looking away $behind")
        assertEquals(CELLS, seen, "the view facing them draws every cell")
        assertEquals(0, behind, "a view looking away draws none")
    }

    @Test
    fun aMeshThatKnowsNoBoundsIsStillDrawnEverywhere() {
        val world = World()
        place(world, x = 0f, MeshRenderer(mesh(null), material, transparent = true))

        assertEquals(1, collect(world, portrait).size, "nothing says where it is, so nothing culls it")
    }

    @Test
    fun boundsAnEntityAlreadyHasAreKept() {
        val world = World()
        // Authored large enough to reach the portrait, which the mesh's own box does not.
        val entity = place(world, x = 0f, MeshRenderer(bounded, material))
        world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 75f))))

        assertEquals(1, collect(world, portrait).size, "the entity's own bounds decide")
    }

    @Test
    fun anIndexedWorldDrawsACellTheFrameItIsAdded() {
        val world = World()
        val index = SpatialIndexSystem()
        index.update(world, 0f)
        place(world, x = 0f, MeshRenderer(bounded, material))

        index.update(world, 0f)

        assertEquals(1, collect(world, main).size, "bounds come before the index, so it holds the new cell")
    }

    private fun place(world: World, x: Float, renderer: MeshRenderer) = world.create().also { entity ->
        world.add(entity, Transform(position = Vec3f(x, 0f, 0f)))
        world.add(entity, renderer)
    }

    private fun collect(world: World, camera: Camera): List<RenderDrawCommand> {
        TransformSystem().update(world, 0f)
        val compiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = compiler.prepare(world, camera)
        val collector = SceneDrawCollector(compiler)
        return collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f) +
            collector.collectAfterParticles(world, culling, camera)
    }

    private fun mesh(bounds: Aabb?): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override val localBounds = bounds
        override fun destroy() = Unit
    }

    private companion object {
        const val CELLS = 40
    }
}
