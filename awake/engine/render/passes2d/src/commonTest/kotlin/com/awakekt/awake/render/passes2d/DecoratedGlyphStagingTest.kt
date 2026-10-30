/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decorated glyphs (#1061) stage in their own layout and run, one quad a glyph, grown to their decoration. */
class DecoratedGlyphStagingTest {
    private val decoration = GlyphDecoration(
        fieldOffsetX = -10f,
        fieldOffsetY = -10f,
        fieldW = 40f,
        fieldH = 40f,
        fieldU0 = 0.5f,
        fieldV0 = 0.5f,
        fieldU1 = 0.9f,
        fieldV1 = 0.9f,
        outlineColor = Color.Black,
        outlineWidth = 2f,
        shadowColor = Color(0f, 0f, 0f, 0.5f),
        shadowOffsetX = 3f,
        shadowOffsetY = 1f,
        shadowBlur = 1f,
    )
    private val glyph = UiDrawPrimitive.Glyph(10f, 10f, 20f, 20f, 0.1f, 0.1f, 0.3f, 0.3f, Color.White, decoration = decoration)

    @Test
    fun theLayoutIsTheVertexFormats() {
        assertEquals(VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX, VertexFormats2D.DecoratedGlyph.strideBytes / Float.SIZE_BYTES)
        assertEquals(DrawPipelineKind.DecoratedGlyph.floatsPerVertex, VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX)
    }

    @Test
    fun aDecoratedGlyphIsOneQuadGrownByItsReachInItsOwnRun() {
        val plain = glyph.copy(decoration = null)
        val runs = DrawRunCoalescer.coalesce(listOf(glyph, glyph.copy(x = 40f), plain)).filterIsInstance<StagedDrawRun.GlyphRun>()
        assertEquals(listOf(true, false), runs.map { it.decorated }, "a plain glyph ends the decorated run")
        val run = runs.first()
        assertEquals(2 * VertexFormats2D.INDICES_PER_QUAD, run.indices.size)
        val stride = VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX
        // Reach: the shadow's offset and blur, 4 px, past the outline's 2, plus a pixel of edge; within the 40 px tile.
        val reach = 5f
        assertEquals(10f - reach, run.vertices[0])
        assertEquals(10f - reach, run.vertices[1])
        assertEquals(30f + reach, run.vertices[2 * stride])
        assertEquals(30f + reach, run.vertices[2 * stride + 1])
        // The atlas uv runs past its rect over the grown quad, and the rect itself is carried as it is.
        assertEquals(0.1f - reach / 20f * 0.2f, run.vertices[2], 1e-6f)
        assertEquals(listOf(0.1f, 0.1f, 0.3f, 0.3f), run.vertices.slice(4..7))
        // The effect in field uv: 0.01 a pixel across the 40 px, 0.4-wide tile.
        assertEquals(listOf(0.02f, 0.01f, 0.03f, 0.01f), run.vertices.slice(26..29).map { (it * 1000f).toInt() / 1000f })
    }

    @Test
    fun theDrawnQuadStaysInsideItsEffectTile() {
        val bounds = FloatArray(4)
        decoration.copy(outlineWidth = 30f).drawnBounds(glyph.x, glyph.y, glyph.w, glyph.h, bounds)
        assertTrue(bounds[0] >= 0f && bounds[1] >= 0f && bounds[2] <= 40f && bounds[3] <= 40f, bounds.toList().toString())
    }
}
