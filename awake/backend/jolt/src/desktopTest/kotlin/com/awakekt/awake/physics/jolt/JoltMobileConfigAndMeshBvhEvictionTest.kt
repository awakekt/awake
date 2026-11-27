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
import com.awakekt.awake.physics.PhysicsWorldConfig
import com.awakekt.awake.physics.ScaledShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies [PhysicsWorldConfig.Mobile] (`TempAllocatorImpl`, compact tables, capped workers),
 * content-based [MeshShape] native BVH deduplication, LRU eviction on body destruction, and
 * `trimMemory()` reclamation on [JoltPhysicsWorld].
 */
class JoltMobileConfigAndMeshBvhEvictionTest {

    @Test
    fun mobileConfigRunsWithFixedTempAllocatorAndBoundedWorkers() {
        val config = PhysicsWorldConfig.Mobile.copy(workerThreads = 2)
        val world = JoltPhysicsWorld(config = config)
        try {
            assertEquals(2, world.workerThreadCount)
            assertEquals(PhysicsWorldConfig.MOBILE_MAX_BODIES, world.activeConfig.maxBodies)

            val floor = triangleAtY(0f)
            world.createBody(floor, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            world.step(1f / 60f)
            val hit = world.raycast(Vec3f(0.25f, 2f, 0.25f), Vec3f(0f, -1f, 0f), 10f)
            assertNotNull(hit, "Mobile fixed-allocator Jolt world must resolve raycasts against MeshShape")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun distinctEqualMeshShapesShareOneNativeBvhAndEvictByLruAndTrim() {
        val config = PhysicsWorldConfig.Mobile.copy(workerThreads = 1, maxCachedMeshShapes = 2)
        val world = JoltPhysicsWorld(config = config)
        try {
            val shapeA1 = triangleAtY(0f)
            val shapeA2 = triangleAtY(0f) // distinct arrays, equal content
            val shapeB = triangleAtY(1f)
            val shapeC = triangleAtY(2f)
            val shapeD = triangleAtY(3f)

            val bodyA1 = world.createBody(shapeA1, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            val bodyA2 = world.createBody(ScaledShape(shapeA2, Vec3f(2f, 2f, 2f)), Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            assertEquals(1, world.cachedMeshShapeCount, "Equal MeshShapes must deduplicate to one native BVH")

            val bodyB = world.createBody(shapeB, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            val bodyC = world.createBody(shapeC, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            val bodyD = world.createBody(shapeD, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC)
            assertEquals(4, world.cachedMeshShapeCount, "Live bodies stay pinned above LRU ceiling")

            // Destroy bodyA1 only: bodyA2 still holds shapeA, so nothing is evictable yet.
            world.destroyBody(bodyA1)
            assertEquals(4, world.cachedMeshShapeCount)

            // Releasing bodyA2, bodyB, and bodyC drops unreferenced BVHs down to maxCachedMeshShapes = 2 (C + live D).
            world.destroyBody(bodyA2)
            assertEquals(3, world.cachedMeshShapeCount, "Evicted oldest idle BVH (shapeA)")
            world.destroyBody(bodyB)
            assertEquals(2, world.cachedMeshShapeCount, "Evicted idle BVH down to cap (2)")
            world.destroyBody(bodyC)
            assertEquals(2, world.cachedMeshShapeCount, "Keeps live shapeD plus 1 warm idle BVH (shapeC)")

            // trimMemory() releases every unreferenced BVH while leaving live bodyD intact and queryable.
            world.trimMemory()
            assertEquals(1, world.cachedMeshShapeCount, "trimMemory() must evict unreferenced BVHs while keeping live bodyD")
            val hit = world.raycast(Vec3f(0.25f, 5f, 0.25f), Vec3f(0f, -1f, 0f), 10f)
            assertNotNull(hit, "Live bodyD must remain intact after trimMemory()")

            world.destroyBody(bodyD)
            world.trimMemory()
            assertEquals(0, world.cachedMeshShapeCount, "All unreferenced BVHs evicted once last body is destroyed")
        } finally {
            world.destroy()
        }
    }

    private fun triangleAtY(y: Float): MeshShape = MeshShape(
        vertices = floatArrayOf(
            0f, y, 0f,
            0f, y, 2f,
            2f, y, 0f,
        ),
        indices = intArrayOf(0, 1, 2),
    )
}
