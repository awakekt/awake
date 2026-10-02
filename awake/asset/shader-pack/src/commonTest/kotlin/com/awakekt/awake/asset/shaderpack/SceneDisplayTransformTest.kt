/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslEvaluator
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.core.geometry.GpuDataShape
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the shared display transform's ASL -- the source both backends compile -- on the CPU and
 * holds it to the Khronos PBR Neutral reference, transcribed independently below.
 */
class SceneDisplayTransformTest {

    private val probe = shader("display_transform_probe") {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val exposure by uniforms.field(GpuDataShape.Vec4)
        val out = varyings("VertexOutput")
        val radiance by out.varying(GpuDataShape.Vec3, location = 0)
        vertex {
            val inPosition by input(GpuDataShape.Vec3, location = 0)
            val inRadiance by input(GpuDataShape.Vec3, location = 1)
            out.position set vec4(inPosition, 1f.lit)
            radiance set inRadiance
        }
        val displayTransform = sceneDisplayTransform()
        fragment { colorOutput(vec4(displayTransform.display(radiance, exposure.x), 1f.lit)) }
    }

    @Test
    fun theShaderMatchesTheKhronosReference() {
        listOf(
            floatArrayOf(0.02f, 0.02f, 0.02f),
            floatArrayOf(0.18f, 0.18f, 0.18f),
            floatArrayOf(0.5f, 0.3f, 0.1f),
            floatArrayOf(0.833f, 0.612f, 0.102f),
            floatArrayOf(2.6f, 1.9f, 0.32f),
            floatArrayOf(10f, 10f, 10f),
        ).forEach { radiance ->
            assertNear(pbrNeutral(radiance).map(::encode), display(radiance, exposure = 1f), "radiance ${radiance.toList()}")
        }
    }

    @Test
    fun exposureScalesTheRadianceBeforeTheCurve() {
        val radiance = floatArrayOf(0.4f, 0.25f, 0.1f)
        assertNear(display(FloatArray(3) { radiance[it] * 2f }, exposure = 1f).toList(), display(radiance, exposure = 2f), "exposure 2")
    }

    /** Amber lit to 85%: per-channel Reinhard compressed red most and showed khaki; the curve keeps the hue. */
    @Test
    fun aLitColourKeepsItsHue() {
        val amber = floatArrayOf(0.98f * 0.85f, 0.72f * 0.85f, 0.12f * 0.85f)
        val shown = display(amber, exposure = 1f).map { it.pow(2.2f) }.toFloatArray()
        val reinhard = FloatArray(3) { amber[it] / (amber[it] + 1f) }

        assertTrue(abs(hue(shown) - hue(amber)) < 0.5f, "hue ${hue(amber)} became ${hue(shown)}")
        assertTrue(abs(hue(reinhard) - hue(amber)) > 3f, "the control: Reinhard moves this hue, ${hue(reinhard)}")
    }

    private fun display(radiance: FloatArray, exposure: Float): FloatArray = AslEvaluator.evalFragment(
        probe,
        mapOf("radiance" to radiance, "uniforms.exposure" to floatArrayOf(exposure, 0f, 0f, 0f)),
    ).copyOf(3)

    /** Khronos PBR Neutral, from the reference GLSL. */
    private fun pbrNeutral(color: FloatArray): FloatArray {
        val startCompression = 0.8f - 0.04f
        val desaturation = 0.15f
        val x = minOf(color[0], color[1], color[2])
        val offset = if (x < 0.08f) x - 6.25f * x * x else 0.04f
        val shifted = FloatArray(3) { color[it] - offset }
        val peak = maxOf(shifted[0], shifted[1], shifted[2])
        if (peak < startCompression) return shifted
        val d = 1f - startCompression
        val newPeak = 1f - d * d / (peak + d - startCompression)
        val g = 1f - 1f / (desaturation * (peak - newPeak) + 1f)
        return FloatArray(3) { shifted[it] * (newPeak / peak) * (1f - g) + newPeak * g }
    }

    private fun encode(linear: Float): Float = linear.coerceAtLeast(0f).pow(1f / 2.2f)

    /** HSV hue in degrees, for a colour whose channels are ordered red >= green >= blue. */
    private fun hue(rgb: FloatArray): Float = 60f * (rgb[1] - rgb[2]) / (rgb[0] - rgb[2])

    private fun assertNear(expected: List<Float>, actual: FloatArray, label: String) {
        expected.indices.forEach { i ->
            assertTrue(abs(expected[i] - actual[i]) < 1e-5f, "$label channel $i: ${actual[i]} != ${expected[i]}")
        }
    }
}
