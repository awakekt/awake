/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.navigation.grid.findPath
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the *content* of the nav-chase showcase rather than the navigation code, which has its
 * own tests. Terrain heights are tuned by hand, and a wall a metre too short or a target parked on
 * a slope produces a demonstration that runs, renders, and quietly shows nothing.
 */
class NavChaseExampleDriverTest {
    private val tile = NavChaseExampleDriver.navGridTile

    @Test
    fun theRidgeIsActuallyImpassable() {
        for (z in 0..11) {
            assertFalse(tile.isWalkable(8, z), "The ridge at (8, $z) should be too steep to stand on.")
        }
    }

    @Test
    fun theRidgeStopsShortOfTheFarEdge() {
        assertTrue(tile.isWalkable(8, 16), "The gap past the ridge is the only way across.")
    }

    @Test
    fun bothCubesStartOnWalkableGround() {
        assertTrue(tile.isWalkable(3, 14), "The chaser's start position.")
        assertTrue(tile.isWalkable(3, 3), "The target's near extreme.")
        assertTrue(tile.isWalkable(13, 3), "The target's far extreme.")
    }

    @Test
    fun aRouteExistsToBothEndsOfTheTargetSweep() {
        val toNear = tile.findPath(startX = 3, startZ = 14, goalX = 3, goalZ = 3)
        val toFar = tile.findPath(startX = 3, startZ = 14, goalX = 13, goalZ = 3)

        assertTrue(toNear.isNotEmpty(), "No route to the target's near extreme.")
        assertTrue(toFar.isNotEmpty(), "No route to the target's far extreme.")
    }

    /** The demonstration is only worth watching if crossing forces a detour around the ridge. */
    @Test
    fun crossingTheRidgeRequiresGoingAroundIt() {
        val route = tile.findPath(startX = 3, startZ = 14, goalX = 13, goalZ = 3)

        assertTrue(
            route.any { it.z.toInt() > 11 },
            "The route should pass the ridge's far end: ${route.map { it.x.toInt() to it.z.toInt() }}",
        )
    }

    /**
     * The floor has to be under the things standing on it.
     *
     * The scene places its camera and both cubes in the heightmap's own coordinates, so the map's
     * origin convention is not a detail of the mesh -- it decides whether the terrain is beneath
     * the demonstration or half a map away from it. Sampling off the map returns NaN, which is
     * the same answer a missing floor gives.
     */
    @Test
    fun theTerrainIsUnderTheScenesAuthoredPositions() {
        listOf(
            "camera focus" to (8f to 8f),
            "target start" to (3f to 3f),
            "chaser start" to (3f to 14f),
        ).forEach { (what, position) ->
            val height = NavChaseExampleDriver.heightmap.heightAtWorld(position.first, position.second)
            assertTrue(
                !height.isNaN(),
                "No terrain under the $what at ${position.first}, ${position.second}: the scene " +
                    "is authored in this map's coordinates, and the map is somewhere else.",
            )
        }
    }
}
