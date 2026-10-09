/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.HeightmapSampleEdit
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.core.math.Vec3f
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One step of the 16-bit encoding over the fixtures' 0..10 elevation range, which edits snap to. */
private const val QUANTUM = 10f / 65535f

@OptIn(ExperimentalCoroutinesApi::class)
class PagedTerrainTest {
    private fun terrain(capacity: Int = 2, uploadBytes: Int = 512): PagedTerrain {
        val layout = TerrainPageLayout(-1, 0, 4, 1, 4f, 4, minElevation = 0f, maxElevation = 10f)
        return PagedTerrain(PagedHeightmap(layout, Heightmap(FloatArray(10), 5, 2, Vec3f(4f, 1f, 4f))), capacity, maxUploadBytes = uploadBytes)
    }
    private fun page(value: Float = 1f) = TerrainPage(Heightmap(FloatArray(25) { value }, 5, 5, Vec3f(1f, 1f, 1f)))

    @Test fun sharedEdgeEditsAreAtomicAndDirtyPagesStayPinnedUntilTheirRevisionIsSaved() {
        val terrain = terrain()
        terrain.put(TerrainPageCoord(-1, 0), page())
        assertFailsWith<IllegalArgumentException> { terrain.editHeights(listOf(HeightmapSampleEdit(4, 2, 5f))) }
        assertEquals(1f, terrain.heights.page(TerrainPageCoord(-1, 0))!!.heightAt(4, 2))
        terrain.put(TerrainPageCoord(0, 0), page())
        val changed = terrain.editHeights(listOf(HeightmapSampleEdit(4, 2, 5f)))
        assertEquals(setOf(TerrainPageCoord(-1, 0), TerrainPageCoord(0, 0)), changed)
        for (coord in changed) assertFalse(terrain.evict(coord))
        assertEquals(5f, terrain.heights.heightAtWorld(0.0, 2.0), QUANTUM)
        val oldSave = terrain.saveSnapshot(TerrainPageCoord(0, 0))
        terrain.editHeights(listOf(HeightmapSampleEdit(5, 2, 7f)))
        terrain.acknowledgeSave(oldSave)
        assertFalse(terrain.evict(oldSave.coord))
        terrain.acknowledgeSave(terrain.saveSnapshot(oldSave.coord))
        assertTrue(terrain.evict(oldSave.coord))
        assertEquals(0f, terrain.heights.heightAtWorld(1.0, 2.0))
    }

    @Test fun sharedCoarseKnotsPersistAfterFinePagesAreSavedAndEvicted() {
        val terrain = terrain()
        val left = TerrainPageCoord(-1, 0)
        val right = TerrainPageCoord(0, 0)
        terrain.put(left, page())
        terrain.put(right, page())
        terrain.editHeights(listOf(HeightmapSampleEdit(4, 0, 5f)))
        val old = terrain.fallbackSaveSnapshot()
        assertTrue(terrain.fallbackDirty)
        assertEquals(5f, old.second.heightAt(1, 0), QUANTUM)
        terrain.editHeights(listOf(HeightmapSampleEdit(4, 0, 7f)))
        terrain.acknowledgeFallbackSave(old.first)
        assertTrue(terrain.fallbackDirty)
        terrain.acknowledgeFallbackSave(terrain.fallbackSaveSnapshot().first)
        assertFalse(terrain.fallbackDirty)
        for (coord in listOf(left, right)) {
            terrain.acknowledgeSave(terrain.saveSnapshot(coord))
            assertTrue(terrain.evict(coord))
        }
        assertEquals(7f, terrain.heights.heightAtWorld(0.0, 0.0), QUANTUM)
    }

    @Test fun aSavedSeamEditReloadsBesideItsStillResidentNeighbour() {
        val terrain = terrain()
        val left = TerrainPageCoord(-1, 0)
        terrain.put(left, page(0f))
        terrain.put(TerrainPageCoord(0, 0), page(0f))
        terrain.editHeights(listOf(HeightmapSampleEdit(4, 2, 3.14159f)))
        val save = terrain.saveSnapshot(left)
        val written = RawHeightmapCodec.encode16LittleEndian(save.page.height, 0f, 10f)
        terrain.acknowledgeSave(save)
        assertTrue(terrain.evict(left))
        // The neighbour still holds the edit in memory; the reloaded file must agree with it exactly.
        assertTrue(terrain.put(left, TerrainPage(RawHeightmapCodec.decode(written, 5, 5, Vec3f(1f, 1f, 1f), minElevation = 0f, maxElevation = 10f))))
    }

    // Pins the deprecated journal until its removal; PagedTerrainUploadsTest covers the replacement.
    @Suppress("DEPRECATION")
    @Test fun partialUploadsPublishOnlyCompleteCellsAndEveryFrameSlotCatchesUp() {
        val terrain = terrain(uploadBytes = 156) // 100-byte page + 16-byte table + 40-byte fallback.
        terrain.put(TerrainPageCoord(-1, 0), page())
        terrain.put(TerrainPageCoord(0, 0), page())
        val first = terrain.updates(0)
        assertTrue(first.sumOf { it.region.data.size } <= 156)
        assertEquals(1, first.count { it.binding == TERRAIN_HEIGHT_PAGES_BINDING })
        assertEquals(1, first.last().region.data[0].toInt())
        assertEquals(0, first.last().region.data[4].toInt())
        val second = terrain.updates(0)
        assertEquals(2, second.last().region.data[4].toInt())
        assertTrue(terrain.updates(0).isEmpty())
        assertEquals(1, terrain.updates(1).count { it.binding == TERRAIN_HEIGHT_PAGES_BINDING })
        terrain.evict(TerrainPageCoord(-1, 0))
        terrain.put(TerrainPageCoord(1, 0), TerrainPage(Heightmap(FloatArray(25) { if (it % 5 == 0) 1f else 2f }, 5, 5, Vec3f(1f, 1f, 1f))))
        val replacement = terrain.updates(0)
        assertEquals(0, replacement.last().region.data[0].toInt())
        assertEquals(1, replacement.last().region.data[8].toInt())
    }

    @Test fun mismatchedBordersAndRangesNeverBecomeVisible() {
        val terrain = terrain()
        terrain.put(TerrainPageCoord(-1, 0), page())
        assertFailsWith<IllegalArgumentException> { terrain.put(TerrainPageCoord(0, 0), page(2f)) }
        assertFailsWith<IllegalArgumentException> { terrain.put(TerrainPageCoord(1, 0), page(11f)) }
        assertEquals(setOf(TerrainPageCoord(-1, 0)), terrain.residentCoords)
    }

    @Test fun lateCancelledReadCannotReplaceAReenteredCell() = runTest {
        val terrain = terrain()
        var calls = 0
        val streamer = TerrainPageStreamer(terrain, this, {
            calls++
            val call = calls
            withContext(NonCancellable) { delay(if (call == 1) 100 else 1) }
            page(call.toFloat())
        }, radius = 0, maxConcurrentReads = 1)
        streamer.update(-2.0, 2.0)
        runCurrent()
        streamer.update(10.0, 2.0)
        streamer.update(-2.0, 2.0)
        assertEquals(1, calls) // A non-cooperative cancelled reader still occupies the read budget.
        advanceUntilIdle()
        streamer.update(-2.0, 2.0)
        advanceUntilIdle()
        streamer.update(-2.0, 2.0)
        assertEquals(2f, terrain.page(TerrainPageCoord(-1, 0))!!.height.heightAt(1, 1))
        assertTrue(streamer.pendingReads <= 1)
        streamer.close()
        assertTrue(terrain.residentCoords.isEmpty())
    }

    @Test fun indexRoundTripsAndRejectsDuplicatesAndUnnestedFallbacks() {
        val index = TerrainPageIndex(minCellX = -1, minCellZ = 0, cellCountX = 4, cellCountZ = 1, cellSize = 4f, intervals = 4, minElevation = 0f, maxElevation = 10f, fallbackHeight = "coarse.raw", fallbackWidth = 5, fallbackDepth = 2, pages = listOf(TerrainPageAssets(-1, 0, "cell.raw")))
        assertEquals(index, TerrainPageIndexCodec.decode(TerrainPageIndexCodec.encode(index)))
        assertFailsWith<IllegalArgumentException> { index.copy(pages = index.pages + index.pages).validate() }
        assertFailsWith<IllegalArgumentException> { index.copy(fallbackWidth = 4).validate() }
    }
}
