/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #1545: an analytic rounded border (one `RoundedQuad` with a stroke width, two triangles) is at
 * least as close to the true band as the tessellated ring it replaces, so the baselines it moves
 * move toward the truth. The truth is the band's coverage at 8 x 8 samples a pixel.
 *
 * Measured, mean error in channel steps (tessellated -> analytic): 1 px borders at radius 4, 8 and
 * 12 go 0.27 -> 0.05, 0.53 -> 0.12, 0.89 -> 0.17; 2 px borders are within 0.01 either way. A square
 * border at a half-pixel offset went 0.136 -> 0.159, worse at its corners, so `Modifier.border`
 * keeps the ring for a square shape and draws only rounded ones analytically.
 */
class AnalyticBorderAccuracyTest {
    private val size = 64

    /** Mean absolute coverage error, in channel steps, of [pixels] against the supersampled band. */
    private fun error(pixels: ByteArray, x: Float, y: Float, w: Float, h: Float, radius: Float, stroke: Float): Double {
        var total = 0.0
        for (pixel in 0 until size * size) {
            val truth = truth(pixel % size, pixel / size) { sx, sy -> roundedDistance(sx, sy, x, y, w, h, radius).let { it <= 0f && it > -stroke } }
            // Black ink on white paper: coverage is how far the red channel fell.
            val drawn = 1.0 - (pixels[pixel * 4].toInt() and 0xFF) / 255.0
            total += abs(drawn - truth) * 255.0
        }
        return total / (size * size)
    }

    /** The share of [SAMPLES] x [SAMPLES] samples in pixel ([px], [py]) that [inside] accepts. */
    private inline fun truth(px: Int, py: Int, inside: (Float, Float) -> Boolean): Double {
        var count = 0
        for (sample in 0 until SAMPLES * SAMPLES) {
            if (inside(px + (sample % SAMPLES + 0.5f) / SAMPLES, py + (sample / SAMPLES + 0.5f) / SAMPLES)) count++
        }
        return count.toDouble() / (SAMPLES * SAMPLES)
    }

    private fun roundedDistance(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float, radius: Float): Float {
        val qx = abs(px - (x + w / 2f)) - w / 2f + radius
        val qy = abs(py - (y + h / 2f)) - h / 2f + radius
        return sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f)) + min(max(qx, qy), 0f) - radius
    }

    /** The ring `Modifier.border` tessellated before: the centreline inset by half the stroke. */
    private fun tessellated(x: Float, y: Float, w: Float, h: Float, radius: Float, stroke: Float): UiDrawPrimitive {
        val inset = stroke / 2f
        val path = DrawShape.RoundedRectangle((radius - inset).coerceAtLeast(0f).dp)
            .toPath(Rectangle(x + inset, y + inset, w - stroke, h - stroke))
        return UiDrawPrimitive.Mesh(path.tessellateStrokeAa(DrawStroke(stroke.dp, join = StrokeJoin.Round), Ink))
    }

    @Test
    fun theAnalyticBorderIsAtLeastAsCloseToTheTrueBandAsTheTessellatedRing() {
        for (radius in listOf(2f, 4f, 6f, 8f, 12f)) {
            for (stroke in listOf(1f, 1.5f, 2f)) {
                for (offset in listOf(0f, 0.5f)) {
                    val x = 8f + offset
                    val y = 8f + offset
                    val w = 44f
                    val h = 30f
                    val old = listOf(tessellated(x, y, w, h, radius, stroke)).rasterize(size, size, background = Paper)
                    val new = listOf(UiDrawPrimitive.RoundedQuad(x, y, w, h, Ink, radius, strokeWidth = stroke)).rasterize(size, size, background = Paper)
                    val oldError = error(old, x, y, w, h, radius, stroke)
                    val newError = error(new, x, y, w, h, radius, stroke)
                    println("PERF border r=$radius stroke=$stroke offset=$offset error old=${"%.3f".format(oldError)} new=${"%.3f".format(newError)}")
                    assertTrue(newError <= oldError + TOLERANCE, "r=$radius stroke=$stroke offset=$offset: analytic $newError vs tessellated $oldError")
                }
            }
        }
    }

    private companion object {
        const val SAMPLES = 8
        const val TOLERANCE = 0.01
        val Ink = Color(0f, 0f, 0f, 1f)
        val Paper = Color(1f, 1f, 1f, 1f)
    }
}
