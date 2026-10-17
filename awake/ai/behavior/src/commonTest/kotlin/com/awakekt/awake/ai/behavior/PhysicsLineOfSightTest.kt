/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.RaycastHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicsLineOfSightTest {
    /** Bodies as slabs across the x axis: each blocks a ray along +X at its x, for |z| <= its half width. */
    private class Slab(val handle: BodyHandle, val x: Float, val halfWidth: Float = 10f)

    private class SlabWorld(private val slabs: List<Slab>) : RayCaster {
        var casts = 0

        override fun cast(origin: Vec3f, direction: Vec3f, maxDistance: Float): RaycastHit? {
            casts++
            return slabs
                .filter { direction.x > 0f && it.x >= origin.x && kotlin.math.abs(origin.z) <= it.halfWidth }
                .map { RaycastHit(it.handle, Vec3f(it.x, origin.y, origin.z), (it.x - origin.x) / direction.x) }
                .filter { it.distance <= maxDistance }
                .minByOrNull { it.distance }
        }
    }

    private val viewer = Entity(1L)
    private val target = Entity(2L)
    private val ownBody = BodyHandle(10L)
    private val targetBody = BodyHandle(20L)
    private val wall = BodyHandle(30L)
    private val bodies = mapOf(viewer to ownBody, target to targetBody)

    private fun sight(vararg slabs: Slab): Pair<PhysicsLineOfSight, SlabWorld> {
        val world = SlabWorld(slabs.toList())
        return PhysicsLineOfSight(world, bodyOf = { _, entity -> bodies[entity] }) to world
    }

    private val from = Vec3f(0f, 1.6f, 0f)
    private val to = Vec3f(10f, 1f, 0f)

    @Test
    fun aWallBetweenBlocksAndOpenGroundDoesNot() {
        assertTrue(sight().first.isClear(World(), from, to, viewer, target))
        assertFalse(sight(Slab(wall, 5f)).first.isClear(World(), from, to, viewer, target))
        assertTrue(sight(Slab(wall, 12f)).first.isClear(World(), from, to, viewer, target), "a wall past the target")
    }

    @Test
    fun theViewersOwnBodyIsSeenPast() {
        val (line, world) = sight(Slab(ownBody, 0.2f))
        assertTrue(line.isClear(World(), from, to, viewer, target))
        assertEquals(2, world.casts, "one cast hits the viewer's own body, the next goes past it")
        assertFalse(sight(Slab(ownBody, 0.2f), Slab(wall, 4f)).first.isClear(World(), from, to, viewer, target))
    }

    @Test
    fun theTargetsOwnBodyCountsAsReachingIt() {
        assertTrue(sight(Slab(targetBody, 9.5f)).first.isClear(World(), from, to, viewer, target))
        assertFalse(sight(Slab(targetBody, 9.5f)).first.isClear(World(), from, to, viewer, null), "an unknown target has no body of its own")
    }
}
