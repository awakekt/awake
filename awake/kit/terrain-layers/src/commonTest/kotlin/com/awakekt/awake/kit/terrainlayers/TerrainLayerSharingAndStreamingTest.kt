/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.TerrainSurfaceReference
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies streaming layer-array packing (matching [packLayerArray] without holding all decoded
 * layers in memory simultaneously) and canonical [TerrainLayerArrayKey] sharing across terrain chunks.
 */
class TerrainLayerSharingAndStreamingTest {

    @Test
    fun packLayerArrayStreamingMatchesPackLayerArrayForSingleAndMultipleLayers() = runTest {
        val albedo0 = solid(2, 2, 10, 20, 30, 0)
        val albedo1 = solid(2, 2, 40, 50, 60, 0)
        val height1 = solid(2, 2, 128, 0, 0, 255)

        val eagerSingle = packLayerArray(listOf(albedo0), listOf(null))
        val streamedSingle = packLayerArrayStreaming(1) { albedo0 to null }
        assertEquals(eagerSingle.layerCount, streamedSingle.layerCount)
        assertContentEquals(eagerSingle.data, streamedSingle.data)

        val order = mutableListOf<Int>()
        val eagerMulti = packLayerArray(listOf(albedo0, albedo1), listOf(null, height1))
        val streamedMulti = packLayerArrayStreaming(2) { index ->
            order += index
            if (index == 0) albedo0 to null else albedo1 to height1
        }
        assertEquals(listOf(0, 1), order, "layers must be decoded sequentially one at a time")
        assertEquals(eagerMulti.layerCount, streamedMulti.layerCount)
        assertContentEquals(eagerMulti.data, streamedMulti.data)
    }

    @Test
    fun providerEmitsEqualArrayTextureKeysForChunksSharingAPalette() = runTest {
        val palette = TerrainLayerPalette(
            layers = listOf(
                TerrainLayer("grass", "tex/grass.png"),
                TerrainLayer("rock", "tex/rock.png", height = "tex/rock_h.png"),
            ),
        )
        val control = TerrainControlMap(
            width = 2,
            depth = 2,
            indices = ByteArray(2 * 2 * 4),
            weights = ByteArray(2 * 2 * 4) { if (it % 4 == 0) 255.toByte() else 0 },
        )
        val files = mapOf(
            "world/shared.terrainpalette.json" to TerrainLayerPaletteCodec.encode(palette),
            "world/tex/grass.png" to byteArrayOf(1),
            "world/tex/rock.png" to byteArrayOf(2),
            "world/tex/rock_h.png" to byteArrayOf(3),
            "world/chunk_a.terrainctl" to TerrainControlMapCodec.encode(control),
            "world/chunk_b.terrainctl" to TerrainControlMapCodec.encode(control),
        )
        val assets = AssetSource { path ->
            files[path.value]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("missing $path"))
        }
        val provider = TerrainLayersSurfaceProvider(assets) { bytes ->
            solid(2, 2, bytes[0].toInt() * 10, 0, 0, 255)
        }

        val chunkA = provider.resolve(surfaceRef("world/shared.terrainpalette.json", "world/chunk_a.terrainctl"))
        val chunkB = provider.resolve(surfaceRef("world/shared.terrainpalette.json", "world/chunk_b.terrainctl"))

        val keyA = chunkA.textureKeys[LAYER_ALBEDO_BINDING]
        val keyB = chunkB.textureKeys[LAYER_ALBEDO_BINDING]
        assertTrue(keyA is TerrainLayerArrayKey)
        assertEquals(keyA, keyB, "chunks sharing one palette must emit equal TerrainLayerArrayKey instances")
        assertEquals(chunkA.textureKeys[LAYER_TABLE_BINDING], chunkB.textureKeys[LAYER_TABLE_BINDING])
        assertNotEquals(
            keyA,
            TerrainLayerArrayKey(listOf(TerrainLayerArrayKey.LayerSourceKey("world/tex/other.png"))),
        )
    }

    private fun surfaceRef(palette: String, control: String): TerrainSurfaceReference = TerrainSurfaceReference(
        provider = TERRAIN_LAYERS_PROVIDER,
        version = 1,
        payload = Json.encodeToJsonElement(TerrainLayersPayload.serializer(), TerrainLayersPayload(palette, control)),
    )

    private fun solid(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int): TextureAsset = TextureAsset(
        data = ByteArray(width * height * 4) { index ->
            when (index % 4) {
                0 -> r.toByte()
                1 -> g.toByte()
                2 -> b.toByte()
                else -> a.toByte()
            }
        },
        width = width,
        height = height,
    )
}
