/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.NavGroundHeight
import com.awakekt.awake.navigation.grid.bakeNavGrid
import com.awakekt.awake.navigation.walkable.NavTestWorlds.FLAT
import com.awakekt.awake.navigation.walkable.NavTestWorlds.clearanceAt
import com.awakekt.awake.navigation.walkable.NavTestWorlds.square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavSurfaceBakeTest {
    private val agent = NavAgent()

    @Test
    fun flatGroundIsStandableEverywhereAndClearanceGrowsAwayFromAnObstacle() {
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = listOf(NavTestWorlds.wall(endZ = 50f))))
        assertEquals(0, clearanceAt(surface, 0f, 0f), "the wall's own sample is blocked")
        val near = clearanceAt(surface, 1f, 0f)
        val far = clearanceAt(surface, 5f, 0f)
        assertEquals(NavClearance.UNITS_PER_SAMPLE, near, "one sample from the wall")
        assertEquals(5 * NavClearance.UNITS_PER_SAMPLE, far, "five samples from the wall")
    }

    @Test
    fun aWallThinnerThanASampleStillBlocks() {
        val sliver = NavObstacle(NavFootprint.Rect(0.4f, -50f, 0.45f, 50f), 0f, 3f)
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = listOf(sliver)))
        assertEquals(0, clearanceAt(surface, 0f, 0f), "the sliver lies in sample 0's cell")
    }

    @Test
    fun terrainSteeperThanTheSlopeLimitIsBlocked() {
        // A 63-degree bank between x = 0 and x = 4.
        val bank = NavGroundHeight { x, _ -> (x * 2f).coerceIn(0f, 8f) }
        val steep = bakeNavSurface(square(), bank, NavAgent(maxSlopeDegrees = 45f))
        val climber = bakeNavSurface(square(), bank, NavAgent(maxSlopeDegrees = 70f))
        assertEquals(0, clearanceAt(steep, 2f, 0f))
        assertTrue(clearanceAt(climber, 2f, 0f) > 0)
        assertTrue(clearanceAt(steep, -5f, 0f) > 0, "level ground below the bank stays walkable")
    }

    @Test
    fun aKerbLowerThanTheStepHeightIsSteppedOver() {
        val kerb = NavObstacle(NavFootprint.Rect(-1f, -50f, 1f, 50f), 0f, 0.2f)
        val areas = NavAreas(obstacles = listOf(kerb))
        val stepper = bakeNavSurface(square(), FLAT, NavAgent(stepHeight = 0.3f), areas)
        val shuffler = bakeNavSurface(square(), FLAT, NavAgent(stepHeight = 0.1f), areas)
        assertTrue(clearanceAt(stepper, 0f, 0f) > 0)
        assertEquals(0, clearanceAt(shuffler, 0f, 0f))
    }

    @Test
    fun anOverhangHigherThanTheAgentIsWalkedUnder() {
        val balcony = NavObstacle(NavFootprint.Rect(-3f, -3f, 3f, 3f), 2.5f, 3f)
        val areas = NavAreas(obstacles = listOf(balcony))
        val person = bakeNavSurface(square(), FLAT, NavAgent(height = 1.8f), areas)
        val giant = bakeNavSurface(square(), FLAT, NavAgent(height = 3f), areas)
        assertTrue(clearanceAt(person, 0f, 0f) > 0)
        assertEquals(0, clearanceAt(giant, 0f, 0f))
    }

    @Test
    fun anObstacleIsTestedAgainstTheGroundUnderIt() {
        // A 1 m block standing on ground that rises to 5 m: on the high ground it is still only 1 m tall.
        val hill = NavGroundHeight { x, _ -> if (x > 10f) 5f else 0f }
        val block = NavObstacle(NavFootprint.Rect(14f, -1f, 16f, 1f), 5f, 6f)
        val surface = bakeNavSurface(square(), hill, NavAgent(maxSlopeDegrees = 89f), NavAreas(obstacles = listOf(block)))
        assertEquals(0, clearanceAt(surface, 15f, 0f))
    }

    @Test
    fun waterDeeperThanTheAgentWadesIsBlocked() {
        val channel = NavGroundHeight { x, _ -> if (x in -3f..3f) -1f else 0f }
        val shallowWader = bakeNavSurface(square(), channel, NavAgent(maxSlopeDegrees = 89f), NavAreas(water = NavWater(0f, 0.5f)))
        val deepWader = bakeNavSurface(square(), channel, NavAgent(maxSlopeDegrees = 89f), NavAreas(water = NavWater(0f, 1.5f)))
        assertEquals(0, clearanceAt(shallowWader, 0f, 0f))
        assertTrue(clearanceAt(deepWader, 0f, 0f) > 0)
        assertTrue(clearanceAt(shallowWader, 10f, 0f) > 0, "dry ground is not water")
    }

    @Test
    fun aNoWalkAreaIsBlockedWhateverIsUnderIt() {
        val keepOut = NavFootprint.Circle(5f, 5f, 2f)
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(noWalk = listOf(keepOut)))
        assertEquals(0, clearanceAt(surface, 5f, 5f))
        assertTrue(clearanceAt(surface, -5f, -5f) > 0)
    }

    @Test
    fun groundWithNoHeightIsBlocked() {
        val island = NavGroundHeight { x, z -> if (x * x + z * z < 100f) 0f else Float.NaN }
        val surface = bakeNavSurface(square(), island, agent)
        assertTrue(clearanceAt(surface, 0f, 0f) > 0)
        assertEquals(0, clearanceAt(surface, 15f, 15f))
    }

    @Test
    fun tilesBakedSeparatelyJoinWithoutASeam() {
        val radius = 1.5f
        val pillar = NavObstacle(NavFootprint.Circle(0.5f, 0f, 1f), 0f, 3f)
        val areas = NavAreas(obstacles = listOf(pillar))
        val whole = bakeNavSurface(NavRegion(-16f, -8f, 32, 16, 1f), FLAT, agent, areas, maxAgentRadius = radius)
        val tiled = NavTiledSurface(tilesX = 2, tilesZ = 1, tileSamples = 16, sampleSize = 1f, originX = -16f, originZ = -8f)
        for (tileX in 0 until 2) tiled.put(tileX, 0, bakeNavSurface(tiled.regionOf(tileX, 0), FLAT, agent, areas, maxAgentRadius = radius))

        val required = NavAgent(radius = radius).requiredClearance(1f)
        for (z in 0 until 16) {
            for (x in 0 until 32) {
                assertEquals(whole.standable(x, z, required), tiled.standable(x, z, required), "sample ($x, $z)")
            }
        }
    }

    @Test
    fun aBuildingBlocksEverySampleItsFootprintTouches() {
        val surface = bakeNavSurface(square(), FLAT, agent, NavAreas(obstacles = listOf(NavTestWorlds.BUILDING)))
        assertEquals(surface.width * surface.depth - 121, surface.walkableCount, "an 11 x 11 sample footprint is blocked")
    }

    @Test
    fun aSlopeBakedGridGainsClearanceWhereItWasWalkable() {
        val heightmap = Heightmap(
            samples = FloatArray(21 * 21) { index -> if (index % 21 == 10 && index / 21 < 15) 5f else 0f },
            width = 21,
            depth = 21,
            scale = Vec3f(1f, 1f, 1f),
        )
        val tile = heightmap.bakeNavGrid()
        val surface = tile.toNavSurface()
        assertEquals(tile.walkableCount, surface.walkableCount)
        for (z in 0 until tile.depth) {
            for (x in 0 until tile.width) assertEquals(tile.isWalkable(x, z), surface.clearance(x, z) > 0, "sample ($x, $z)")
        }
        assertEquals(tile.originX, surface.originX)
    }
}
