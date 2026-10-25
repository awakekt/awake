/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.asset.shaderdsl.AslEvaluator
import com.awakekt.awake.asset.shaders.UiGlyphShader
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The raster's distance-field coverage is the glyph shader's own math (#1057): `UiGlyphShader`'s
 * fragment stage, run by the ASL evaluator, and [distanceFieldScreenPxRange] with
 * [distanceFieldCoverage] give the same alpha for every sample and footprint below.
 *
 * The evaluator cannot sample a texture, read its size or take a derivative, so those three `let`s
 * are supplied: the atlas sample, the atlas size, and `screenTexSize = 1 / fwidth(uv)`, which for
 * the axis-aligned quad a glyph is equals its on-screen size over its uv span. Everything after
 * them -- the range, the median, the ramp, the gamma and the tint's alpha -- is the shader's.
 *
 * Negative control: with the range the raster used before (half the range times the horizontal
 * scale alone) the first footprint off the clamp, one screen pixel a texel, already fails: the
 * shader gives alpha 0.264 for the (0.3, 0.35, 0.4) sample and that range 0.388.
 */
class DistanceFieldGlyphShaderTwinTest {
    @Test
    fun rasterCoverageMatchesTheGlyphShader() {
        for (footprint in FOOTPRINTS) {
            for (sample in SAMPLES) {
                val shader = shaderAlpha(sample, footprint)
                val screenPxRange = distanceFieldScreenPxRange(
                    RANGE_TEXELS,
                    ATLAS_WIDTH,
                    ATLAS_HEIGHT,
                    footprint.uvWidth,
                    footprint.uvHeight,
                    footprint.screenWidth,
                    footprint.screenHeight,
                )
                val raster = distanceFieldCoverage(sample[0], sample[1], sample[2], screenPxRange) * TINT_ALPHA
                assertTrue(
                    abs(shader - raster) < TOLERANCE,
                    "sample ${sample.toList()} at $footprint: shader $shader, raster $raster",
                )
            }
        }
    }

    private fun shaderAlpha(sample: FloatArray, footprint: Footprint): Float {
        val trace = AslEvaluator.traceFragment(
            UiGlyphShader,
            inputs = mapOf(
                "uv" to floatArrayOf(0.5f, 0.5f),
                "color" to floatArrayOf(0.07f, 0.07f, 0.07f, TINT_ALPHA),
                "uniforms.screenToNdc" to floatArrayOf(0f, 0f, 0f, 0f),
                // x = 1: a distance-field atlas; y: its range in atlas texels.
                "uniforms.fontInfo" to floatArrayOf(1f, RANGE_TEXELS, 0f, 0f),
            ),
            letOverrides = mapOf(
                "atlas" to sample,
                "atlasSizeU" to floatArrayOf(ATLAS_WIDTH.toFloat(), ATLAS_HEIGHT.toFloat()),
                "screenTexSize" to floatArrayOf(
                    footprint.screenWidth / footprint.uvWidth,
                    footprint.screenHeight / footprint.uvHeight,
                ),
            ),
        )
        return trace.color[3]
    }

    /** A glyph quad [screenWidth] by [screenHeight] screen pixels showing a [uvWidth] by [uvHeight] uv rect. */
    private data class Footprint(val screenWidth: Float, val screenHeight: Float, val uvWidth: Float, val uvHeight: Float)

    private companion object {
        const val ATLAS_WIDTH = 656
        const val ATLAS_HEIGHT = 350
        const val RANGE_TEXELS = 2f
        const val TINT_ALPHA = 0.8f
        const val TOLERANCE = 1e-5f

        /** 20 by 26 atlas texels drawn at 0.5x, 1x, 1.5x, 3x and 4.5x, plus a non-uniform one. */
        val FOOTPRINTS = listOf(0.5f, 1f, 1.5f, 3f, 4.5f).map { scale ->
            Footprint(20f * scale, 26f * scale, 20f / ATLAS_WIDTH, 26f / ATLAS_HEIGHT)
        } + Footprint(30f, 52f, 20f / ATLAS_WIDTH, 26f / ATLAS_HEIGHT)

        /** RGBA atlas samples: outside, on and inside the edge, and channels that disagree. */
        val SAMPLES = listOf(
            floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(0.3f, 0.35f, 0.4f, 0.35f),
            floatArrayOf(0.45f, 0.5f, 0.55f, 0.5f),
            floatArrayOf(0.52f, 0.48f, 0.6f, 0.5f),
            floatArrayOf(0.62f, 0.66f, 0.7f, 0.66f),
            floatArrayOf(1f, 0.2f, 0.55f, 0.6f),
            floatArrayOf(0.9f, 0.95f, 1f, 1f),
        )
    }
}
