/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.splat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProceduralTerrainMaterialTest {

    @Test
    fun flatLowlandGeneratesLayer0Grass() {
        val width = 8
        val height = 8
        // Flat lowland at 10m altitude
        val heights = FloatArray(width * height) { 10f }

        val splatMap = ProceduralTerrainSplatGenerator.generateSplatMap(
            heights = heights,
            width = width,
            height = height,
            horizontalScale = 1f,
            verticalScale = 1f,
            config = ProceduralTerrainMaterialConfig.MountainAlpine,
        )

        assertEquals(width, splatMap.width)
        assertEquals(height, splatMap.height)

        // Center pixel sample
        val weights = splatMap.sampleWeights(0.5f, 0.5f)
        // Layer 0 (Grass) should be 1.0 (255 / 255), other layers 0.0
        assertEquals(1.0f, weights[0], 0.01f)
        assertEquals(0.0f, weights[1], 0.01f)
        assertEquals(0.0f, weights[2], 0.01f)
        assertEquals(0.0f, weights[3], 0.01f)
    }

    @Test
    fun highAltitudeFlatGeneratesLayer3Snow() {
        val width = 8
        val height = 8
        // Flat mountain peak at 200m altitude
        val heights = FloatArray(width * height) { 200f }

        val splatMap = ProceduralTerrainSplatGenerator.generateSplatMap(
            heights = heights,
            width = width,
            height = height,
            horizontalScale = 1f,
            verticalScale = 1f,
            config = ProceduralTerrainMaterialConfig.MountainAlpine,
        )

        val weights = splatMap.sampleWeights(0.5f, 0.5f)
        // Layer 3 (Snow) should dominate (>= 0.95)
        assertTrue(weights[3] >= 0.95f, "High altitude flat terrain should generate snow; was ${weights[3]}")
    }

    @Test
    fun sheerCliffGeneratesLayer2Rock() {
        val width = 8
        val height = 8
        // Steep ramp along X axis: slope = arctan(50) ~= 88.8 degrees
        val heights = FloatArray(width * height) { idx ->
            val x = idx % width
            x * 50f
        }

        val splatMap = ProceduralTerrainSplatGenerator.generateSplatMap(
            heights = heights,
            width = width,
            height = height,
            horizontalScale = 1f,
            verticalScale = 1f,
            config = ProceduralTerrainMaterialConfig.MountainAlpine,
        )

        // Center pixel (where slope is well defined)
        val weights = splatMap.sampleWeights(0.5f, 0.5f)
        // Layer 2 (Rock/Cliff) should dominate on steep slopes
        assertTrue(weights[2] >= 0.8f, "Steep cliff should generate rock layer 2; was ${weights[2]}")
    }

    @Test
    fun energyConservationSumEquals255Everywhere() {
        val width = 16
        val height = 16
        // Complex noisy terrain with valleys, ridges, and slopes
        val heights = FloatArray(width * height) { idx ->
            val x = idx % width
            val z = idx / width
            (kotlin.math.sin(x * 0.5f) * 40f + kotlin.math.cos(z * 0.5f) * 60f + 70f)
        }

        val splatMap = ProceduralTerrainSplatGenerator.generateSplatMap(
            heights = heights,
            width = width,
            height = height,
            horizontalScale = 2f,
            verticalScale = 1f,
            config = ProceduralTerrainMaterialConfig.MountainAlpine,
        )

        val raw = splatMap.rgbaBytes
        for (i in 0 until width * height) {
            val offset = i * 4
            val r = raw[offset].toInt() and 0xFF
            val g = raw[offset + 1].toInt() and 0xFF
            val b = raw[offset + 2].toInt() and 0xFF
            val a = raw[offset + 3].toInt() and 0xFF
            val sum = r + g + b + a

            assertEquals(
                255,
                sum,
                "Splat weight at pixel $i ($r, $g, $b, $a) must sum to 255 exactly.",
            )
        }
    }

    @Test
    fun presetBiomeConfigurationsAreWellFormed() {
        val alpine = ProceduralTerrainMaterialConfig.MountainAlpine
        assertEquals(4, alpine.rules.size)
        assertTrue(alpine.rules.any { it.layerIndex == 0 })
        assertTrue(alpine.rules.any { it.layerIndex == 1 })
        assertTrue(alpine.rules.any { it.layerIndex == 2 })
        assertTrue(alpine.rules.any { it.layerIndex == 3 })

        val hills = ProceduralTerrainMaterialConfig.RollingHills
        assertEquals(4, hills.rules.size)

        val desert = ProceduralTerrainMaterialConfig.DesertCanyon
        assertEquals(4, desert.rules.size)
    }
}
