/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Extraction ([SceneDrawCollector]) allocates no per-frame garbage in steady state (zero bytes per
 * frame) for plain [MeshRenderer] entities with and without [MeshBounds], billboards, entities
 * with a [PbrMaterial] and a [TextureAnimation], [LodGroup] entities, and modular characters.
 *
 * Skinned entities are not probed.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class SceneExtractionAllocationProbeTest {

    @Test
    fun extractingVisibleEntitiesAllocatesZeroBytesInSteadyState() {
        assertZeroAllocation("Extraction") { world, entity, mesh, material ->
            world.add(entity, MeshRenderer(mesh, material))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
    }

    @Test
    fun extractingVisibleEntitiesWithoutMeshBoundsAllocatesZeroBytesInSteadyState() {
        assertZeroAllocation("Extraction without MeshBounds") { world, entity, mesh, material ->
            world.add(entity, MeshRenderer(mesh, material))
        }
    }

    @Test
    fun extractingBillboardsAllocatesZeroBytesInSteadyState() {
        assertZeroAllocation("Billboard extraction") { world, entity, mesh, material ->
            world.add(entity, MeshRenderer(mesh, material, billboard = true))
        }
    }

    @Test
    fun extractingPbrMaterialsWithTextureAnimationAllocatesZeroBytesInSteadyState() {
        val animation = TextureAnimation(columns = 4, rows = 4, framesPerSecond = 12f)
        assertZeroAllocation("PBR and texture animation extraction") { world, entity, mesh, material ->
            world.add(entity, MeshRenderer(mesh, material))
            world.add(entity, MeshBounds(UNIT_BOX))
            world.add(entity, PbrMaterial(metallic = 0.25f, roughness = 0.75f))
            world.add(entity, animation)
        }
    }

    @Test
    fun extractingTextureAnimationWithoutAMaterialAllocatesZeroBytesInSteadyState() {
        val animation = TextureAnimation(columns = 4, rows = 4, framesPerSecond = 12f)
        assertZeroAllocation("Texture animation without a material extraction") { world, entity, mesh, material ->
            world.add(entity, MeshRenderer(mesh, material))
            world.add(entity, animation)
        }
    }

    @Test
    fun extractingLodGroupsAllocatesZeroBytesInSteadyState() {
        assertZeroAllocation("LodGroup extraction") { world, entity, mesh, material ->
            val levels = listOf(
                LodLevel(mesh, material, maxDistance = 50f),
                LodLevel(mesh, material, maxDistance = 5_000f),
            )
            world.add(entity, LodGroup(levels))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
    }


    @Test
    fun extractingModularCharactersAllocatesZeroBytesInSteadyState() {
        val skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))
        assertZeroAllocation("Modular character extraction", drawsPerEntity = 2) { world, entity, mesh, material ->
            val character = ModularCharacterComponent(skin)
            character.equip("hair", mesh, material)
            character.equip("chest", mesh, material)
            world.add(entity, character)
            world.add(entity, MeshBounds(UNIT_BOX))
        }
    }
    /** Spawns [ENTITY_COUNT] entities through [spawn], warms up, then measures bytes per frame. */
    private fun assertZeroAllocation(
        label: String,
        drawsPerEntity: Int = 1,
        spawn: (World, Entity, Mesh, Material) -> Unit,
    ) {
        val world = World()
        val camera = Camera(
            Lens(
                eye = Vec3f(0f, 0f, 500f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 1000f,
            ),
        )
        val mesh = fakeMesh()
        val material = fakeMaterial()
        repeat(ENTITY_COUNT) { i ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(0f, 0f, (i % 100).toFloat())))
            spawn(world, entity, mesh, material)
        }

        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = cullingCompiler.prepare(world, camera)
        val collector = SceneDrawCollector(cullingCompiler)

        // Warm up the pools and collections
        repeat(WARMUP_FRAMES) {
            val before = collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            val after = collector.collectAfterParticles(world, culling, camera)
            assertEquals(ENTITY_COUNT * drawsPerEntity, before.size + after.size, "$label: every entity must be extracted")
        }

        val start = allocated()
        repeat(MEASURED_FRAMES) {
            collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            collector.collectAfterParticles(world, culling, camera)
        }
        val allocatedBytesPerFrame = (allocated() - start) / MEASURED_FRAMES
        assertEquals(
            0L,
            allocatedBytesPerFrame,
            "$label must allocate 0 bytes per frame in steady state, but allocated $allocatedBytesPerFrame bytes/frame",
        )
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val ENTITY_COUNT = 1000
        const val WARMUP_FRAMES = 100
        const val MEASURED_FRAMES = 200
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
    }
}
