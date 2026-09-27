/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerrainLayerPaletteTest {

    private val palette = TerrainLayerPalette(
        layers = listOf(
            TerrainLayer(id = "grass", albedo = "grass.png", tiling = 4f),
            TerrainLayer(id = "rock", albedo = "rock.png", height = "rock_height.png", tiling = 8f, blendSharpness = 1f),
        ),
    )

    @Test
    fun thePaletteCodecRoundTrips() {
        assertEquals(palette, TerrainLayerPaletteCodec.decode(TerrainLayerPaletteCodec.encode(palette)))
    }

    @Test
    fun validationNamesEveryProblem() {
        val broken = TerrainLayerPalette(
            formatVersion = 9,
            layers = listOf(
                TerrainLayer(id = "a", albedo = "", tiling = 1000f, blendSharpness = 2f),
                TerrainLayer(id = "a", albedo = "a.png"),
            ),
        )

        assertEquals(5, broken.validate().size)
        assertFailsWith<IllegalArgumentException> { TerrainLayerPaletteCodec.decode(TerrainLayerPaletteCodec.encode(broken)) }
    }

    @Test
    fun tilingSurvivesTheLayerTableWithinTwoPercent() {
        listOf(MIN_LAYER_TILING, 0.7f, 4f, 13f, MAX_LAYER_TILING).forEach { tiling ->
            val decoded = decodeTiling(encodeTiling(tiling))
            assertTrue(abs(decoded - tiling) / tiling < 0.02f, "$tiling came back as $decoded")
        }
    }

    @Test
    fun theLayerTableHoldsOneTexelPerLayer() {
        val table = layerTable(palette)

        assertEquals(2, table.width)
        assertEquals(1, table.height)
        assertEquals(255, table.data[4 + 1].toInt() and 0xFF)
    }

    /** Height comes from the height image's red channel, never from albedo alpha. */
    @Test
    fun packingPutsHeightInAlpha() {
        val albedo = listOf(solid(10, alpha = 7), solid(20, alpha = 7))
        val packed = packLayerArray(albedo, listOf(null, solid(99)))

        assertEquals(2, packed.layerCount)
        assertEquals(255, packed.data[3].toInt() and 0xFF)
        assertEquals(99, packed.data[16 + 3].toInt() and 0xFF)
        assertEquals(20, packed.data[16].toInt() and 0xFF)
    }

    @Test
    fun aOneLayerPaletteIsPaddedToTwoArrayLayers() {
        assertEquals(2, packLayerArray(listOf(solid(10))).layerCount)
    }

    @Test
    fun layersOfDifferentSizesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            packLayerArray(listOf(solid(1), TextureAsset(ByteArray(3 * 3 * 4), 3, 3)))
        }
    }

    @Test
    fun theSurfaceBindsItsFourTextures() {
        val control = TerrainControlMap.reduce(2, 2, layerCount = 2) { layer, x, _ -> if (layer == x) 1f else 0f }.controlMap

        val surface = terrainLayersSurface(palette, packLayerArray(listOf(solid(1), solid(2))), control)

        assertEquals(
            setOf(LAYER_ALBEDO_BINDING, LAYER_TABLE_BINDING, CONTROL_INDICES_BINDING, CONTROL_WEIGHTS_BINDING),
            surface.textures.keys,
        )
    }

    @Test
    fun aControlMapUsingALayerThePaletteLacksIsRejected() {
        val control = TerrainControlMap.reduce(1, 1, layerCount = 3) { layer, _, _ -> if (layer == 2) 1f else 0f }.controlMap

        assertFailsWith<IllegalArgumentException> {
            terrainLayersSurface(palette, packLayerArray(listOf(solid(1), solid(2))), control)
        }
    }

    /** A 2x2 image of one grey, with [alpha] in every alpha byte. */
    private fun solid(value: Int, alpha: Int = 255) = TextureAsset(
        ByteArray(2 * 2 * 4) { if (it % 4 == 3) alpha.toByte() else value.toByte() },
        2,
        2,
    )
}
