/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [World.clear] keeps the same [World] instance but discards its component type registry and its
 * families. A collector that caches type ids or families per world instance must notice, or it
 * keeps iterating a family nobody maintains and resolves stores by ids that now name other types.
 */
class SceneDrawCollectorWorldClearTest {

    @Test
    fun aClearedAndRepopulatedWorldDrawsOnlyItsNewEntities() {
        val world = World()
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val collector = SceneDrawCollector(cullingCompiler)
        val oldMesh = fakeMesh()
        repeat(OLD_COUNT) { spawn(world, oldMesh) }

        val before = collector.collectBeforeParticles(world, cullingCompiler.prepare(world, CAMERA), elapsedTimeSeconds = 0f)
        assertEquals(OLD_COUNT, before.size)

        world.clear()
        val newMesh = fakeMesh()
        repeat(NEW_COUNT) { spawn(world, newMesh) }

        val after = collector.collectBeforeParticles(world, cullingCompiler.prepare(world, CAMERA), elapsedTimeSeconds = 0f)

        assertEquals(NEW_COUNT, after.size, "Every entity spawned after clear() must be drawn, and only those")
        assertTrue(after.all { it.mesh === newMesh }, "No draw may come from an entity removed by clear()")
    }

    @Test
    fun componentsRegisteredInADifferentOrderAfterClearResolveToTheirOwnTypes() {
        val world = World()
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val collector = SceneDrawCollector(cullingCompiler)
        // First population registers MeshBounds before PbrMaterial.
        spawn(world, fakeMesh())
        collector.collectBeforeParticles(world, cullingCompiler.prepare(world, CAMERA), elapsedTimeSeconds = 0f)

        world.clear()
        // Second population registers PbrMaterial first, so it takes the id MeshBounds used to hold.
        val mesh = fakeMesh()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(mesh, fakeMaterial()))
        world.add(entity, PbrMaterial(metallic = 0.25f, roughness = 0.75f))
        world.add(entity, MeshBounds(UNIT_BOX))

        val draws = collector.collectBeforeParticles(world, cullingCompiler.prepare(world, CAMERA), elapsedTimeSeconds = 0f)

        val draw = draws.single()
        assertTrue(draw.mesh === mesh)
        assertContentEquals(
            pbrMaterialFloats(0.25f, 0.75f, Color.White, Color.Transparent),
            draw.extraUniformFloats,
            "The entity's PbrMaterial must be read through PbrMaterial's current type id",
        )
        val bounds = draw.worldBounds
        assertEquals(UNIT_BOX.min, bounds?.min, "World bounds must come from the entity's MeshBounds")
        assertEquals(UNIT_BOX.max, bounds?.max, "World bounds must come from the entity's MeshBounds")
    }

    private fun spawn(world: World, mesh: Mesh) {
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(mesh, fakeMaterial()))
        world.add(entity, MeshBounds(UNIT_BOX))
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val OLD_COUNT = 3
        const val NEW_COUNT = 5
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
        val CAMERA = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
