/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.asset.shaderdsl.AslEvaluator
import com.awakekt.awake.asset.shaders.UiGlyphShader
import com.awakekt.awake.core.color.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One text coverage rule on the GPU and the CPU raster, for both glyph paths (#1003, #1064):
 * `UiGlyphShader`'s fragment stage, run by the ASL evaluator, gives [textCoverageAlpha] times the tint's
 * alpha for every coverage and text colour below, whether the coverage comes from an exact-size pixel
 * glyph (`fontInfo` x = 0, z = 1) or from the distance field (x = 1). A coverage atlas that is not exact,
 * such as the bitmap font's (x = 0, z = 0), draws its alpha as it is.
 *
 * The evaluator cannot sample a texture or take a derivative, so the atlas sample is supplied, and for
 * the distance field so is `fieldCoverage`, the ramp `DistanceFieldGlyphShaderTwinTest` already holds.
 *
 * Negative control: with the raster's gamma at 1.8 and the shader's at 1.2, the first case off the ends
 * fails, black at coverage 0.05: the shader gives 0.0498 and the raster 0.0335; and dark text at half
 * coverage draws 0.384, outside the polarity check's band.
 */
class TextCoverageRuleTwinTest {
    @Test
    fun pixelGlyphsAndTheDistanceFieldShareTheRasterRule() {
        for (text in COLOURS) {
            for (coverage in COVERAGES) {
                val raster = textCoverageAlpha(coverage, text) * text.a
                val pixelGlyph = shaderAlpha(text, coverage, fontInfo = floatArrayOf(0f, 0f, 1f, 0f))
                val field = shaderAlpha(text, coverage, fontInfo = floatArrayOf(1f, 2f, 0f, 0f))
                assertTrue(abs(pixelGlyph - raster) < TOLERANCE, "$text at $coverage: pixel glyph $pixelGlyph, raster $raster")
                assertTrue(abs(field - raster) < TOLERANCE, "$text at $coverage: distance field $field, raster $raster")
            }
        }
    }

    @Test
    fun coverageThatIsNotExactDrawsAsItIs() {
        for (coverage in COVERAGES) {
            val alpha = shaderAlpha(COLOURS[0], coverage, fontInfo = floatArrayOf(0f, 0f, 0f, 0f))
            assertEquals(coverage * COLOURS[0].a, alpha, TOLERANCE)
        }
    }

    /**
     * The rule's ends and its two polarities: no coverage is no ink, full coverage is full ink, and at half
     * coverage dark text on its light ground and light text on its dark one both land near half, as Chromium
     * draws them, rather than the 0.49 and 0.80 the pixel-glyph rule before it gave.
     */
    @Test
    fun theRuleKeepsItsEndsAndBalancesThePolarities() {
        for (text in COLOURS) {
            assertEquals(0f, textCoverageAlpha(0f, text), 1e-6f, "$text at 0")
            assertEquals(1f, textCoverageAlpha(1f, text), 1e-5f, "$text at 1")
        }
        val dark = textCoverageAlpha(0.5f, Color(0.09f, 0.09f, 0.11f, 1f))
        val light = textCoverageAlpha(0.5f, Color(0.96f, 0.96f, 0.97f, 1f))
        assertTrue(dark in 0.48f..0.52f, "dark text at half coverage: $dark")
        assertTrue(light in 0.53f..0.57f, "light text at half coverage: $light")
    }

    private fun shaderAlpha(text: Color, coverage: Float, fontInfo: FloatArray): Float {
        val trace = AslEvaluator.traceFragment(
            UiGlyphShader,
            inputs = mapOf(
                "uv" to floatArrayOf(0.5f, 0.5f),
                "color" to floatArrayOf(text.r, text.g, text.b, text.a),
                "uniforms.screenToNdc" to floatArrayOf(0f, 0f, 0f, 0f),
                "uniforms.fontInfo" to fontInfo,
            ),
            letOverrides = mapOf(
                "atlas" to floatArrayOf(1f, 1f, 1f, coverage),
                "atlasSizeU" to floatArrayOf(ATLAS_SIZE, ATLAS_SIZE),
                "screenTexSize" to floatArrayOf(ATLAS_SIZE, ATLAS_SIZE),
                "fieldCoverage" to floatArrayOf(coverage),
            ),
        )
        return trace.color[3]
    }

    private companion object {
        const val ATLAS_SIZE = 256f
        const val TOLERANCE = 1e-5f

        /** Black, white, near-black and near-white, the mid grey whose assumed ground is itself, colours, and a faded tint. */
        val COLOURS = listOf(
            Color(0f, 0f, 0f, 1f),
            Color(1f, 1f, 1f, 1f),
            Color(0.09f, 0.09f, 0.11f, 1f),
            Color(0.96f, 0.96f, 0.97f, 1f),
            Color(0.5f, 0.5f, 0.5f, 1f),
            Color(0.95f, 0.75f, 0.2f, 1f),
            Color(0.2f, 0.4f, 0.9f, 1f),
            Color(1f, 1f, 1f, 0.6f),
        )
        val COVERAGES = listOf(0f, 0.05f, 0.25f, 0.5f, 0.75f, 0.95f, 1f)
    }
}
