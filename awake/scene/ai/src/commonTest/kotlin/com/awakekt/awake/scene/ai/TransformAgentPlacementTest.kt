/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransformAgentPlacementTest {
    @Test
    fun anAgentIsWhereItsTransformSays() {
        val world = World()
        val agent = world.create()
        world.add(agent, Transform(position = Vec3f(1f, 2f, 3f)))
        val into = Vec3f(0f, 0f, 0f)

        assertTrue(TransformAgentPlacement.position(world, agent, into))
        assertEquals(Vec3f(1f, 2f, 3f), into)
    }

    /** Waypoints carry no height, so a step moves the agent on the ground plane and leaves its height alone. */
    @Test
    fun aStepMovesTheTransformOnTheGroundPlane() {
        val world = World()
        val agent = world.create()
        val transform = Transform(position = Vec3f(1f, 2f, 3f))
        world.add(agent, transform)

        TransformAgentPlacement.moveBy(world, agent, 0.5f, -1f)

        assertEquals(Vec3f(1.5f, 2f, 2f), transform.position)
    }

    @Test
    fun anEntityWithNoTransformHasNoPlaceAndDoesNotMove() {
        val world = World()
        val ghost = world.create()
        val into = Vec3f(9f, 9f, 9f)

        assertFalse(TransformAgentPlacement.position(world, ghost, into))
        assertEquals(Vec3f(9f, 9f, 9f), into, "a miss leaves the output alone")
        TransformAgentPlacement.moveBy(world, ghost, 1f, 1f)
    }
}
