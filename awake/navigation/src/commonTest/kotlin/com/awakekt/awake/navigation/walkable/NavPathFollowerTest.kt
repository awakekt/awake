/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.walkable.NavTestWorlds.FLAT
import com.awakekt.awake.navigation.walkable.NavTestWorlds.square
import com.awakekt.awake.navigation.walkable.NavTestWorlds.wall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavPathFollowerTest {
    private val agent = NavAgent()
    private val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = listOf(wall(endZ = 10f))))
    private val query = NavPathQuery(surface, FLAT)
    private val direction = Vec3f(0f, 0f, 0f)

    /** Walks [position] kinematically at [speed] for up to [seconds], stopping when the follower does. */
    private fun walk(follower: NavPathFollower, position: Vec3f, seconds: Float, speed: Float = 4f) {
        var time = 0f
        while (time < seconds && follower.update(query, position, STEP, direction)) {
            position.x += direction.x * speed * STEP
            position.z += direction.z * speed * STEP
            time += STEP
        }
    }

    @Test
    fun anAgentWalksAroundTheWallAndArrives() {
        // No interval re-plans, so any second plan would be a false "stuck".
        val follower = NavPathFollower(agent).apply { repathInterval = 0f }
        val position = Vec3f(-10f, 0f, -10f)
        follower.moveTo(Vec3f(10f, 0f, -10f))
        var furthestZ = position.z
        var time = 0f
        while (time < 30f && follower.update(query, position, STEP, direction)) {
            position.x += direction.x * 4f * STEP
            position.z += direction.z * 4f * STEP
            furthestZ = maxOf(furthestZ, position.z)
            assertTrue(clearanceAt(position) > 0, "the agent never stands on the wall: $position")
            time += STEP
        }

        assertEquals(NavFollowStatus.Arrived, follower.status)
        assertTrue(furthestZ > 10f, "it went round the end of the wall")
        assertTrue(position.horizontalDistanceTo(Vec3f(10f, 0f, -10f)) <= follower.arriveDistance)
        assertEquals(1, follower.plans, "one plan for a walk nothing disturbed")
    }

    @Test
    fun anUnreachableDestinationStopsAtTheNearestPoint() {
        val walledIn = bakeNavSurface(
            square(),
            FLAT,
            agent,
            NavAreas(obstacles = listOf(NavObstacle(NavFootprint.Rect(4f, -50f, 5f, 50f), 0f, 3f))),
        )
        val follower = NavPathFollower(agent)
        val position = Vec3f(-10f, 0f, 0f)
        follower.moveTo(Vec3f(10f, 0f, 0f))
        var time = 0f
        while (time < 20f && follower.update(NavPathQuery(walledIn, FLAT), position, STEP, direction)) {
            position.x += direction.x * 4f * STEP
            position.z += direction.z * 4f * STEP
            time += STEP
        }

        assertEquals(NavFollowStatus.Unreachable, follower.status)
        assertTrue(position.x in 2f..4f, "it waits at the wall, at ${position.x}")
    }

    @Test
    fun anAgentHeldInPlaceRePlans() {
        val follower = NavPathFollower(agent).apply { repathInterval = 0f }
        val position = Vec3f(-10f, 0f, -10f)
        follower.moveTo(Vec3f(10f, 0f, -10f))
        // Something the surface does not know about holds the agent still for two seconds.
        var time = 0f
        while (time < 2f) {
            follower.update(query, position, STEP, direction)
            time += STEP
        }
        assertTrue(follower.plans >= 2, "no progress for stuckSeconds re-plans; planned ${follower.plans} times")

        walk(follower, position, seconds = 30f)
        assertEquals(NavFollowStatus.Arrived, follower.status, "and still gets there once released")
    }

    @Test
    fun anAgentPushedOffTheRouteFindsItsWayFromWhereItIs() {
        val follower = NavPathFollower(agent).apply { repathInterval = 0.5f }
        val position = Vec3f(-10f, 0f, -10f)
        follower.moveTo(Vec3f(10f, 0f, -10f))
        walk(follower, position, seconds = 1f)
        // Knocked far back down the map.
        position.set(-15f, 0f, -18f)
        walk(follower, position, seconds = 30f)
        assertEquals(NavFollowStatus.Arrived, follower.status)
        assertTrue(follower.plans >= 2)
    }

    @Test
    fun stopDropsTheRoute() {
        val follower = NavPathFollower(agent)
        follower.moveTo(Vec3f(10f, 0f, -10f))
        assertTrue(follower.update(query, Vec3f(-10f, 0f, -10f), STEP, direction))
        follower.stop()
        assertEquals(false, follower.update(query, Vec3f(-10f, 0f, -10f), STEP, direction))
        assertEquals(NavFollowStatus.Idle, follower.status)
        assertTrue(follower.path.isEmpty)
        assertEquals(Vec3f(0f, 0f, 0f), direction)
    }

    private fun clearanceAt(position: Vec3f): Int = NavTestWorlds.clearanceAt(surface, position.x, position.z)

    private companion object {
        const val STEP = 1f / 60f
    }
}
