/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.NavGroundHeight
import com.awakekt.awake.navigation.walkable.NavTestWorlds.BUILDING
import com.awakekt.awake.navigation.walkable.NavTestWorlds.FLAT
import com.awakekt.awake.navigation.walkable.NavTestWorlds.assertWalkable
import com.awakekt.awake.navigation.walkable.NavTestWorlds.square
import com.awakekt.awake.navigation.walkable.NavTestWorlds.wall
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavPathQueryTest {
    private val agent = NavAgent()

    private fun surfaceWith(vararg obstacles: NavObstacle): NavSurfaceTile =
        bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = obstacles.toList()))

    private fun route(surface: NavSurface, from: Vec3f, to: Vec3f, who: NavAgent = agent): NavPath =
        NavPath().also { NavPathQuery(surface, FLAT).findPath(from, to, who, it) }

    @Test
    fun aPathGoesAroundAWall() {
        val surface = surfaceWith(wall(endZ = 10f))
        val path = route(surface, Vec3f(-10f, 0f, -10f), Vec3f(10f, 0f, -10f))

        assertEquals(NavPathStatus.Complete, path.status)
        assertTrue(path.toList().any { it.z > 10f }, "the route rounds the end of the wall: ${path.toList()}")
        assertEquals(-10f, path.x(0))
        assertEquals(10f, path.x(path.size - 1), "a complete route ends exactly at the target")
        assertWalkable(surface, path, agent)
        assertTrue(path.size <= 4, "smoothing keeps only the corners: ${path.toList()}")
    }

    @Test
    fun aPathGoesAroundABuilding() {
        val surface = surfaceWith(BUILDING)
        val path = route(surface, Vec3f(-10f, 0f, 0f), Vec3f(10f, 0f, 0f))

        assertEquals(NavPathStatus.Complete, path.status)
        assertWalkable(surface, path, agent)
        for (index in 0 until path.size) {
            val inside = abs(path.x(index)) < 5f + agent.radius && abs(path.z(index)) < 5f + agent.radius
            assertTrue(!inside, "waypoint $index (${path.x(index)}, ${path.z(index)}) touches the building")
        }
    }

    @Test
    fun anUnreachableTargetGivesTheNearestReachablePoint() {
        // A closed pen of four walls around (10, 10).
        val pen = listOf(
            NavObstacle(NavFootprint.Rect(5f, 5f, 15f, 6f), 0f, 3f),
            NavObstacle(NavFootprint.Rect(5f, 14f, 15f, 15f), 0f, 3f),
            NavObstacle(NavFootprint.Rect(5f, 5f, 6f, 15f), 0f, 3f),
            NavObstacle(NavFootprint.Rect(14f, 5f, 15f, 15f), 0f, 3f),
        )
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = pen))
        val path = route(surface, Vec3f(-10f, 0f, -10f), Vec3f(10f, 0f, 10f))

        assertEquals(NavPathStatus.Partial, path.status)
        val endX = path.x(path.size - 1)
        val endZ = path.z(path.size - 1)
        // The pen's inside is open ground, so the target is not moved; the nearest samples outside
        // the pen are 6 m from it, one past each wall, and the search settles on the first of them.
        assertEquals(10f, endX)
        assertEquals(4f, endZ, "the route stops just outside the pen's south wall")
        assertWalkable(surface, path, agent)
    }

    @Test
    fun aTargetInsideABuildingWalksToItsWall() {
        val surface = surfaceWith(BUILDING)
        val path = route(surface, Vec3f(-15f, 0f, 0f), Vec3f(-2f, 0f, 0f))

        assertEquals(NavPathStatus.Partial, path.status)
        assertEquals(-6f, path.x(path.size - 1), "the nearest standable sample outside the west wall")
        assertEquals(0f, path.z(path.size - 1))
    }

    @Test
    fun aStartPushedIntoAWallStillFindsItsWayOut() {
        val surface = surfaceWith(wall(endZ = 10f))
        val path = route(surface, Vec3f(0.1f, 0f, -10f), Vec3f(10f, 0f, -10f))
        assertEquals(NavPathStatus.Complete, path.status)
        assertTrue(abs(path.x(0)) >= 1f, "the first waypoint is off the wall: ${path.x(0)}")
    }

    @Test
    fun aStartWithNowhereToStandHasNoPath() {
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(noWalk = listOf(NavFootprint.Circle(0f, 0f, 15f))))
        val path = route(surface, Vec3f(0f, 0f, 0f), Vec3f(18f, 0f, 18f))
        assertEquals(NavPathStatus.NoPath, path.status)
        assertTrue(path.isEmpty)
    }

    @Test
    fun aNarrowAgentFitsThroughAGapAWideOneDoesNot() {
        val walls = listOf(
            NavObstacle(NavFootprint.Rect(-0.25f, -50f, 0.25f, -1f), 0f, 3f),
            NavObstacle(NavFootprint.Rect(-0.25f, 1f, 0.25f, 50f), 0f, 3f),
        )
        val wide = NavAgent(radius = 1f)
        val surface = bakeNavSurface(square(sampleSize = 0.5f), FLAT, agent, NavAreas(obstacles = walls), maxAgentRadius = 1f)
        val from = Vec3f(-5f, 0f, 0f)
        val to = Vec3f(5f, 0f, 0f)

        assertEquals(NavPathStatus.Complete, route(surface, from, to, NavAgent(radius = 0.3f)).status)
        val blocked = route(surface, from, to, wide)
        assertEquals(NavPathStatus.Partial, blocked.status)
        assertTrue(blocked.x(blocked.size - 1) < 0f, "the wide agent stops on its own side")
    }

    @Test
    fun aSlopeTooSteepToClimbIsRoutedAroundOrStopsAtItsFoot() {
        // A 63-degree bank across the whole map between x = 0 and x = 4.
        val bank = NavGroundHeight { x, _ -> (x * 2f).coerceIn(0f, 8f) }
        val hiker = NavAgent(maxSlopeDegrees = 45f)
        val climber = NavAgent(maxSlopeDegrees = 70f)
        val from = Vec3f(-10f, 0f, 0f)
        val to = Vec3f(10f, 8f, 0f)

        val stopped = NavPath().also { NavPathQuery(bakeNavSurface(square(), bank, hiker), bank).findPath(from, to, hiker, it) }
        val climbed = NavPath().also { NavPathQuery(bakeNavSurface(square(), bank, climber), bank).findPath(from, to, climber, it) }
        assertEquals(NavPathStatus.Partial, stopped.status)
        assertTrue(stopped.x(stopped.size - 1) < 0f)
        assertEquals(NavPathStatus.Complete, climbed.status)
        assertEquals(8f, climbed.y(climbed.size - 1), "waypoint heights come from the ground")
    }

    @Test
    fun aKerbWithinTheStepHeightDoesNotDivertTheRoute() {
        val kerb = NavObstacle(NavFootprint.Rect(-1f, -50f, 1f, 50f), 0f, 0.2f)
        val stepper = NavAgent(stepHeight = 0.3f)
        val surface = bakeNavSurface(square(), FLAT, stepper, NavAreas(obstacles = listOf(kerb)))
        val path = route(surface, Vec3f(-10f, 0f, 0f), Vec3f(10f, 0f, 0f), stepper)
        assertEquals(NavPathStatus.Complete, path.status)
        assertEquals(2, path.size, "straight over the kerb")
    }

    @Test
    fun theSameQueryGivesTheSameRouteEveryTime() {
        // A pillar dead centre: going round above or below costs the same, and the tie must break the same way.
        val surface = surfaceWith(NavObstacle(NavFootprint.Circle(0f, 0f, 3f), 0f, 3f))
        val from = Vec3f(-12f, 0f, 0f)
        val to = Vec3f(12f, 0f, 0f)
        val reused = NavPathQuery(surface, FLAT)
        val first = NavPath().also { reused.findPath(from, to, agent, it) }
        repeat(5) {
            val again = NavPath().also { reused.findPath(from, to, agent, it) }
            val fresh = route(surface, from, to)
            assertEquals(first.toList(), again.toList())
            assertEquals(first.toList(), fresh.toList())
        }
    }

    @Test
    fun theNodeBudgetBoundsTheSearchAndStillMakesProgress() {
        val surface = surfaceWith(wall(endZ = 15f))
        val from = Vec3f(-15f, 0f, -15f)
        val to = Vec3f(15f, 0f, -15f)
        val query = NavPathQuery(surface, FLAT, maxExpandedNodes = 60)
        val path = NavPath()
        query.findPath(from, to, agent, path)

        assertEquals(NavPathStatus.Partial, path.status)
        assertTrue(query.lastExpandedNodes <= 60)
        val endX = path.x(path.size - 1)
        val endZ = path.z(path.size - 1)
        val remaining = sqrt((to.x - endX) * (to.x - endX) + (to.z - endZ) * (to.z - endZ))
        assertTrue(remaining < 30f, "the partial route ends nearer the target than it started: ($endX, $endZ)")
    }

    @Test
    fun aRouteCrossesTilesAndStopsAtAnUnloadedOne() {
        val tiled = NavTiledSurface(tilesX = 3, tilesZ = 1, tileSamples = 16, sampleSize = 1f, originX = -24f, originZ = -8f)
        for (tileX in 0 until 2) tiled.put(tileX, 0, bakeNavSurface(tiled.regionOf(tileX, 0), FLAT, agent))
        val across = route(tiled, Vec3f(-20f, 0f, 0f), Vec3f(4f, 0f, 0f))
        val beyond = route(tiled, Vec3f(-20f, 0f, 0f), Vec3f(20f, 0f, 0f))

        assertEquals(NavPathStatus.Complete, across.status)
        assertEquals(NavPathStatus.Partial, beyond.status)
        assertTrue(beyond.x(beyond.size - 1) < 8f, "the route stops inside the loaded tiles")
    }

    @Test
    fun theNavMeshAdapterAnswersWithAList() {
        val mesh = NavSurfaceMesh(surfaceWith(wall(endZ = 10f)), agent)
        val waypoints = mesh.findPath(Vec3f(-10f, 0f, -10f), Vec3f(10f, 0f, -10f))
        assertTrue(waypoints.size >= 3)
        assertEquals(Vec3f(10f, 0f, -10f), waypoints.last())
    }
}
