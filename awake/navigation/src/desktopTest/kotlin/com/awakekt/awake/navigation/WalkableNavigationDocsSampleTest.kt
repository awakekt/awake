/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.NavGroundHeight
import com.awakekt.awake.navigation.walkable.NavAgent
import com.awakekt.awake.navigation.walkable.NavAreas
import com.awakekt.awake.navigation.walkable.NavFootprint
import com.awakekt.awake.navigation.walkable.NavObstacle
import com.awakekt.awake.navigation.walkable.NavPath
import com.awakekt.awake.navigation.walkable.NavPathQuery
import com.awakekt.awake.navigation.walkable.NavPathStatus
import com.awakekt.awake.navigation.walkable.NavRegion
import com.awakekt.awake.navigation.walkable.NavSurfaceCodec
import com.awakekt.awake.navigation.walkable.NavTiledSurface
import com.awakekt.awake.navigation.walkable.NavWater
import com.awakekt.awake.navigation.walkable.bakeNavSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The walkable-surface samples on the "Navigation" guide, run against real bakes. */
class WalkableNavigationDocsSampleTest {
    /** A 64 x 64 m gentle slope, corner-anchored at the origin. */
    private val heightmap = Heightmap(
        samples = FloatArray(65 * 65) { index -> (index % 65) * 0.05f },
        width = 65,
        depth = 65,
        scale = Vec3f(1f, 1f, 1f),
        origin = GridOrigin.Corner,
    )

    @Test
    fun aRouteGoesAroundAHouseAndThePond() {
        // --8<-- [start:bake-surface]
        val agent = NavAgent(radius = 0.4f, height = 1.8f, stepHeight = 0.3f, maxSlopeDegrees = 40f)
        val areas = NavAreas(
            obstacles = listOf(
                // A house: its world bounds, from the ground to the roof.
                NavObstacle(NavFootprint.Rect(20f, 20f, 30f, 32f), minY = 0f, maxY = 6f),
                // A well: an upright cylinder.
                NavObstacle(NavFootprint.Circle(40f, 12f, 1.2f), minY = 0f, maxY = 1f),
            ),
            noWalk = listOf(NavFootprint.Rect(50f, 50f, 60f, 60f)), // a fenced field
            water = NavWater(surfaceY = -1f, maxDepth = 0.5f),
        )
        val ground = NavGroundHeight(heightmap::heightAtWorld) // NaN off the map reads as blocked
        val surface = bakeNavSurface(NavRegion(0f, 0f, 129, 129, 0.5f), ground, agent, areas)
        // --8<-- [end:bake-surface]

        // --8<-- [start:query]
        val query = NavPathQuery(surface, ground) // keep one per thread and reuse it
        val path = NavPath() // the agent owns its path and reuses it too
        when (query.findPath(Vec3f(10f, 0f, 26f), Vec3f(40f, 0f, 26f), agent, path)) {
            NavPathStatus.Complete -> Unit // path ends at the target
            NavPathStatus.Partial -> Unit // path ends at the reachable point nearest the target
            NavPathStatus.NoPath, NavPathStatus.None -> Unit // nowhere to stand at the start
        }
        val next = path.get(1, Vec3f()) // read waypoints without allocating
        // --8<-- [end:query]

        assertEquals(NavPathStatus.Complete, path.status)
        assertTrue(next.z < 20f || next.z > 32f, "the route rounds the house: ${path.toList()}")
    }

    @Test
    fun aLargeRegionIsTiledAndCached() {
        val agent = NavAgent()
        val ground = NavGroundHeight(heightmap::heightAtWorld)
        // --8<-- [start:tiles]
        val world = NavTiledSurface(tilesX = 4, tilesZ = 4, tileSamples = 32, sampleSize = 0.5f)
        for (tileZ in 0 until 4) {
            for (tileX in 0 until 4) {
                val tile = bakeNavSurface(world.regionOf(tileX, tileZ), ground, agent, maxAgentRadius = 1f)
                val bytes = NavSurfaceCodec.encode(tile) // cache these; decode instead of re-baking
                world.put(tileX, tileZ, NavSurfaceCodec.decode(bytes))
            }
        }
        // --8<-- [end:tiles]

        val path = NavPath()
        NavPathQuery(world, ground).findPath(Vec3f(2f, 0f, 2f), Vec3f(60f, 0f, 60f), agent, path)
        assertEquals(NavPathStatus.Complete, path.status)
        assertEquals(2, path.size, "open ground is one straight leg")
    }
}
