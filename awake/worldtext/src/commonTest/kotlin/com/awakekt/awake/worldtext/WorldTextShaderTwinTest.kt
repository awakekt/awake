/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.asset.shaderdsl.AslEvaluator
import com.awakekt.awake.asset.shaderpack.WorldTextShader
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [worldTextEdgeWidth] and [worldTextCoverage] are `WorldTextShader`'s own fragment math: run by the
 * ASL evaluator, the shader gives the same alpha for every sample and footprint below.
 *
 * The evaluator cannot sample a texture, read its size or take a derivative, so the atlas sample, its
 * size, the true distance's screen gradient and the uv's two screen derivatives are supplied by name;
 * the footprint bounds, the ramp, the median and the tint and vertex alpha are the shader's.
 */
class WorldTextShaderTwinTest {
    /** A pixel's footprint: the true distance's screen gradient, and the uv's screen derivatives. */
    private data class Footprint(val gradient: FloatArray, val uvAcross: FloatArray, val uvDown: FloatArray)

    @Test
    fun cpuCoverageMatchesTheShader() {
        for (footprint in FOOTPRINTS) {
            for (sample in SAMPLES) {
                val trace = AslEvaluator.traceFragment(
                    WorldTextShader,
                    inputs = mapOf(
                        "uv" to floatArrayOf(0.5f, 0.5f),
                        "color" to floatArrayOf(1f, 1f, 1f, VERTEX_ALPHA),
                        "uniforms.tint" to floatArrayOf(1f, 1f, 1f, TINT_ALPHA),
                        "uniforms.field" to floatArrayOf(RANGE_TEXELS, 0f, 0f, 0f),
                    ),
                    letOverrides = mapOf(
                        "atlas" to sample,
                        "atlasSizeU" to floatArrayOf(ATLAS_SIZE, ATLAS_SIZE),
                        "trueGradient" to footprint.gradient,
                        "uvAcross" to footprint.uvAcross,
                        "uvDown" to footprint.uvDown,
                    ),
                )
                val shader = trace.color[3]
                val edgeWidth = worldTextEdgeWidth(
                    footprint.gradient[0],
                    footprint.gradient[1],
                    texels(footprint.uvAcross),
                    texels(footprint.uvDown),
                    RANGE_TEXELS,
                )
                val cpu = worldTextCoverage(sample[0], sample[1], sample[2], edgeWidth) * VERTEX_ALPHA * TINT_ALPHA
                assertTrue(abs(shader - cpu) < TOLERANCE, "sample ${sample.toList()} at $footprint: shader $shader, cpu $cpu")
            }
        }
    }

    @Test
    fun theFootprintBoundsTheMeasuredWidth() {
        // Close up, a quarter texel a pixel: a flat field's zero gradient is raised to a quarter texel's change.
        assertEquals(0.125f, worldTextEdgeWidth(0f, 0f, 0.25f, 0.25f, 2f), 1e-6f)
        // Far away, eight texels a pixel: the field is read as coverage, one field unit wide.
        assertEquals(1f, worldTextEdgeWidth(0f, 0f, 8f, 8f, 2f), 1e-6f)
        // Seen at a grazing angle, a texel across and three down: the measured width stands between the two.
        assertEquals(0.9f, worldTextEdgeWidth(0.9f, 0f, 1f, 3f, 2f), 1e-6f)
    }

    private fun texels(uv: FloatArray): Float = sqrt(uv[0] * uv[0] + uv[1] * uv[1]) * ATLAS_SIZE

    @Test
    fun theEdgeIsOneEdgeWidthWide() {
        // A field changing 0.1 a pixel: from 0.45 to 0.55 is one pixel, 0 to 1 coverage.
        assertTrue(abs(worldTextCoverage(0.45f, 0.45f, 0.45f, 0.1f)) < TOLERANCE)
        assertTrue(abs(worldTextCoverage(0.5f, 0.5f, 0.5f, 0.1f) - 0.5f) < TOLERANCE)
        assertTrue(abs(worldTextCoverage(0.55f, 0.55f, 0.55f, 0.1f) - 1f) < TOLERANCE)
    }

    private companion object {
        const val VERTEX_ALPHA = 0.9f
        const val TINT_ALPHA = 0.8f
        const val TOLERANCE = 1e-5f
        const val RANGE_TEXELS = 2f
        const val ATLAS_SIZE = 512f

        /** Close up, about a texel a pixel, far away, and at a grazing angle, with gradients inside and outside the footprint. */
        val FOOTPRINTS = listOf(
            Footprint(floatArrayOf(0.01f, 0f), floatArrayOf(0.1f / ATLAS_SIZE, 0f), floatArrayOf(0f, 0.1f / ATLAS_SIZE)),
            Footprint(floatArrayOf(0.3f, 0.3f), floatArrayOf(1f / ATLAS_SIZE, 0f), floatArrayOf(0f, 1f / ATLAS_SIZE)),
            Footprint(floatArrayOf(0f, 0f), floatArrayOf(6f / ATLAS_SIZE, 0f), floatArrayOf(0f, 6f / ATLAS_SIZE)),
            Footprint(floatArrayOf(0.7f, 0.1f), floatArrayOf(1f / ATLAS_SIZE, 0f), floatArrayOf(0.5f / ATLAS_SIZE, 3f / ATLAS_SIZE)),
        )

        /** RGBA atlas samples: outside, on and inside the edge, and channels that disagree. */
        val SAMPLES = listOf(
            floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(0.3f, 0.35f, 0.4f, 0.35f),
            floatArrayOf(0.45f, 0.5f, 0.55f, 0.5f),
            floatArrayOf(0.52f, 0.48f, 0.6f, 0.5f),
            floatArrayOf(1f, 0.2f, 0.55f, 0.6f),
            floatArrayOf(0.9f, 0.95f, 1f, 1f),
        )
    }
}
