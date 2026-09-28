/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.splat

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val RAD_TO_DEG = (180.0 / PI).toFloat()
private const val MAX_SLOPE_DEGREES = 90f
private const val TOTAL_WEIGHT_BYTE = 255

/**
 * A rule governing the blend weight of a terrain splat layer based on altitude and surface slope.
 *
 * @property layerIndex Index of the splat texture layer (0..3).
 * @property minAltitude Minimum elevation in world units where this layer appears.
 * @property maxAltitude Maximum elevation in world units where this layer appears.
 * @property altitudeBlend Distance in elevation units over which the layer fades in/out.
 * @property minSlopeDegrees Minimum surface slope angle in degrees [0, 90] where this layer appears.
 * @property maxSlopeDegrees Maximum surface slope angle in degrees [0, 90] where this layer appears.
 * @property slopeBlendDegrees Angular range in degrees over which the slope condition fades in/out.
 */
data class ProceduralTerrainRule(
    val layerIndex: Int,
    val minAltitude: Float = Float.NEGATIVE_INFINITY,
    val maxAltitude: Float = Float.POSITIVE_INFINITY,
    val altitudeBlend: Float = 0f,
    val minSlopeDegrees: Float = 0f,
    val maxSlopeDegrees: Float = MAX_SLOPE_DEGREES,
    val slopeBlendDegrees: Float = 0f,
) {
    init {
        require(layerIndex in 0..3) { "layerIndex must be in 0..3; was $layerIndex" }
        require(minAltitude <= maxAltitude) {
            "minAltitude ($minAltitude) cannot exceed maxAltitude ($maxAltitude)"
        }
        require(minSlopeDegrees in 0f..MAX_SLOPE_DEGREES) {
            "minSlopeDegrees must be in [0, 90]; was $minSlopeDegrees"
        }
        require(maxSlopeDegrees in 0f..MAX_SLOPE_DEGREES) {
            "maxSlopeDegrees must be in [0, 90]; was $maxSlopeDegrees"
        }
        require(minSlopeDegrees <= maxSlopeDegrees) {
            "minSlopeDegrees ($minSlopeDegrees) cannot exceed maxSlopeDegrees ($maxSlopeDegrees)"
        }
        require(altitudeBlend >= 0f) { "altitudeBlend cannot be negative; was $altitudeBlend" }
        require(slopeBlendDegrees >= 0f) { "slopeBlendDegrees cannot be negative; was $slopeBlendDegrees" }
    }

    /**
     * Evaluates the continuous blend factor $\in [0, 1]$ for this rule given surface [altitude] and [slopeDegrees].
     */
    fun evaluate(altitude: Float, slopeDegrees: Float): Float {
        val altWeight = evaluateRange(altitude, minAltitude, maxAltitude, altitudeBlend)
        val slopeWeight = evaluateRange(slopeDegrees, minSlopeDegrees, maxSlopeDegrees, slopeBlendDegrees)
        return altWeight * slopeWeight
    }

    private fun evaluateRange(value: Float, minVal: Float, maxVal: Float, blend: Float): Float {
        val low = when {
            minVal == Float.NEGATIVE_INFINITY -> 1f
            blend > 0f -> smoothstep(minVal - blend, minVal, value)
            value >= minVal -> 1f
            else -> 0f
        }
        val high = when {
            maxVal == Float.POSITIVE_INFINITY -> 1f
            blend > 0f -> 1f - smoothstep(maxVal, maxVal + blend, value)
            value <= maxVal -> 1f
            else -> 0f
        }
        return (low * high).coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 >= edge1) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Procedural material configuration containing rules and industry-standard biome presets.
 *
 * @property rules Biome rules dictating the distribution of terrain layers.
 */
data class ProceduralTerrainMaterialConfig(
    val rules: List<ProceduralTerrainRule>,
) {
    companion object {
        /**
         * Alpine mountain preset:
         * - Layer 0 (Grass): Valleys and gentle slopes (<= 50m, <= 30 deg).
         * - Layer 1 (Dirt/Forest): Mid elevations (30m..120m, <= 45 deg).
         * - Layer 2 (Rock/Cliff): Steep cliff faces across all elevations (35..90 deg).
         * - Layer 3 (Snow): High mountain peaks (>= 100m, <= 50 deg).
         */
        val MountainAlpine = ProceduralTerrainMaterialConfig(
            rules = listOf(
                ProceduralTerrainRule(
                    layerIndex = 0,
                    maxAltitude = 50f,
                    altitudeBlend = 15f,
                    maxSlopeDegrees = 30f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 1,
                    minAltitude = 30f,
                    maxAltitude = 120f,
                    altitudeBlend = 20f,
                    maxSlopeDegrees = 45f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 2,
                    minSlopeDegrees = 35f,
                    maxSlopeDegrees = 90f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 3,
                    minAltitude = 100f,
                    altitudeBlend = 25f,
                    maxSlopeDegrees = 50f,
                    slopeBlendDegrees = 10f,
                ),
            ),
        )

        /**
         * Rolling hills preset:
         * - Layer 0 (Lush Grass): Low valleys.
         * - Layer 1 (Dry Grass): Hill crests and rolling meadows.
         * - Layer 2 (Soil/Gravel): Steeper hill embankments.
         * - Layer 3 (Bedrock Outcrops): Exposed crest rocks.
         */
        val RollingHills = ProceduralTerrainMaterialConfig(
            rules = listOf(
                ProceduralTerrainRule(
                    layerIndex = 0,
                    maxAltitude = 60f,
                    altitudeBlend = 20f,
                    maxSlopeDegrees = 25f,
                    slopeBlendDegrees = 8f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 1,
                    minAltitude = 40f,
                    maxAltitude = 150f,
                    altitudeBlend = 20f,
                    maxSlopeDegrees = 35f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 2,
                    minSlopeDegrees = 25f,
                    maxSlopeDegrees = 90f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 3,
                    minAltitude = 110f,
                    altitudeBlend = 20f,
                    minSlopeDegrees = 30f,
                    maxSlopeDegrees = 90f,
                    slopeBlendDegrees = 10f,
                ),
            ),
        )

        /**
         * Desert canyon preset:
         * - Layer 0 (Fine Dune Sand): Canyon floors and gentle washes.
         * - Layer 1 (Hardpacked Earth): Plateau slopes.
         * - Layer 2 (Red Sandstone Cliffs): Sheer canyon walls.
         * - Layer 3 (Mesa Capstone): High flat tablelands.
         */
        val DesertCanyon = ProceduralTerrainMaterialConfig(
            rules = listOf(
                ProceduralTerrainRule(
                    layerIndex = 0,
                    maxAltitude = 45f,
                    altitudeBlend = 15f,
                    maxSlopeDegrees = 20f,
                    slopeBlendDegrees = 5f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 1,
                    minAltitude = 30f,
                    maxAltitude = 110f,
                    altitudeBlend = 15f,
                    maxSlopeDegrees = 35f,
                    slopeBlendDegrees = 10f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 2,
                    minSlopeDegrees = 30f,
                    maxSlopeDegrees = 90f,
                    slopeBlendDegrees = 8f,
                ),
                ProceduralTerrainRule(
                    layerIndex = 3,
                    minAltitude = 90f,
                    altitudeBlend = 15f,
                    maxSlopeDegrees = 25f,
                    slopeBlendDegrees = 5f,
                ),
            ),
        )
    }
}

/**
 * Generator that calculates surface slope and altitude gradients from height samples, evaluating
 * procedural rules to output an energy-conserving [TerrainSplatWeightMap].
 */
object ProceduralTerrainSplatGenerator {

    private class HeightGrid(
        val heights: FloatArray,
        val width: Int,
        val height: Int,
        val horizontalScale: Float,
        val verticalScale: Float,
    )

    /**
     * Generates a 4-channel [TerrainSplatWeightMap] from [heights] according to [config].
     *
     * The generated weights strictly satisfy $\sum_{i=0}^3 w_i = 255$ at each sample.
     *
     * @param heights Elevation samples in row-major order ($z \times width + x$).
     * @param width Number of horizontal elevation samples.
     * @param height Number of vertical elevation samples.
     * @param horizontalScale World distance between adjacent samples.
     * @param verticalScale Elevation multiplier.
     * @param config Biome rules to evaluate.
     */
    fun generateSplatMap(
        heights: FloatArray,
        width: Int,
        height: Int,
        horizontalScale: Float = 1f,
        verticalScale: Float = 1f,
        config: ProceduralTerrainMaterialConfig,
    ): TerrainSplatWeightMap {
        require(width > 1 && height > 1) { "Dimensions must be at least 2x2; was $width x $height" }
        require(heights.size >= width * height) {
            "Heights array length ${heights.size} must be at least width * height (${width * height})"
        }
        require(horizontalScale > 0f) { "horizontalScale must be positive: $horizontalScale" }

        val grid = HeightGrid(heights, width, height, horizontalScale, verticalScale)
        val rgbaBytes = ByteArray(width * height * 4)
        val channelWeights = FloatArray(4)

        for (z in 0 until height) {
            for (x in 0 until width) {
                val altitude = heights[z * width + x] * verticalScale
                val slopeDegrees = calculateSlope(grid, x, z)
                evaluateChannelWeights(config, altitude, slopeDegrees, channelWeights)
                packNormalizedWeights(channelWeights, rgbaBytes, (z * width + x) * 4)
            }
        }

        return TerrainSplatWeightMap(width, height, rgbaBytes)
    }

    private fun calculateSlope(grid: HeightGrid, x: Int, z: Int): Float {
        val xPrev = if (x > 0) x - 1 else 0
        val xNext = if (x < grid.width - 1) x + 1 else grid.width - 1
        val zPrev = if (z > 0) z - 1 else 0
        val zNext = if (z < grid.height - 1) z + 1 else grid.height - 1

        val dxDist = (xNext - xPrev) * grid.horizontalScale
        val dzDist = (zNext - zPrev) * grid.horizontalScale

        val dhX = if (dxDist > 0f) {
            ((grid.heights[z * grid.width + xNext] - grid.heights[z * grid.width + xPrev]) * grid.verticalScale) / dxDist
        } else {
            0f
        }
        val dhZ = if (dzDist > 0f) {
            ((grid.heights[zNext * grid.width + x] - grid.heights[zPrev * grid.width + x]) * grid.verticalScale) / dzDist
        } else {
            0f
        }

        val gradientMag = sqrt(dhX * dhX + dhZ * dhZ)
        return (atan2(gradientMag, 1f) * RAD_TO_DEG).coerceIn(0f, MAX_SLOPE_DEGREES)
    }

    private fun evaluateChannelWeights(
        config: ProceduralTerrainMaterialConfig,
        altitude: Float,
        slopeDegrees: Float,
        outWeights: FloatArray,
    ) {
        outWeights.fill(0f)
        for (rule in config.rules) {
            outWeights[rule.layerIndex] += rule.evaluate(altitude, slopeDegrees)
        }
    }

    private fun packNormalizedWeights(
        channelWeights: FloatArray,
        rgbaBytes: ByteArray,
        offset: Int,
    ) {
        val totalWeight = channelWeights[0] + channelWeights[1] + channelWeights[2] + channelWeights[3]
        if (totalWeight <= 1e-6f) {
            rgbaBytes[offset] = TOTAL_WEIGHT_BYTE.toByte()
            rgbaBytes[offset + 1] = 0
            rgbaBytes[offset + 2] = 0
            rgbaBytes[offset + 3] = 0
            return
        }

        val scale = TOTAL_WEIGHT_BYTE / totalWeight
        var w0 = (channelWeights[0] * scale).roundToInt().coerceIn(0, TOTAL_WEIGHT_BYTE)
        var w1 = (channelWeights[1] * scale).roundToInt().coerceIn(0, TOTAL_WEIGHT_BYTE)
        var w2 = (channelWeights[2] * scale).roundToInt().coerceIn(0, TOTAL_WEIGHT_BYTE)
        var w3 = (channelWeights[3] * scale).roundToInt().coerceIn(0, TOTAL_WEIGHT_BYTE)

        val remainder = TOTAL_WEIGHT_BYTE - (w0 + w1 + w2 + w3)
        if (remainder != 0) {
            val maxIdx = findMaxIndex(channelWeights)
            when (maxIdx) {
                0 -> w0 = (w0 + remainder).coerceIn(0, TOTAL_WEIGHT_BYTE)
                1 -> w1 = (w1 + remainder).coerceIn(0, TOTAL_WEIGHT_BYTE)
                2 -> w2 = (w2 + remainder).coerceIn(0, TOTAL_WEIGHT_BYTE)
                3 -> w3 = (w3 + remainder).coerceIn(0, TOTAL_WEIGHT_BYTE)
            }
        }

        rgbaBytes[offset] = w0.toByte()
        rgbaBytes[offset + 1] = w1.toByte()
        rgbaBytes[offset + 2] = w2.toByte()
        rgbaBytes[offset + 3] = w3.toByte()
    }

    private fun findMaxIndex(weights: FloatArray): Int {
        var maxIdx = 0
        var maxVal = weights[0]
        for (i in 1..3) {
            if (weights[i] > maxVal) {
                maxVal = weights[i]
                maxIdx = i
            }
        }
        return maxIdx
    }
}
