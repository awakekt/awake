/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #1545: a rounded border is one analytic quad, two triangles, where a stroked path was about a thousand. */
class AnalyticBorderStagingTest {
    private val ink = Color(0.5f, 0.6f, 0.7f, 1f)

    private fun triangles(runs: List<StagedDrawRun>): Long = runs.sumOf { run ->
        when (run) {
            is StagedDrawRun.QuadRun -> run.indices.size / 3L
            is StagedDrawRun.RoundedQuadRun -> run.indices.size / 3L
            is StagedDrawRun.GlyphRun -> run.indices.size / 3L
            else -> 0L
        }
    }

    @Test
    fun theFixturesFirstFrameDropsMostOfItsTriangles() {
        val paths = triangles(DrawRunCoalescer.coalesce(VectorHudFixture.frame(analytic = false)))
        val analytic = triangles(DrawRunCoalescer.coalesce(VectorHudFixture.frame()))
        println("PERF analyticBorders fixture first-frame triangles paths=$paths analytic=$analytic")
        assertTrue(analytic * 4 < paths, "analytic borders should cut the HUD's triangles at least 4x: $paths -> $analytic")
    }

    @Test
    fun aBorderIsTwoTrianglesCarryingItsStrokeWidth() {
        val run = DrawRunCoalescer.coalesce(listOf(DrawCommand.RoundedQuad(10f, 10f, 48f, 48f, ink, 6f, strokeWidth = 1.5f)))
            .single() as StagedDrawRun.RoundedQuadRun
        assertEquals(2, run.indices.size / 3)
        val stride = VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX
        assertEquals(List(4) { 1.5f }, List(4) { run.vertices[it * stride + stride - 1] })
    }

    @Test
    fun underAPathClipABorderIsARingNotAFill() {
        val clip = Rectangle(0f, 0f, 100f, 100f)
        val clipPath = DrawShape.RoundedRectangle(8f.dp).toPath(clip)
        val runs = DrawRunCoalescer.coalesce(
            listOf(
                DrawCommand.ClipPathPush(clipPath, clip),
                DrawCommand.RoundedQuad(20f, 20f, 40f, 40f, ink, 6f, strokeWidth = 1f),
                DrawCommand.ClipPop(clip),
            ),
        )
        val ring = runs.filterIsInstance<StagedDrawRun.QuadRun>().single()
        val stride = VertexFormats2D.FLOATS_PER_VERTEX
        val points = (0 until ring.vertices.size / stride).map { ring.vertices[it * stride] to ring.vertices[it * stride + 1] }
        // A fill would place vertices across the middle; a 1 px ring keeps them within 2 px of its edge.
        assertTrue(points.none { (x, y) -> x in 23f..57f && y in 23f..57f }, "the clipped border filled its middle")
    }
}
