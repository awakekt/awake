/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.grid.NavGrid
import com.awakekt.awake.navigation.grid.bakeNavGrid
import com.awakekt.awake.navigation.grid.findPath
import com.awakekt.awake.navigation.grid.smoothPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every sample on the "Navigation" guide is a region here, run against a real baked grid. */
class NavigationDocsSampleTest {

    /** A 21 x 21 m flat map with a 5 m wall along x = 0 that stops 5 m short of the far edge. */
    private val heightmap = Heightmap(
        samples = FloatArray(21 * 21) { index ->
            val x = index % 21
            val z = index / 21
            if (x == 10 && z < 15) 5f else 0f
        },
        width = 21,
        depth = 21,
        scale = Vec3f(1f, 1f, 1f),
    )

    @Test
    fun aPathGoesAroundTheWall() {
        // --8<-- [start:bake]
        val tile = heightmap.bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)
        val path = tile.findPath(start = Vec3f(-5f, 0f, -5f), goal = Vec3f(5f, 0f, -5f))
        val route = tile.smoothPath(path) // drops waypoints the ones around them can see past
        // --8<-- [end:bake]

        assertTrue(path.isNotEmpty(), "a route exists around the wall")
        assertTrue(route.size < path.size)
        assertTrue(route.any { it.z > 4f }, "the route detours past the end of the wall: $route")
        // Waypoints carry X and Z; Y is left at zero.
        assertTrue(route.all { it.y == 0f })
    }

    @Test
    fun aPathRequestIsAnsweredByTheSystem() {
        val world = World()
        val agent = world.create()
        // --8<-- [start:path-request]
        val navMesh = NavGrid(heightmap.bakeNavGrid())
        val paths = PathRequestSystem(navMesh) // pass a CoroutineScope to search off the frame thread

        val request = PathRequest()
        world.add(agent, request)
        request.requestPath(from = Vec3f(-5f, 0f, -5f), to = Vec3f(5f, 0f, -5f))

        paths.update(world, 1f / 30f)
        if (request.status == PathStatus.Ready) {
            println("walk ${request.waypoints}")
        }
        // --8<-- [end:path-request]

        assertEquals(PathStatus.Ready, request.status)
        assertTrue(abs(request.waypoints.last().x - 5f) < 1f)
    }

    @Test
    fun anUnreachableGoalIsReported() {
        val world = World()
        val request = PathRequest()
        world.add(world.create(), request)
        request.requestPath(from = Vec3f(-5f, 0f, -5f), to = Vec3f(50f, 0f, 50f))

        PathRequestSystem(NavGrid(heightmap.bakeNavGrid())).update(world, 1f / 30f)

        assertEquals(PathStatus.Unreachable, request.status)
        assertTrue(request.waypoints.isEmpty())
    }

    @Test
    fun debugLinesMarkTheWall() {
        val tile = heightmap.bakeNavGrid()
        // --8<-- [start:debug-lines]
        val lines = navGridDebugLines(tile, surfaceAt = { x, z -> heightmap.heightAtWorld(x, z) })
        // Hand `lines` to the renderer's debug-line pass with any other debug lines you draw.
        // --8<-- [end:debug-lines]
        assertTrue(lines.isNotEmpty(), "blocked samples are drawn")
    }
}
