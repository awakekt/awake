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
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Extraction ([SceneDrawCollector]) extracts visible entities without allocating per-frame garbage
 * in steady state (zero bytes allocated per frame).
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class SceneExtractionAllocationProbeTest {

    @Test
    fun extractingVisibleEntitiesAllocatesZeroBytesInSteadyState() {
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
            world.add(entity, MeshRenderer(mesh, material))
            world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        }

        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = cullingCompiler.prepare(world, camera)
        val collector = SceneDrawCollector(cullingCompiler)

        // Warm up the pools and collections
        repeat(WARMUP_FRAMES) {
            val draws = collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            collector.collectAfterParticles(world, culling, camera)
            assertEquals(ENTITY_COUNT, draws.size, "All entities must be extracted")
        }

        // Measure steady state allocation
        val before = allocated()
        repeat(MEASURED_FRAMES) {
            collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            collector.collectAfterParticles(world, culling, camera)
        }
        val after = allocated()

        val allocatedBytesPerFrame = (after - before) / MEASURED_FRAMES
        assertEquals(
            0L,
            allocatedBytesPerFrame,
            "Extraction must allocate 0 bytes per frame in steady state, but allocated $allocatedBytesPerFrame bytes/frame",
        )
    }

    @Test
    fun extractingVisibleEntitiesWithoutMeshBoundsAllocatesZeroBytesInSteadyState() {
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
            world.add(entity, MeshRenderer(mesh, material))
        }

        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = cullingCompiler.prepare(world, camera)
        val collector = SceneDrawCollector(cullingCompiler)

        // Warm up
        repeat(WARMUP_FRAMES) {
            val draws = collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            collector.collectAfterParticles(world, culling, camera)
            assertEquals(ENTITY_COUNT, draws.size)
        }

        val before = allocated()
        repeat(MEASURED_FRAMES) {
            collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
            collector.collectAfterParticles(world, culling, camera)
        }
        val after = allocated()

        val allocatedBytesPerFrame = (after - before) / MEASURED_FRAMES
        assertEquals(
            0L,
            allocatedBytesPerFrame,
            "Extraction without MeshBounds must allocate 0 bytes per frame in steady state, but allocated $allocatedBytesPerFrame bytes/frame",
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
    }
}
