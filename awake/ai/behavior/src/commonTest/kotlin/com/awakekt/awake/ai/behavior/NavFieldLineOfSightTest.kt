/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.Perceivable
import com.awakekt.awake.ai.perception.Perceiver
import com.awakekt.awake.ai.perception.PerceptionSettings
import com.awakekt.awake.ai.perception.PerceptionSpace
import com.awakekt.awake.ai.perception.PerceptionSystem
import com.awakekt.awake.ai.perception.SightSettings
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.grid.NavField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavFieldLineOfSightTest {
    // A 1 m grid with a wall in column 4, rows 0 to 4 blocked.
    private val field = object : NavField {
        override val sampleSize = 1f

        override fun isWalkable(x: Int, z: Int) = x in 0..9 && z in 0..9 && !(x == 4 && z <= 4)
    }
    private val sight = NavFieldLineOfSight(field)

    @Test
    fun theWallBlocksAndOpenGroundDoesNot() {
        val viewer = Entity(1L)
        assertFalse(sight.isClear(World(), Vec3f(1f, 1.6f, 2f), Vec3f(8f, 1f, 2f), viewer, null))
        assertTrue(sight.isClear(World(), Vec3f(1f, 1.6f, 7f), Vec3f(8f, 1f, 7f), viewer, null))
        assertTrue(sight.isClear(World(), Vec3f(1f, 0f, 1f), Vec3f(3f, 0f, 3f), viewer, null))
        assertFalse(sight.isClear(World(), Vec3f(1f, 0f, 1f), Vec3f(12f, 0f, 1f), viewer, null))
    }

    @Test
    fun perceptionUsesTheGridAsWalls() {
        val world = World()
        val positions = HashMap<Entity, Vec3f>()
        val space = object : PerceptionSpace {
            override fun position(world: World, entity: Entity, into: Vec3f): Boolean = positions[entity]?.let { into.set(it); true } ?: false

            override fun forward(world: World, entity: Entity, into: Vec3f) = false
        }
        val viewer = world.create().also { positions[it] = Vec3f(1f, 0f, 2f) }
        val hidden = world.create().also { positions[it] = Vec3f(7f, 0f, 2f) }
        val open = world.create().also { positions[it] = Vec3f(2f, 0f, 8f) }
        world.add(viewer, Perceiver(FactionId("a"), PerceptionSettings(sight = SightSettings(peripheralRange = 10f, peripheralAngle = 360f), updateInterval = 0f)))
        world.add(hidden, Perceivable(FactionId("b")))
        world.add(open, Perceivable(FactionId("b")))
        PerceptionSystem(space, lineOfSight = sight).update(world, 0.1f)
        val memory = world.get<Perceiver>(viewer)!!.memory
        assertEquals(listOf(open), memory.entities.map { it.entity })
    }
}
