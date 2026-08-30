/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The evaluator is the oracle for what the emitted WGSL computes -- these pin its math to
 * hand-derived values, so a preview that looks wrong can be blamed precisely. */
class AslEvaluatorTest {

    @Test
    fun triangleFragmentFullyLitFaceReinhardMaps() {
        // Normal facing the light head-on: diffuse = 1, shade = 1, Reinhard halves the color.
        val rgba = AslEvaluator.evalFragment(
            TriangleShader,
            mapOf(
                "color" to floatArrayOf(1f, 0f, 0f),
                "normal" to floatArrayOf(0f, 0f, 1f),
                "uniforms.lightDirection" to floatArrayOf(0f, 0f, 1f, 0f),
                "uniforms.lightColor" to floatArrayOf(1f, 1f, 1f, 1f),
            ),
        )
        assertNear(floatArrayOf(0.5f, 0f, 0f, 1f), rgba)
    }

    @Test
    fun triangleFragmentBackFaceGetsAmbientOnly() {
        // Normal opposite the light: diffuse clamps to 0, only ambient survives.
        val ambient = 0.08f
        val expected = ambient / (ambient + 1f)
        val rgba = AslEvaluator.evalFragment(
            TriangleShader,
            mapOf(
                "color" to floatArrayOf(1f, 1f, 1f),
                "normal" to floatArrayOf(0f, 0f, -1f),
                "uniforms.lightDirection" to floatArrayOf(0f, 0f, 1f, 0f),
                "uniforms.lightColor" to floatArrayOf(1f, 1f, 1f, 1f),
            ),
        )
        assertNear(floatArrayOf(expected, expected, expected, 1f), rgba)
    }

    @Test
    fun checkerFragmentAlternatesColorsByCellParity() {
        val uniforms = mapOf(
            "uniforms.tiles" to floatArrayOf(4f, 0f, 0f, 0f),
            "uniforms.colorA" to floatArrayOf(1f, 0f, 0f, 1f),
            "uniforms.colorB" to floatArrayOf(0f, 0f, 1f, 1f),
        )
        fun at(u: Float, v: Float) =
            AslEvaluator.evalFragment(CheckerShader, uniforms + ("uv" to floatArrayOf(u, v)))

        assertNear(floatArrayOf(1f, 0f, 0f, 1f), at(0.1f, 0.1f))
        assertNear(floatArrayOf(0f, 0f, 1f, 1f), at(0.3f, 0.1f))
        assertNear(floatArrayOf(1f, 0f, 0f, 1f), at(0.3f, 0.3f))
    }

    @Test
    fun traceExposesIntermediateLetsInDeclarationOrder() {
        val trace = AslEvaluator.traceFragment(
            CheckerShader,
            mapOf(
                "uv" to floatArrayOf(0.3f, 0.1f),
                "uniforms.tiles" to floatArrayOf(4f, 0f, 0f, 0f),
                "uniforms.colorA" to floatArrayOf(1f, 0f, 0f, 1f),
                "uniforms.colorB" to floatArrayOf(0f, 0f, 1f, 1f),
            ),
        )
        assertTrue(trace.lets.keys.toList() == listOf("cell", "parity"), "${trace.lets.keys}")
        assertNear(floatArrayOf(1f, 0f), trace.lets.getValue("cell"))
        assertNear(floatArrayOf(1f), trace.lets.getValue("parity"))
        assertNear(floatArrayOf(0f, 0f, 1f, 1f), trace.color)
    }

    private fun assertNear(expected: FloatArray, actual: FloatArray) {
        assertTrue(expected.size == actual.size, "size ${actual.size} != ${expected.size}")
        expected.indices.forEach { i ->
            assertTrue(
                abs(expected[i] - actual[i]) < 1e-5f,
                "component $i: ${actual[i]} != ${expected[i]}",
            )
        }
    }
}
