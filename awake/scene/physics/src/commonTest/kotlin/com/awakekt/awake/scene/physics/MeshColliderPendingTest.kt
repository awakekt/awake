/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** [MeshColliderSystem] visits only colliders still waiting for a body (#1428). */
class MeshColliderPendingTest {
    private class CountingMeshes : CollisionMeshSource {
        var calls = 0
        var loaded = true

        override fun meshShape(mesh: String, primitive: Int?): MeshShape? {
            calls++
            return if (loaded) TRIANGLE else null
        }
    }

    private fun World.collider(): Entity = create().also { entity ->
        add(entity, Transform())
        add(entity, MeshCollider("props/ramp.glb"))
    }

    @Test
    fun aBuiltColliderIsNotVisitedAgain() {
        val world = World()
        repeat(100) { world.collider() }
        val meshes = CountingMeshes()
        val system = MeshColliderSystem(meshes)

        system.update(world, STEP)
        assertEquals(100, meshes.calls)
        assertEquals(100, world.query(PhysicsBody::class).size)

        repeat(10) { system.update(world, STEP) }
        assertEquals(100, meshes.calls, "a step re-checked colliders that already have bodies")
    }

    @Test
    fun aColliderAddedLaterOrWhoseBodyWasRemovedIsBuilt() {
        val world = World()
        val first = world.collider()
        val meshes = CountingMeshes()
        val system = MeshColliderSystem(meshes)
        system.update(world, STEP)

        val second = world.collider()
        world.remove<PhysicsBody>(first)
        system.update(world, STEP)

        assertEquals(3, meshes.calls)
        assertNotNull(world.get<PhysicsBody>(first))
        assertNotNull(world.get<PhysicsBody>(second))
    }

    @Test
    fun anEntityWithBothCollidersGetsOneBodyFromItsMesh() {
        val world = World()
        val entity = world.collider()
        world.add(entity, ConvexHullCollider("props/ramp.glb", motion = MotionType.DYNAMIC))
        val meshes = CountingMeshes()

        MeshColliderSystem(meshes).update(world, STEP)

        assertEquals(1, meshes.calls)
        assertEquals(MotionType.STATIC, world.get<PhysicsBody>(entity)?.motionType)
    }

    @Test
    fun aColliderWithNoTrianglesStaysPendingAndIsBuiltOnceTheyLoad() {
        val world = World()
        val entity = world.collider()
        val meshes = CountingMeshes().apply { loaded = false }
        val system = MeshColliderSystem(meshes)

        assertFailsWith<IllegalStateException> { system.update(world, STEP) }
        assertNull(world.get<PhysicsBody>(entity))

        meshes.loaded = true
        system.update(world, STEP)
        assertNotNull(world.get<PhysicsBody>(entity))
    }

    @Test
    fun aClearedWorldIsReadAfresh() {
        val world = World()
        world.collider()
        val meshes = CountingMeshes()
        val system = MeshColliderSystem(meshes)
        system.update(world, STEP)

        world.clear()
        val entity = world.collider()
        system.update(world, STEP)

        assertNotNull(world.get<PhysicsBody>(entity))
        assertEquals(2, meshes.calls)
    }

    private companion object {
        const val STEP = 1f / 60f
        val TRIANGLE = MeshShape(floatArrayOf(1f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 1f), intArrayOf(0, 1, 2))
    }
}
