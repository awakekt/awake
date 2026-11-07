/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.worldstream.ScenePagedTerrain
import com.awakekt.awake.terrain.TerrainPageAssets
import com.awakekt.awake.terrain.TerrainPageIndex
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TerrainLayerPagingTest {
    // Four 4 m cells in a row, each with an 8 x 8 share of the whole control map and lightmap.
    private val index = TerrainPageIndex(
        minCellX = 0, minCellZ = 0, cellCountX = CELLS, cellCountZ = 1, cellSize = 4f, intervals = 4,
        minElevation = 0f, maxElevation = 10f, fallbackHeight = "coarse.raw", fallbackWidth = 5, fallbackDepth = 2,
        palette = "palette.json", pages = List(CELLS) { TerrainPageAssets(it, 0, "cell$it.raw") },
    )

    /** Every texel of cell `n` blends layer `n % 2` alone; its light's red channel is `10 * (n + 1)`. */
    private val control = TerrainControlMap(
        CELLS * TEXELS, TEXELS,
        ByteArray(CELLS * TEXELS * TEXELS * SLOTS) { ((it / SLOTS % (CELLS * TEXELS)) / TEXELS % 2).toByte() },
        ByteArray(CELLS * TEXELS * TEXELS * SLOTS) { if (it % SLOTS == 0) 255.toByte() else 0 },
        SLOTS,
    )
    private val lightmap = TerrainLightmap(
        CELLS * TEXELS, TEXELS,
        ByteArray(CELLS * TEXELS * TEXELS * 4) { if (it % 4 == 0) (10 * ((it / 4 % (CELLS * TEXELS)) / TEXELS + 1)).toByte() else 0 },
    )

    @Test
    fun enteringAPagedTerrainReadsOnlyTheLayerAndLightBytesOfTheCellsInReach() = runTest {
        val paged = TerrainLayerPaging.page(index, control, lightmap, coarseTexelsPerCell = 2)
        val files = projectFiles(paged)
        val bytesRead = mutableMapOf<String, Int>()
        val source = AssetSource { path ->
            val bytes = files.getValue(path.value)
            bytesRead[path.value] = (bytesRead[path.value] ?: 0) + bytes.size
            Result.success(bytes)
        }
        val config = ScenePagedTerrain("world/index.json", capacity = CELLS, radius = 1, maxConcurrentReads = 2)
        val layers = PagedTerrainLayers.load(source, AssetPath(config.index), config.capacity, config.maxUploadBytes) { TextureAsset(ByteArray(16), 2, 2) }
        val streamer = config.streamer(layers.terrain, this, layers::read)
        val eye = Vec3f(2f, 10f, 2f)
        val system = config.system(streamer, observer = { eye })
        val world = World()

        repeat(STEPS) {
            system.update(world, STEP)
            advanceUntilIdle()
        }

        // Cell 0 and its neighbour 1 are in reach; 2 and 3 are not, so nothing of theirs is read.
        assertEquals(setOf(TerrainPageCoord(0, 0), TerrainPageCoord(1, 0)), layers.terrain.residentCoords)
        val layerAndLight = bytesRead.filterKeys { it.endsWith(".terrainctl") || it.endsWith(".terrainlight") }
        val expected = listOf("coarse", "0_0", "1_0").flatMap { listOf("world/layers/$it.terrainctl", "world/layers/$it.terrainlight") }
        assertEquals(expected.associateWith { files.getValue(it).size }, layerAndLight)
        val whole = TerrainControlMapCodec.encode(control).size + TerrainLightmapCodec.encode(lightmap).size
        assertTrue(layerAndLight.values.sum() < whole, "entry read ${layerAndLight.values.sum()} of $whole whole-map bytes")

        // The resident cell draws its own share of the whole maps.
        val cell1 = layers.terrain.page(TerrainPageCoord(1, 0))!!.textures
        assertEquals(1, cell1.getValue(CONTROL_INDICES_BINDING).data[0].toInt())
        assertEquals(20, cell1.getValue(LIGHTMAP_BINDING).data[0].toInt())

        // Walking to the last cell reads cells 2 and 3, once each.
        eye.x = 14f
        repeat(STEPS) {
            system.update(world, STEP)
            advanceUntilIdle()
        }
        assertEquals(setOf(TerrainPageCoord(2, 0), TerrainPageCoord(3, 0)), layers.terrain.residentCoords)
        assertEquals(files.getValue("world/layers/3_0.terrainctl").size, bytesRead["world/layers/3_0.terrainctl"])
        system.close(world)
    }

    @Test
    fun theCoarseMapsSampleEachCellsCentreAndTheIndexNamesEveryPagedFile() {
        val paged = TerrainLayerPaging.page(index, control, lightmap, coarseTexelsPerCell = 1, directory = "paged")

        assertEquals(TEXELS, paged.index.controlWidth)
        assertEquals(SLOTS, paged.index.controlSlots)
        assertEquals("paged/coarse.terrainctl", paged.index.fallbackControl)
        assertEquals("paged/2_0.terrainlight", paged.index.pages[2].lightmap)
        val coarse = TerrainControlMapCodec.decode(paged.files.getValue("paged/coarse.terrainctl"))
        assertEquals(listOf(0, 1, 0, 1), List(CELLS) { coarse.layerAt(it, 0, 0) })
        val coarseLight = TerrainLightmapCodec.decode(paged.files.getValue("paged/coarse.terrainlight")).copyRgba()
        assertEquals(listOf(10, 20, 30, 40), List(CELLS) { coarseLight[it * 4].toInt() })
        assertEquals(paged.index.pages.size * 2 + 2, paged.files.size)
    }

    @Test
    fun withoutALightmapCellsAndTheCoarseSurfaceUseNeutralLight() {
        val paged = TerrainLayerPaging.page(index, control)

        assertTrue(paged.index.pages.all { it.lightmap == null } && paged.index.fallbackLightmap == null)
        assertTrue(paged.files.keys.none { it.endsWith(".terrainlight") })
    }

    @Test
    fun mapsThatDoNotSplitIntoWholeCellsAreRejected() {
        val ragged = TerrainControlMap(30, TEXELS, ByteArray(30 * TEXELS * SLOTS), ByteArray(30 * TEXELS * SLOTS), SLOTS)
        assertFailsWith<IllegalArgumentException> { TerrainLayerPaging.page(index, ragged) }
        assertFailsWith<IllegalArgumentException> { TerrainLayerPaging.page(index, control, TerrainLightmap.Neutral) }
        assertFailsWith<IllegalArgumentException> { TerrainLayerPaging.page(index, control, coarseTexelsPerCell = TEXELS + 1) }
        assertFailsWith<IllegalArgumentException> { TerrainLayerPaging.page(index.copy(palette = null), control) }
    }

    private fun projectFiles(paged: TerrainLayerPages): Map<String, ByteArray> {
        val cell = RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(25), 5, 5, Vec3f(1f, 1f, 1f)), 0f, 10f)
        val palette = TerrainLayerPalette(layers = listOf(TerrainLayer("soil", "soil.png"), TerrainLayer("stone", "stone.png")))
        return mapOf(
            "world/index.json" to TerrainPageIndexCodec.encode(paged.index),
            "world/palette.json" to TerrainLayerPaletteCodec.encode(palette),
            "world/soil.png" to byteArrayOf(1),
            "world/stone.png" to byteArrayOf(2),
            "world/coarse.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(10), 5, 2, Vec3f(4f, 1f, 4f)), 0f, 10f),
        ) + List(CELLS) { "world/cell$it.raw" to cell } + paged.files.mapKeys { "world/${it.key}" }
    }

    private companion object {
        const val CELLS = 4
        const val TEXELS = 8
        const val SLOTS = 4
        const val STEPS = 8
        const val STEP = 1f / 60f
    }
}
