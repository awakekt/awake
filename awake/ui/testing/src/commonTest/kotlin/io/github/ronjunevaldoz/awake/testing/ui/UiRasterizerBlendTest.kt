// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.testing.ui

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.testing.PixelMap
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.graphics2d.UiLinearGradient
import io.github.ronjunevaldoz.awake.core.graphics2d.uiPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every primitive used to composite with its own inlined blend. Quads, gradients, and triangles
 * overwrote the destination and parked the source alpha in the alpha channel, so a 10%-alpha
 * fill rasterized fully saturated -- and since this rasterizer backs the preview and parity
 * snapshots, that made them wrong oracles. These pin all of them to the one [PixelMap.blend].
 */
class UiRasterizerBlendTest {
    private val white = Color(1f, 1f, 1f, 1f)
    private val tenPercentRed = Color(1f, 0f, 0f, 0.1f)

    private fun centerPixel(primitive: UiDrawPrimitive): List<Int> {
        val pixels = listOf(primitive).rasterize(width = 20, height = 20, background = white)
        return PixelMap(20, 20, pixels).sample(10, 10).let { listOf(it.r, it.g, it.b, it.a) }
    }

    @Test
    fun tenPercentRedOverWhiteBlendsToPink() {
        // 0.1 red over opaque white: 255*0.1 + 255*0.9 = 255 red, 0*0.1 + 255*0.9 = 229 green/blue.
        val expected = listOf(255, 229, 229, 255)
        assertEquals(expected, centerPixel(quad(tenPercentRed)), "Quad")
        assertEquals(expected, centerPixel(roundedQuad(tenPercentRed)), "RoundedQuad")
        assertEquals(expected, centerPixel(gradient(tenPercentRed)), "LinearGradient")
    }

    @Test
    fun opaqueFillIsUnchangedByBlending() {
        val red = Color(1f, 0f, 0f, 1f)
        val expected = listOf(255, 0, 0, 255)
        assertEquals(expected, centerPixel(quad(red)), "Quad")
        assertEquals(expected, centerPixel(roundedQuad(red)), "RoundedQuad")
    }

    @Test
    fun fullyTransparentFillLeavesDestinationAlone() {
        val expected = listOf(255, 255, 255, 255)
        assertEquals(expected, centerPixel(quad(Color(1f, 0f, 0f, 0f))), "Quad")
        assertEquals(expected, centerPixel(gradient(Color(1f, 0f, 0f, 0f))), "LinearGradient")
    }

    @Test
    fun stackedTranslucentFillsAccumulate() {
        // Two 0.1 passes must land strictly darker than one and lighter than opaque. The old
        // overwrite path produced identical pixels no matter how many passes ran.
        val once = centerPixel(quad(tenPercentRed))[1]
        val twice = listOf(quad(tenPercentRed), quad(tenPercentRed))
            .rasterize(width = 20, height = 20, background = white)
            .let { PixelMap(20, 20, it).sample(10, 10).g }
        assertTrue(twice < once, "second pass must darken green: once=$once twice=$twice")
        assertTrue(twice > 0, "two 10% passes must not reach full saturation: twice=$twice")
    }

    @Test
    fun blendOverTransparentDestinationUnpremultiplies() {
        // The rounded-quad copy omitted the `/ outA`, which only shows up over a non-opaque
        // destination: red at 50% over nothing is still full-strength red, just half-covered.
        val map = PixelMap(1, 1)
        map.blend(0, 0, r = 255f, g = 0f, b = 0f, srcA = 0.5f)
        val sample = map.sample(0, 0)
        assertEquals(255, sample.r, "red must stay full-strength, not be halved toward black")
        // 127, not 128: channel conversion truncates (0.5 * 255 = 127.5), as every path here
        // always has. Pinned so a later switch to rounding is a deliberate baseline change.
        assertEquals(127, sample.a, "coverage belongs in alpha")
    }

    @Test
    fun translucentFilledPathHasNoDoubleBlendedSeam() {
        // fillTriangleMesh fills every triangle of a tessellated path with the SAME color, and
        // the inside test accepts a sample sitting exactly on an edge (`>= 0 || <= 0`). Under
        // the old overwrite that double coverage was invisible; under a real blend a pixel
        // covered by two triangles composites twice and darkens. A uniformly-colored shape must
        // still rasterize to uniform pixels.
        val square = uiPath {
            moveTo(2f, 2f)
            lineTo(18f, 2f)
            lineTo(18f, 18f)
            lineTo(2f, 18f)
            close()
        }
        val pixels = listOf(UiDrawPrimitive.FilledPath(square, tenPercentRed))
            .rasterize(width = 20, height = 20, background = white)
        val map = PixelMap(20, 20, pixels)
        val interior = (5..14).flatMap { y -> (5..14).map { x -> map.sample(x, y) } }
        val distinct = interior.distinct()
        assertEquals(1, distinct.size, "interior must be uniform, found ${distinct.size} shades: $distinct")
    }

    @Test
    fun aTranslucentFillDoesNotShowOrTintAnOpaqueBorder() {
        // A translucent fill must not read as the border color, and must not tint an opaque
        // border drawn over it. ShapePainter used to fake a rounded border by filling a
        // border-colored rect and insetting the fill on top, which only hides that rect while
        // the fill is opaque -- at 10% the whole surface read as solid border.
        val border = Color(0f, 0f, 1f, 1f)
        val pixels = listOf(
            UiDrawPrimitive.Quad(x = 4f, y = 4f, w = 12f, h = 12f, color = tenPercentRed),
            UiDrawPrimitive.Quad(x = 4f, y = 4f, w = 12f, h = 1f, color = border),
        ).rasterize(width = 20, height = 20, background = white)
        val map = PixelMap(20, 20, pixels)
        assertEquals(listOf(0, 0, 255, 255), map.sample(10, 4).let { listOf(it.r, it.g, it.b, it.a) }, "border stays pure")
        assertEquals(listOf(255, 229, 229, 255), map.sample(10, 10).let { listOf(it.r, it.g, it.b, it.a) }, "interior is the tint")
    }

    private fun quad(color: Color) = UiDrawPrimitive.Quad(x = 0f, y = 0f, w = 20f, h = 20f, color = color)

    private fun roundedQuad(color: Color) =
        UiDrawPrimitive.RoundedQuad(x = 0f, y = 0f, w = 20f, h = 20f, color = color, radius = 4f)

    private fun gradient(color: Color) = UiDrawPrimitive.GradientQuad(
        x = 0f,
        y = 0f,
        w = 20f,
        h = 20f,
        gradient = UiLinearGradient(
            topLeft = color,
            topRight = color,
            bottomRight = color,
            bottomLeft = color,
        ),
    )
}
