/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.ScaledShape
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.math.sin
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measures and guards town-scale static triangle-mesh body creation (`MeshShape` + `ScaledShape`).
 *
 * Large scenes place thousands of static props across a couple hundred unique collision meshes,
 * mostly with non-unit or mirrored transforms. Before world-scoped base-mesh BVH caching and
 * [ScaledShape], every placement cloned vertices and built a fresh Jolt BVH on frame 1 (a multi-
 * second freeze and tens of megabytes of native/JNI churn).
 *
 * Desktop only: `currentThreadAllocatedBytes` has no wasm or Native equivalent, and the budget
 * guards order-of-magnitude regressions without flaking on busy CI hosts.
 */
class JoltMeshCreationBudgetTest {

    @Test
    fun townScaleScaledMeshPlacementsStayUnderTimeAndAllocationBudgets() = runTest {
        val meshes = Array(TOWN_UNIQUE_MESHES) { index -> gridMesh(GRID_CELLS, seed = index) }
        val placements = Array(TOWN_PLACEMENTS) { index ->
            val base = meshes[index % meshes.size]
            if (index % 5 < 2) {
                base
            } else {
                val factor = 0.75f + (index % 17) * 0.125f
                val sx = if (index % 11 == 0) -factor else factor
                ScaledShape(base, Vec3f(sx, 1f + (index % 7) * 0.15f, factor))
            }
        }

        // Warm up JIT and native library paths outside the timed window.
        warmUp(meshes[0])

        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        val allocatedBytes: Long
        val elapsedNanos: Long
        try {
            val bytesBefore = threads.currentThreadAllocatedBytes
            elapsedNanos = measureNanoTime {
                for (index in placements.indices) {
                    world.createBody(
                        shape = placements[index],
                        position = Vec3f((index % 100) * 6f, 0f, (index / 100) * 6f),
                        rotation = Quat.IDENTITY,
                        motionType = MotionType.STATIC,
                    )
                }
            }
            allocatedBytes = threads.currentThreadAllocatedBytes - bytesBefore
        } finally {
            world.destroy()
        }

        val elapsedMs = elapsedNanos / 1_000_000.0
        val bytesPerBody = allocatedBytes / TOWN_PLACEMENTS
        val totalKiB = allocatedBytes / 1024L
        println(
            "JoltMeshCreationBudget: $TOWN_PLACEMENTS bodies ($TOWN_UNIQUE_MESHES unique " +
                "${meshes[0].triangleCount}-tri meshes) built in ${round1(elapsedMs)} ms, " +
                "$totalKiB KiB allocated ($bytesPerBody B/body)",
        )
        assertTrue(
            elapsedMs < TOWN_BUDGET_MILLIS,
            "creating $TOWN_PLACEMENTS cached/scaled mesh bodies took ${round1(elapsedMs)} ms " +
                "(budget ${TOWN_BUDGET_MILLIS} ms)",
        )
        assertTrue(
            bytesPerBody < TOWN_BYTES_PER_BODY_CEILING,
            "creating $TOWN_PLACEMENTS cached/scaled mesh bodies allocated $bytesPerBody B/body " +
                "(ceiling $TOWN_BYTES_PER_BODY_CEILING B/body)",
        )
    }

    @Test
    fun cachedScaledPlacementsBeatUncachedPerPlacementRebuilds() = runTest {
        val baseMeshes = Array(COMPARE_UNIQUE_MESHES) { index -> gridMesh(GRID_CELLS, seed = index + 500) }
        val scales = Array(COMPARE_PLACEMENTS) { index ->
            val factor = 0.8f + (index % 19) * 0.1f
            Vec3f(if (index % 9 == 0) -factor else factor, 1f + (index % 5) * 0.2f, factor)
        }

        warmUp(baseMeshes[0])

        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        // Uncached path reproduces the pre-fix Frame-1 cost: baking each placement's scale into a
        // fresh MeshShape vertex/index copy and building a separate Jolt BVH per placement.
        val (uncachedMs, uncachedBytes) = measureCreateAll(threads, COMPARE_PLACEMENTS) { index ->
            bakedScaledCopy(baseMeshes[index % baseMeshes.size], scales[index])
        }
        val (cachedMs, cachedBytes) = measureCreateAll(threads, COMPARE_PLACEMENTS) { index ->
            ScaledShape(baseMeshes[index % baseMeshes.size], scales[index])
        }
        val speedup = uncachedMs / cachedMs.coerceAtLeast(0.001)
        val allocRatio = uncachedBytes.toDouble() / cachedBytes.coerceAtLeast(1L).toDouble()

        println(
            "JoltMeshCreationCompare ($COMPARE_PLACEMENTS placements / $COMPARE_UNIQUE_MESHES meshes): " +
                "uncached=${round1(uncachedMs)} ms (${uncachedBytes / 1024} KiB) vs " +
                "cached+ScaledShape=${round1(cachedMs)} ms (${cachedBytes / 1024} KiB) -> " +
                "${round1(speedup)}x faster, ${round1(allocRatio)}x less JVM alloc",
        )
        assertTrue(
            speedup >= MIN_SPEEDUP,
            "expected cached ScaledShape placements to be at least ${MIN_SPEEDUP}x faster than " +
                "per-placement BVH builds, got ${round1(speedup)}x " +
                "(uncached=${round1(uncachedMs)} ms, cached=${round1(cachedMs)} ms)",
        )
        assertTrue(
            allocRatio >= MIN_ALLOC_REDUCTION,
            "expected cached ScaledShape placements to allocate at least ${MIN_ALLOC_REDUCTION}x less " +
                "than per-placement builds, got ${round1(allocRatio)}x",
        )
    }

    private suspend fun warmUp(sample: MeshShape) {
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        try {
            repeat(WARMUP_BODIES) { index ->
                val shape = if (index % 2 == 0) sample else ScaledShape(sample, Vec3f(1.5f, 1.2f, 1.5f))
                world.createBody(shape, Vec3f(index * 4f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            }
        } finally {
            world.destroy()
        }
    }

    private suspend fun measureCreateAll(
        threads: ThreadMXBean,
        count: Int,
        shapeAt: (Int) -> com.awakekt.awake.physics.PhysicsShape,
    ): Pair<Double, Long> {
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        try {
            val bytesBefore = threads.currentThreadAllocatedBytes
            val elapsedNanos = measureNanoTime {
                for (index in 0 until count) {
                    world.createBody(
                        shape = shapeAt(index),
                        position = Vec3f((index % 50) * 6f, 0f, (index / 50) * 6f),
                        rotation = Quat.IDENTITY,
                        motionType = MotionType.STATIC,
                    )
                }
            }
            val bytes = threads.currentThreadAllocatedBytes - bytesBefore
            return (elapsedNanos / 1_000_000.0) to bytes
        } finally {
            world.destroy()
        }
    }

    private fun bakedScaledCopy(base: MeshShape, scale: Vec3f): MeshShape {
        val verts = FloatArray(base.vertices.size) { index ->
            base.vertices[index] * when (index % 3) {
                0 -> scale.x
                1 -> scale.y
                else -> scale.z
            }
        }
        val indices = base.indices.copyOf()
        if (scale.x * scale.y * scale.z < 0f) {
            for (t in 0 until indices.size / 3) {
                val tmp = indices[t * 3 + 1]
                indices[t * 3 + 1] = indices[t * 3 + 2]
                indices[t * 3 + 2] = tmp
            }
        }
        return MeshShape(verts, indices)
    }

    private fun gridMesh(cells: Int, seed: Int): MeshShape {
        val stride = cells + 1
        val vertices = FloatArray(stride * stride * 3)
        var v = 0
        for (z in 0..cells) {
            for (x in 0..cells) {
                vertices[v++] = x * 0.5f
                vertices[v++] = sin((x + seed) * 0.4f) * 0.35f + sin((z + seed) * 0.3f) * 0.25f
                vertices[v++] = z * 0.5f
            }
        }
        val indices = IntArray(cells * cells * 6)
        var i = 0
        for (z in 0 until cells) {
            for (x in 0 until cells) {
                val a = z * stride + x
                val b = a + 1
                val c = a + stride
                val d = c + 1
                indices[i++] = a
                indices[i++] = c
                indices[i++] = b
                indices[i++] = b
                indices[i++] = c
                indices[i++] = d
            }
        }
        return MeshShape(vertices, indices)
    }

    private fun round1(value: Double): Double = (value * 10.0).toLong() / 10.0

    private companion object {
        const val GRID_CELLS = 10 // 200 triangles, 121 vertices per unique mesh
        const val WARMUP_BODIES = 20
        const val TOWN_UNIQUE_MESHES = 180
        const val TOWN_PLACEMENTS = 6_000
        const val TOWN_BUDGET_MILLIS = 1_500.0
        const val TOWN_BYTES_PER_BODY_CEILING = 2_048L
        const val COMPARE_UNIQUE_MESHES = 10
        const val COMPARE_PLACEMENTS = 600
        const val MIN_SPEEDUP = 4.0
        const val MIN_ALLOC_REDUCTION = 2.0
    }
}
