/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.walkable.NavFollowStatus
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationAgentSystemTest {
    /** 12 by 12 at 1 m; a wall across z = 5 from x = 0 to 8 leaves a gap at x = 9 to 11. */
    private val yard = NavigationGrid(
        SceneNavigation(
            rows = List(12) { z -> if (z == 5) "#########..." else "............" },
        ),
    )

    @Test
    fun anAgentMovedByItsTransformGoesThroughTheGapAndArrives() {
        val world = World()
        val entity = world.create()
        val transform = Transform(position = Vec3f(1f, 0f, 1f))
        world.add(entity, transform)
        world.add(entity, SceneNavigationAgent(radius = 0.3f, speed = 4f, destinationX = 1f, destinationZ = 10f).toAgent())
        val system = NavigationAgentSystem(yard.surface)
        var throughTheGap = false

        repeat(FRAMES) {
            system.update(world, DELTA)
            val position = transform.position
            if (abs(position.z - 5f) < 0.5f) {
                assertTrue(position.x >= 8.5f, "the agent crosses the wall's row only in the gap: $position")
                throughTheGap = true
            }
        }

        assertTrue(throughTheGap)
        assertEquals(NavFollowStatus.Arrived, world.get<NavigationAgent>(entity)!!.follower.status)
        assertTrue(transform.position.horizontalDistanceTo(Vec3f(1f, 0f, 10f)) < 0.5f)
    }

    @Test
    fun anAgentWithAnAgentMovementControlIsSteeredThroughItAndStopsOnArrival() {
        val world = World()
        val entity = world.create()
        val transform = Transform(position = Vec3f(10f, 0f, 1f))
        val control = MovementControl().apply { driver = MovementDriver.Agent }
        world.add(entity, transform)
        world.add(entity, control)
        world.add(entity, SceneNavigationAgent(speed = 5f, destinationX = 10f, destinationZ = 10f).toAgent())
        val system = NavigationAgentSystem(yard.surface)

        system.update(world, DELTA)
        assertEquals(5f, control.moveSpeed, "the agent's speed rides on the control")
        assertTrue(control.moveZ > 0.9f, "a straight run south through the gap: ${control.moveX}, ${control.moveZ}")
        assertEquals(transform.position, Vec3f(10f, 0f, 1f), "the system leaves moving to whatever reads the control")

        // Stand in for the movement system: move by the intent until the agent arrives.
        repeat(FRAMES) {
            transform.position.x += control.moveX * 5f * DELTA
            transform.position.z += control.moveZ * 5f * DELTA
            system.update(world, DELTA)
        }
        assertEquals(0f, control.moveX)
        assertEquals(0f, control.moveZ)
        assertNull(control.moveSpeed, "an arrived agent clears its intent")
    }

    @Test
    fun aPlayerDrivenControlIsNotTouched() {
        val world = World()
        val entity = world.create()
        val control = MovementControl().apply { moveX = 0.5f }
        world.add(entity, Transform(position = Vec3f(1f, 0f, 1f)))
        world.add(entity, control)
        world.add(entity, SceneNavigationAgent(destinationX = 6f, destinationZ = 1f).toAgent())
        NavigationAgentSystem(yard.surface).update(world, DELTA)
        assertEquals(0.5f, control.moveX)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val FRAMES = 60 * 10
    }
}
