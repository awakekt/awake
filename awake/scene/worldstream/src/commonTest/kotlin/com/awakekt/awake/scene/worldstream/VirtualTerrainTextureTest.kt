/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.asset.terrain.splat.TerrainSplatWeightMap
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.world.WorldCellCoord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VirtualTerrainTextureTest {

    private fun createDummySplat(width: Int = 4, height: Int = 4): TerrainSplatWeightMap {
        val bytes = ByteArray(width * height * 4) { i ->
            if (i % 4 == 0) 255.toByte() else 0
        }
        return TerrainSplatWeightMap(width, height, bytes)
    }

    @Test
    fun tileCacheLruEviction() {
        val evictedList = mutableListOf<WorldCellCoord>()
        val cache = VirtualTerrainTileCache(capacity = 3) { evicted ->
            evictedList.add(evicted.coord)
        }

        val tileA = VirtualTerrainTile(WorldCellCoord(0, 0), createDummySplat())
        val tileB = VirtualTerrainTile(WorldCellCoord(1, 0), createDummySplat())
        val tileC = VirtualTerrainTile(WorldCellCoord(2, 0), createDummySplat())
        val tileD = VirtualTerrainTile(WorldCellCoord(3, 0), createDummySplat())

        cache.put(tileA)
        cache.put(tileB)
        cache.put(tileC)
        assertEquals(3, cache.size)
        assertEquals(0, cache.evictionCount)

        // Access A, making B the LRU element
        assertNotNull(cache.get(tileA.coord))
        assertEquals(1, cache.hitCount)

        // Insert D -> B should be evicted
        cache.put(tileD)
        assertEquals(3, cache.size)
        assertEquals(1, cache.evictionCount)
        assertEquals(listOf(WorldCellCoord(1, 0)), evictedList)

        assertTrue(cache.contains(tileA.coord))
        assertFalse(cache.contains(tileB.coord))
        assertTrue(cache.contains(tileC.coord))
        assertTrue(cache.contains(tileD.coord))

        // Miss check
        assertNull(cache.get(WorldCellCoord(99, 99)))
        assertEquals(1, cache.missCount)
    }

    @Test
    fun streamListenerLoadsAndPrefetchesSurroundingCells() = runTest {
        val cache = VirtualTerrainTileCache(capacity = 32)
        val provider = VirtualTerrainTileProvider { coord ->
            VirtualTerrainTile(
                coord = coord,
                splatWeightMap = createDummySplat(),
                diffuseBytes = byteArrayOf(1, 2, 3),
            )
        }
        val listener = VirtualTerrainCellStreamListener(
            cache = cache,
            provider = provider,
            prefetchRadius = 1,
        )

        val targetCoord = WorldCellCoord(10, 10)
        val content = listener.loadCell(targetCoord)

        // Target cell + 8 surrounding cells in radius 1 should be cached (total 9)
        assertEquals(9, cache.size)
        assertTrue(cache.contains(targetCoord))
        for (dx in -1..1) {
            for (dz in -1..1) {
                assertTrue(
                    cache.contains(WorldCellCoord(10 + dx, 10 + dz)),
                    "Cell (10+$dx, 10+$dz) should be prefetched into cache",
                )
            }
        }

        // Apply content to ECS World
        val world = World()
        content.applyTo(world)

        var spawnedTile: VirtualTerrainTile? = null
        world.queryEach(VirtualTerrainTileComponent::class) { _, comp ->
            spawnedTile = comp.tile
        }
        assertNotNull(spawnedTile)
        assertEquals(targetCoord, spawnedTile.coord)

        // Unload cell
        listener.onCellUnload(world, targetCoord)
        var countAfterUnload = 0
        world.queryEach(VirtualTerrainTileComponent::class) { _, _ -> countAfterUnload++ }
        assertEquals(0, countAfterUnload, "Tile entity should be destroyed on cell unload.")
    }

    @Test
    fun tileEqualityAndDataIntegrity() {
        val splat = createDummySplat()
        val tile1 = VirtualTerrainTile(
            coord = WorldCellCoord(2, 3),
            splatWeightMap = splat,
            diffuseBytes = byteArrayOf(10, 20),
            normalBytes = byteArrayOf(30, 40),
        )
        val tile2 = VirtualTerrainTile(
            coord = WorldCellCoord(2, 3),
            splatWeightMap = splat,
            diffuseBytes = byteArrayOf(10, 20),
            normalBytes = byteArrayOf(30, 40),
        )
        val tile3 = VirtualTerrainTile(
            coord = WorldCellCoord(2, 3),
            splatWeightMap = splat,
            diffuseBytes = byteArrayOf(99, 20),
            normalBytes = byteArrayOf(30, 40),
        )

        assertEquals(tile1, tile2)
        assertEquals(tile1.hashCode(), tile2.hashCode())
        assertTrue(tile1 != tile3)
    }
}
