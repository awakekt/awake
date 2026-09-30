/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.docs

import com.awakekt.awake.ecs.EcsTag
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// --8<-- [start:components]
data class Position(var x: Float = 0f, var y: Float = 0f)

data class Velocity(var dx: Float = 0f, var dy: Float = 0f)

data object Frozen : EcsTag
// --8<-- [end:components]

// --8<-- [start:system]
class MovementSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach<Position, Velocity> { _, position, velocity ->
            position.x += velocity.dx * delta
            position.y += velocity.dy * delta
        }
    }
}
// --8<-- [end:system]

// --8<-- [start:family-system]
class FrozenAwareMovementSystem(world: World) : System {
    // Built once and kept up to date by the world, so update() does no matching.
    private val movers = world.family {
        all(Position::class, Velocity::class)
        exclude(Frozen::class)
    }

    override fun update(world: World, delta: Float) {
        movers.forEach { entity ->
            val position = world.get<Position>(entity) ?: return@forEach
            val velocity = world.get<Velocity>(entity) ?: return@forEach
            position.x += velocity.dx * delta
        }
    }
}
// --8<-- [end:family-system]

/** The "ECS" guide includes every sample from here, so they keep compiling and doing what it says. */
class EcsDocsSampleTest {

    @Test
    fun entitiesCarryComponentsAndDestroyedHandlesStayDead() {
        // --8<-- [start:entities]
        val world = World()
        val player = world.create()
        world.add(player, Position(0f, 0f))
        world.add(player, Velocity(dx = 2f))

        val position = world.get<Position>(player) // null when absent
        val moving = world.has<Velocity>(player)
        world.remove<Velocity>(player)

        world.destroy(player)
        val alive = world.isAlive(player) // false: this handle never comes back
        // --8<-- [end:entities]

        assertEquals(Position(0f, 0f), position)
        assertTrue(moving)
        assertFalse(alive)
        assertNull(world.get<Position>(player))
    }

    @Test
    fun aSystemMovesEveryEntityWithBothComponents() {
        // --8<-- [start:run-system]
        val world = World()
        val ball = world.create()
        world.add(ball, Position())
        world.add(ball, Velocity(dx = 1f, dy = 2f))

        val movement = MovementSystem()
        movement.update(world, 0.5f)
        // --8<-- [end:run-system]

        assertEquals(Position(0.5f, 1f), world.get<Position>(ball))
    }

    @Test
    fun aFamilyExcludesTaggedEntitiesAndTracksChanges() {
        val world = World()
        val system = FrozenAwareMovementSystem(world)
        val moving = world.create().also {
            world.add(it, Position())
            world.add(it, Velocity(dx = 1f))
        }
        val frozen = world.create().also {
            world.add(it, Position())
            world.add(it, Velocity(dx = 1f))
            world.add(it, Frozen)
        }

        system.update(world, 1f)
        assertEquals(1f, world.get<Position>(moving)?.x)
        assertEquals(0f, world.get<Position>(frozen)?.x)

        world.remove<Frozen>(frozen)
        system.update(world, 1f)
        assertEquals(1f, world.get<Position>(frozen)?.x)
    }
}
