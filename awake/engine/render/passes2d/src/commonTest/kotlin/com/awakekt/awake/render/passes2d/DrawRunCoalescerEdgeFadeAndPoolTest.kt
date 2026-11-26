/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.EdgeFade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DrawRunCoalescerEdgeFadeAndPoolTest {
    @Test
    fun edgeFadeEmitsSingleBatchedDrawWithSmoothPerVertexAlpha() {
        val tex = Any()
        val fade = EdgeFade(left = 0.25f, top = 0f, right = 0.25f, bottom = 0.65f)
        val staged = DrawRunCoalescer.coalesce(
            listOf(DrawCommand.Texture(0f, 0f, 100f, 80f, tex, edgeFade = fade)),
        )
        val run = assertIs<StagedDrawRun.TextureRun>(staged.single())
        assertEquals(1, run.primitives.size, "edge-faded texture stays a single draw call")
        val prim = run.primitives.single()
        val stride = VertexFormats2D.TEXTURE_FLOATS_PER_VERTEX
        val vertexCount = prim.vertices.size / stride
        assertTrue(vertexCount > VertexFormats2D.VERTICES_PER_QUAD, "faded edges subdivide into smooth vertex grid")
        assertTrue(prim.indices.size > VertexFormats2D.INDICES_PER_QUAD)

        // Vertex alpha channel is at offset 11 (x, y, u, v, r0, r1, r2, r3, cr, cg, cb, ca, ...).
        val alphaOffset = 11
        var sawZeroEdge = false
        var sawSolidInterior = false
        var sawSmoothMid = false
        for (v in 0 until vertexCount) {
            val x = prim.vertices[v * stride]
            val y = prim.vertices[v * stride + 1]
            val a = prim.vertices[v * stride + alphaOffset]
            if (x == 0f || x == 100f || y == 80f) {
                assertEquals(0f, a, 1e-4f, "outer faded edge at ($x,$y) must reach alpha 0")
                sawZeroEdge = true
            } else if (x in 25f..75f && y <= 28f) {
                assertEquals(1f, a, 1e-4f, "solid upper interior at ($x,$y) must stay alpha 1")
                sawSolidInterior = true
            } else if (a in 0.05f..0.95f) {
                sawSmoothMid = true
            }
        }
        assertTrue(sawZeroEdge && sawSolidInterior && sawSmoothMid)

        // EdgeFade.None keeps the 4-vertex fast quad path unchanged.
        val plain = assertIs<StagedDrawRun.TextureRun>(
            DrawRunCoalescer.coalesce(listOf(DrawCommand.Texture(0f, 0f, 100f, 80f, tex, edgeFade = EdgeFade.None))).single(),
        ).primitives.single()
        assertEquals(VertexFormats2D.VERTICES_PER_QUAD * stride, plain.vertices.size)
    }

    @Test
    fun dynamicTextureRunReusesPooledArraysAndMarksRequiresUpload() {
        val tex = Any()
        val retained = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()

        fun stageAt(x: Float): TexturedDrawRun {
            val runs = DrawRunCoalescer.coalesce(
                primitives = listOf(DrawCommand.Texture(x, 0f, 64f, 64f, tex)),
                retained = retained,
                pool = pool,
            )
            return assertIs<StagedDrawRun.TextureRun>(runs.single()).primitives.single()
        }

        // First sight builds fresh and stores in retained cache; subsequent dynamic frames alternate pool generations.
        val frame0 = stageAt(0f)
        assertFalse(frame0.requiresUpload, "first sight is stored immutable for potential retention")
        val frame1 = stageAt(1f)
        val frame2 = stageAt(2f)
        val frame3 = stageAt(3f)
        assertTrue(frame1.requiresUpload, "dynamic texture run must force GPU upload")
        assertTrue(frame2.requiresUpload)
        assertTrue(frame3.requiresUpload)
        assertNotSame(frame1.vertices, frame2.vertices, "adjacent frames alternate generations")
        assertSame(frame1.vertices, frame3.vertices, "generation N+2 reuses generation N buffer with zero allocation")
        assertSame(frame1.indices, frame3.indices)
        assertEquals(0, pool.resizeCount, "steady dynamic texture size causes zero pool resizes")
    }
}
