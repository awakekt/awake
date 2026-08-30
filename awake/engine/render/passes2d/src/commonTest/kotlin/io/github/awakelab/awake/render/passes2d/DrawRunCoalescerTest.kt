/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes2d

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.graphics2d.UiLinearGradient
import io.github.awakelab.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawRunCoalescerTest {

    @Test
    fun testCoalesceSimpleQuads() {
        val primitives = listOf(
            DrawCommand.Quad(0f, 0f, 100f, 50f, Color(1f, 1f, 1f, 1f)),
            DrawCommand.Quad(100f, 0f, 100f, 50f, Color(1f, 0f, 0f, 1f)),
        )

        val runs = DrawRunCoalescer.coalesce(primitives)
        assertEquals(1, runs.size)
        assertTrue(runs[0] is StagedDrawRun.QuadRun)

        val quadRun = runs[0] as StagedDrawRun.QuadRun
        // 2 quads * 4 vertices * 10 floats = 80 floats
        assertEquals(80, quadRun.vertices.size)
        // 2 quads * 6 indices = 12 indices
        assertEquals(12, quadRun.indices.size)
    }

    @Test
    fun testCoalesceInterleavedPrimitivesAndClips() {
        val primitives = listOf(
            DrawCommand.Quad(0f, 0f, 100f, 50f, Color(1f, 1f, 1f, 1f)),
            DrawCommand.ClipPush(Rectangle(10f, 10f, 80f, 30f)),
            DrawCommand.RoundedQuad(10f, 10f, 80f, 30f, Color(0f, 0f, 1f, 1f), 4f, 1f),
            DrawCommand.ClipPop(Rectangle(0f, 0f, 200f, 200f)),
            DrawCommand.Glyph(20f, 20f, 16f, 16f, 0f, 0f, 0.5f, 0.5f, Color(1f, 1f, 1f, 1f)),
        )

        val runs = DrawRunCoalescer.coalesce(primitives)
        assertEquals(5, runs.size)
        assertTrue(runs[0] is StagedDrawRun.QuadRun)
        assertTrue(runs[1] is StagedDrawRun.ClipRun)
        assertTrue(runs[2] is StagedDrawRun.RoundedQuadRun)
        assertTrue(runs[3] is StagedDrawRun.ClipRun)
        assertTrue(runs[4] is StagedDrawRun.GlyphRun)

        val clip1 = runs[1] as StagedDrawRun.ClipRun
        assertEquals(Rectangle(10f, 10f, 80f, 30f), clip1.rect)

        val clip2 = runs[3] as StagedDrawRun.ClipRun
        assertEquals(Rectangle(0f, 0f, 200f, 200f), clip2.rect)
    }

    @Test
    fun testShadowQuadCoalescing() {
        val primitives = listOf(
            DrawCommand.ShadowQuad(
                x = 10f,
                y = 10f,
                w = 100f,
                h = 50f,
                radius = 8f,
                color = Color(0f, 0f, 0f, 1f),
                blurRadius = 4f,
                spread = 2f,
                offsetX = 0f,
                offsetY = 4f,
            ),
        )

        val runs = DrawRunCoalescer.coalesce(primitives)
        assertEquals(1, runs.size)
        assertTrue(runs[0] is StagedDrawRun.RoundedQuadRun)

        val shadowRun = runs[0] as StagedDrawRun.RoundedQuadRun
        // 1 quad * 4 vertices * 16 floats = 64 floats
        assertEquals(64, shadowRun.vertices.size)
        assertEquals(6, shadowRun.indices.size)
    }

    @Test
    fun shadowGradientWritesEachCornerToTheRoundedQuadVertices() {
        val red = Color(1f, 0f, 0f, 1f)
        val green = Color(0f, 1f, 0f, 1f)
        val blue = Color(0f, 0f, 1f, 1f)
        val white = Color(1f, 1f, 1f, 1f)
        val run = DrawRunCoalescer.coalesce(
            listOf(
                DrawCommand.ShadowQuad(
                    x = 0f, y = 0f, w = 20f, h = 20f, radius = 2f,
                    offsetX = 0f, offsetY = 0f, blurRadius = 2f, spread = 0f, color = red,
                    gradient = UiLinearGradient(red, green, blue, white),
                ),
            ),
        ).single() as StagedDrawRun.RoundedQuadRun

        // Rounded vertices store RGBA at offsets 8..11 of each 16-float vertex.
        assertEquals(listOf(1f, 0f, 0f, 1f), run.vertices.slice(8..11))
        assertEquals(listOf(0f, 1f, 0f, 1f), run.vertices.slice(24..27))
        assertEquals(listOf(0f, 0f, 1f, 1f), run.vertices.slice(40..43))
        assertEquals(listOf(1f, 1f, 1f, 1f), run.vertices.slice(56..59))
    }

    @Test
    fun testChunkingExceedingMaxQuads() {
        val primitives = (0 until 10).map { i ->
            DrawCommand.Quad(i * 10f, 0f, 10f, 10f, Color(1f, 1f, 1f, 1f))
        }

        // maxQuadsPerRun = 3 -> 10 quads should produce 4 chunks (3, 3, 3, 1)
        val runs = DrawRunCoalescer.coalesce(primitives, maxQuadsPerRun = 3)
        assertEquals(4, runs.size)
        assertTrue(runs.all { it is StagedDrawRun.QuadRun })

        val q0 = runs[0] as StagedDrawRun.QuadRun
        assertEquals(3 * 4 * 10, q0.vertices.size)

        val q3 = runs[3] as StagedDrawRun.QuadRun
        assertEquals(1 * 4 * 10, q3.vertices.size)
    }
}
