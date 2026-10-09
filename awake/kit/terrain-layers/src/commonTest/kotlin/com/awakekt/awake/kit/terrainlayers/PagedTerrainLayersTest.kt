/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.TerrainPageAssets
import com.awakekt.awake.terrain.TerrainPageIndex
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PagedTerrainLayersTest {
    @Test fun sharedPaletteLoadsOnceAndWideControlEditsSaveOnlyTheirCellAndCoarseFallback() = runTest {
        val index = TerrainPageIndex(
            minCellX = 0, minCellZ = 0, cellCountX = 2, cellCountZ = 1, cellSize = 4f, intervals = 4,
            minElevation = 0f, maxElevation = 10f, fallbackHeight = "coarse.raw", fallbackWidth = 3, fallbackDepth = 2,
            palette = "palette.json", fallbackControl = "coarse.terrainctl", controlWidth = 2, controlDepth = 2, controlSlots = 8,
            pages = listOf(TerrainPageAssets(0, 0, "cell.raw", "cell.terrainctl")),
        )
        fun control(width: Int, depth: Int, layer: Int) = TerrainControlMap(width, depth, ByteArray(width * depth * 8) { layer.toByte() }, ByteArray(width * depth * 8) { if (it % 8 == 0) 255.toByte() else 0 }, 8)
        val files = mutableMapOf(
            "world/index.json" to TerrainPageIndexCodec.encode(index),
            "world/palette.json" to TerrainLayerPaletteCodec.encode(TerrainLayerPalette(layers = listOf(TerrainLayer("soil", "soil.png"), TerrainLayer("stone", "stone.png")))),
            "world/soil.png" to byteArrayOf(1),
            "world/stone.png" to byteArrayOf(2),
            "world/coarse.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(6), 3, 2, Vec3f(4f, 1f, 4f)), 0f, 10f),
            "world/cell.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(25), 5, 5, Vec3f(1f, 1f, 1f)), 0f, 10f),
            "world/coarse.terrainctl" to TerrainControlMapCodec.encode(control(2, 1, 0)),
            "world/cell.terrainctl" to TerrainControlMapCodec.encode(control(2, 2, 0)),
        )
        val reads = mutableListOf<String>()
        val source = AssetSource { path ->
            reads += path.value
            Result.success(files.getValue(path.value))
        }
        val loaded = PagedTerrainLayers.load(source, AssetPath("world/index.json"), capacity = 2) { bytes -> TextureAsset(ByteArray(16) { bytes[0] }, 2, 2) }
        val coord = TerrainPageCoord(0, 0)
        assertTrue(loaded.terrain.put(coord, assertNotNull(loaded.read(coord))))
        loaded.content.resolve(RenderBackend.WebGpu) // All declarations and shapes must agree.
        loaded.editControl(coord, control(2, 2, 1))
        assertTrue(loaded.terrain.surfaceFallbackDirty)
        assertFalse(loaded.terrain.evict(coord))
        val snapshot = loaded.terrain.saveSnapshot(coord)
        val writes = mutableListOf<String>()
        loaded.save(snapshot) { path, bytes ->
            writes += path.value
            files[path.value] = bytes
        }
        loaded.terrain.acknowledgeSave(snapshot)
        val coarse = loaded.terrain.surfaceFallbackSaveSnapshot()
        loaded.saveSurfaceFallback(coarse) { path, bytes ->
            writes += path.value
            files[path.value] = bytes
        }
        loaded.terrain.acknowledgeSurfaceFallbackSave(coarse.first)
        assertEquals(listOf("world/cell.raw", "world/cell.terrainctl", "world/coarse.terrainctl"), writes)
        assertEquals(1, reads.count { it == "world/palette.json" })
        assertEquals(1, TerrainControlMapCodec.decode(files.getValue("world/cell.terrainctl")).layerAt(0, 0, 0))
        val savedCoarse = TerrainControlMapCodec.decode(files.getValue("world/coarse.terrainctl"))
        assertEquals(1, savedCoarse.layerAt(0, 0, 0))
        assertEquals(0, savedCoarse.layerAt(1, 0, 0))
        assertTrue(loaded.terrain.evict(coord))
        loaded.terrain.editFallbackTextures(mapOf(36 to TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)))
        assertFailsWith<IllegalArgumentException> {
            loaded.saveSurfaceFallback(loaded.terrain.surfaceFallbackSaveSnapshot()) { _, _ ->
                error("A missing authored lightmap path must fail before any writes.")
            }
        }
        assertTrue(loaded.terrain.surfaceFallbackDirty)
    }
}
