/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How a behaviour hands its steering to a placement: a direction and a speed, which a placement may pass on rather than move by. */
class AgentSteeringTest {
    @Test
    fun aPlacementThatOnlyMovesIsSteppedTheDistanceTheSpeedCovers() {
        val world = World()
        val agent = world.create()
        val placed = Placed()
        world.add(agent, placed)

        PlacedAgents.steer(world, agent, velocityX = 3f, velocityZ = 4f, delta = 0.5f)

        assertClose(1.5f, placed.position.x)
        assertClose(2f, placed.position.z)
    }

    @Test
    fun aRouteIsSteeredAtTheBehavioursSpeedTowardItsWaypoint() {
        val world = World()
        val agent = world.create()
        world.add(agent, Placed())
        val chase = ChaseBehavior(speed = 3f).apply { path = listOf(Vec3f(3f, 0f, 4f)) }
        val steering = RecordingSteering()

        chase.steer(steering, world, agent, Vec3f(0f, 0f, 0f), delta = 0.1f)

        assertClose(1.8f, steering.velocityX)
        assertClose(2.4f, steering.velocityZ)
        assertEquals(0.1f, steering.delta, "the behaviour's own speed, 3, toward (3, 4), not a distance")
    }

    /** Records what it is asked to steer and moves nothing, as a placement that passes steering on does. */
    private class RecordingSteering : AgentPlacement by PlacedAgents {
        var velocityX = Float.NaN
        var velocityZ = Float.NaN
        var delta = Float.NaN

        override fun steer(world: World, entity: Entity, velocityX: Float, velocityZ: Float, delta: Float) {
            this.velocityX = velocityX
            this.velocityZ = velocityZ
            this.delta = delta
        }
    }

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 1e-5f, "expected $expected, was $actual")
}
