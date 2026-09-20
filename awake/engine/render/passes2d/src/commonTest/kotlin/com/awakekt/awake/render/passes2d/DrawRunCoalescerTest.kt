/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawRunCoalescerTest {

    @Test
    fun aMeshInsideASafeInteriorSkipsPathClipping() {
        val mesh = ColoredTriangleMesh(
            vertices = listOf(
                ColoredVertex(DrawPoint(0f, 0f), Color(1f, 1f, 1f, 1f)),
                ColoredVertex(DrawPoint(10f, 0f), Color(1f, 1f, 1f, 1f)),
                ColoredVertex(DrawPoint(10f, 10f), Color(1f, 1f, 1f, 1f)),
                ColoredVertex(DrawPoint(0f, 10f), Color(1f, 1f, 1f, 1f)),
            ),
            indices = intArrayOf(0, 1, 2, 2, 3, 0),
        )
        val clip = drawPath {
            moveTo(-100f, -100f)
            lineTo(100f, -100f)
            lineTo(100f, 100f)
            lineTo(-100f, 100f)
            close()
        }
        val safeInterior = Rectangle(-50f, -50f, 100f, 100f)
        val bounds = Rectangle(-100f, -100f, 200f, 200f)

        val withSafeInterior = DrawRunCoalescer.coalesce(
            listOf(
                DrawCommand.ClipPathPush(clip, bounds, safeInterior),
                DrawCommand.Mesh(mesh),
                DrawCommand.ClipPop(bounds),
            ),
        ).filterIsInstance<StagedDrawRun.QuadRun>().single()
        val withoutSafeInterior = DrawRunCoalescer.coalesce(
            listOf(
                DrawCommand.ClipPathPush(clip, bounds),
                DrawCommand.Mesh(mesh),
                DrawCommand.ClipPop(bounds),
            ),
        ).filterIsInstance<StagedDrawRun.QuadRun>().single()

        // Safe-interior placement preserves four vertices; exact clipping triangulates the
        // clipped quad into six. Each staged vertex has the same stride in both runs.
        assertEquals(withoutSafeInterior.vertices.size * 2 / 3, withSafeInterior.vertices.size)
    }

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

    /** Reproduces the shared local-mesh cache used by clipped rounded quads
     * ([localRoundedRectMesh]/[placedAt] in [DrawRunCoalescer]): two quads with the same
     * radius/w/h but different position and color must not corrupt each other's placed output,
     * and the AA fringe's outer ring must stay fully transparent after recoloring. */
    @Test
    fun testClippedRoundedQuadsWithSharedShapeCacheStayIndependent() {
        val coveringClip = drawPath {
            moveTo(-1000f, -1000f)
            lineTo(2000f, -1000f)
            lineTo(2000f, 2000f)
            lineTo(-1000f, 2000f)
            close()
        }
        val safeInterior = Rectangle(-1000f, -1000f, 3000f, 3000f)
        val red = Color(1f, 0f, 0f, 1f)
        val blue = Color(0f, 0f, 1f, 1f)

        val primitives = listOf(
            DrawCommand.ClipPathPush(coveringClip, Rectangle(-1000f, -1000f, 3000f, 3000f), safeInterior),
            DrawCommand.RoundedQuad(0f, 0f, 20f, 20f, red, 4f, 1f),
            DrawCommand.RoundedQuad(50f, 30f, 20f, 20f, blue, 4f, 1f),
            DrawCommand.ClipPop(Rectangle(0f, 0f, 200f, 200f)),
        )

        val runs = DrawRunCoalescer.coalesce(primitives)
        val quadRuns = runs.filterIsInstance<StagedDrawRun.QuadRun>()
        assertEquals(1, quadRuns.size)
        val vertices = quadRuns.single().vertices

        // VertexFormats2D writes pos(2) + color(4) + transform(4) = 10 floats/vertex; each quad's
        // tessellation contributes the same vertex count since both share (radius=4, w=20, h=20).
        assertEquals(0, vertices.size % 20)
        val perQuad = vertices.size / 2
        val firstColors = (0 until perQuad step 10).map { vertices.slice(it + 2..it + 5) }
        val secondColors = (perQuad until vertices.size step 10).map { vertices.slice(it + 2..it + 5) }

        // Every vertex is either the quad's own baked color, or transparent (AA fringe) -- never
        // the other quad's color, which is what a shared, mistakenly-mutated cache entry would leak.
        assertTrue(firstColors.all { it == listOf(1f, 0f, 0f, 1f) || it[3] == 0f })
        assertTrue(secondColors.all { it == listOf(0f, 0f, 1f, 1f) || it[3] == 0f })
        assertTrue(firstColors.any { it == listOf(1f, 0f, 0f, 1f) })
        assertTrue(secondColors.any { it == listOf(0f, 0f, 1f, 1f) })

        // Second quad's fill vertex positions are the first quad's plus the (50, 30) offset.
        val firstFillX = (0 until perQuad step 10).first { vertices[it + 5] == 1f }.let { vertices[it] }
        val secondFillX = (perQuad until vertices.size step 10).first { vertices[it + 5] == 1f }.let { vertices[it] }
        assertEquals(firstFillX + 50f, secondFillX)
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

    @Test
    fun retainedCacheReusesUnchangedStagedSpan() {
        val cache = RetainedDrawRunCache()
        val firstFrame = listOf(
            DrawCommand.Quad(0f, 0f, 100f, 50f, Color(1f, 1f, 1f, 1f)),
        )

        val firstRuns = DrawRunCoalescer.coalesce(firstFrame, retained = cache)
        val secondRuns = DrawRunCoalescer.coalesce(
            firstFrame.map { it.copy() },
            retained = cache,
        )

        assertEquals(1, secondRuns.size)
        assertTrue(firstRuns.single() === secondRuns.single())
    }

    @Test
    fun retainedMeshKeySkipsGeometryComparisonAndInvalidatesWhenReplaced() {
        val cache = RetainedDrawRunCache()
        val mesh = ColoredTriangleMesh(
            vertices = listOf(
                ColoredVertex(DrawPoint(0f, 0f), Color(1f, 1f, 1f, 1f)),
                ColoredVertex(DrawPoint(10f, 0f), Color(1f, 1f, 1f, 1f)),
                ColoredVertex(DrawPoint(10f, 10f), Color(1f, 1f, 1f, 1f)),
            ),
            indices = intArrayOf(0, 1, 2),
        )
        val key = Any()
        fun command(geometry: ColoredTriangleMesh, geometryKey: Any) =
            DrawCommand.Mesh(geometry).also { it.retainedGeometryKey = geometryKey }

        val firstRuns = DrawRunCoalescer.coalesce(listOf(command(mesh, key)), retained = cache)
        val unchangedRuns = DrawRunCoalescer.coalesce(listOf(command(mesh, key)), retained = cache)
        assertTrue(firstRuns.single() === unchangedRuns.single())

        val changedMesh = mesh.copy(
            vertices = mesh.vertices.mapIndexed { index, vertex ->
                if (index == 0) vertex.copy(position = DrawPoint(-1f, 0f)) else vertex
            },
        )
        val changedRuns = DrawRunCoalescer.coalesce(
            listOf(command(changedMesh, Any())),
            retained = cache,
        )
        assertTrue(firstRuns.single() !== changedRuns.single())
    }

    @Test
    fun retainedCacheMissesWhenGeometryChanges() {
        val cache = RetainedDrawRunCache()
        val firstRuns = DrawRunCoalescer.coalesce(
            listOf(DrawCommand.Quad(0f, 0f, 100f, 50f, Color(1f, 1f, 1f, 1f))),
            retained = cache,
        )
        val changedRuns = DrawRunCoalescer.coalesce(
            listOf(DrawCommand.Quad(1f, 0f, 100f, 50f, Color(1f, 1f, 1f, 1f))),
            retained = cache,
        )

        assertTrue(firstRuns.single() !== changedRuns.single())
        val changed = changedRuns.single() as StagedDrawRun.QuadRun
        assertEquals(1f, changed.vertices[0])
    }

    @Test
    fun retainedCacheReusesGeometryInsideRectAndPathClips() {
        val cache = RetainedDrawRunCache()
        val rectClipped = listOf(
            DrawCommand.ClipPush(Rectangle(0f, 0f, 100f, 100f)),
            DrawCommand.Quad(10f, 10f, 20f, 20f, Color(1f, 1f, 1f, 1f)),
            DrawCommand.ClipPop(Rectangle(0f, 0f, 200f, 200f)),
        )
        val firstRectRuns = DrawRunCoalescer.coalesce(rectClipped, retained = cache)
        val secondRectRuns = DrawRunCoalescer.coalesce(rectClipped, retained = cache)
        assertTrue(firstRectRuns[1] === secondRectRuns[1])

        val path = drawPath {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            lineTo(0f, 100f)
            close()
        }
        val pathClipped = listOf(
            DrawCommand.ClipPathPush(path, Rectangle(0f, 0f, 100f, 100f)),
            DrawCommand.Quad(10f, 10f, 20f, 20f, Color(1f, 1f, 1f, 1f)),
            DrawCommand.ClipPop(Rectangle(0f, 0f, 200f, 200f)),
        )
        val firstPathRuns = DrawRunCoalescer.coalesce(pathClipped, retained = cache)
        val secondPathRuns = DrawRunCoalescer.coalesce(pathClipped, retained = cache)
        assertTrue(firstPathRuns[1] === secondPathRuns[1])

        val smallerPath = drawPath {
            moveTo(0f, 0f)
            lineTo(15f, 0f)
            lineTo(15f, 100f)
            lineTo(0f, 100f)
            close()
        }
        val changedPathRuns = DrawRunCoalescer.coalesce(
            listOf(
                DrawCommand.ClipPathPush(smallerPath, Rectangle(0f, 0f, 100f, 100f)),
                DrawCommand.Quad(10f, 10f, 20f, 20f, Color(1f, 1f, 1f, 1f)),
                DrawCommand.ClipPop(Rectangle(0f, 0f, 200f, 200f)),
            ),
            retained = cache,
        )
        assertTrue(firstPathRuns[1] !== changedPathRuns[1])
        val firstGeometry = (firstPathRuns[1] as StagedDrawRun.QuadRun).vertices
        val changedGeometry = (changedPathRuns[1] as StagedDrawRun.QuadRun).vertices
        assertTrue(!firstGeometry.contentEquals(changedGeometry))
    }

    /**
     * A stroked path is tessellated once, however many frames draw it.
     *
     * Stroking is the most expensive tessellation here -- flatten every curve, offset both sides of
     * every contour with per-join arc trig, scanline-fill the ring -- and a UI redraws the same
     * icons every frame. The frame ratchet's scene went 41,016 to 56,184 vertices when the icon set
     * became real outlines; without a cache every one of those was recomputed per frame.
     *
     * Counts, not milliseconds, for the reason `UiFrameCostRatchetTest` gives: wall-clock is noisy
     * on a developer's machine and through a software rasteriser in CI.
     */
    @Test
    fun aRepeatedStrokedPathIsTessellatedOnce() {
        val primitives = listOf(strokedCircle(x = 10f, y = 10f))
        StrokedPathMeshCache.reset()

        DrawRunCoalescer.coalesce(primitives)
        val afterFirst = StrokedPathMeshCache.tessellations
        DrawRunCoalescer.coalesce(primitives)
        DrawRunCoalescer.coalesce(primitives)

        assertEquals(1, afterFirst, "the first frame must actually tessellate")
        assertEquals(
            afterFirst,
            StrokedPathMeshCache.tessellations,
            "redrawing the same stroked path re-tessellated it",
        )
    }

    /**
     * Different geometry is still tessellated.
     *
     * The pair matters: a cache that never misses would pass the test above by returning one
     * mesh for everything, and every icon would be drawn as whichever one was cached first.
     */
    @Test
    fun aDifferentStrokedPathIsItsOwnTessellation() {
        StrokedPathMeshCache.reset()

        DrawRunCoalescer.coalesce(listOf(strokedCircle(x = 10f, y = 10f)))
        DrawRunCoalescer.coalesce(listOf(strokedCircle(x = 40f, y = 10f)))

        assertEquals(
            2,
            StrokedPathMeshCache.tessellations,
            "two different stroked paths shared one tessellation",
        )
    }

    /** A closed triangle at [x], [y] -- enough contour for the stroker to do real work. */
    private fun strokedCircle(x: Float, y: Float) = DrawCommand.StrokedPath(
        path = drawPath {
            moveTo(x, y)
            lineTo(x + 20f, y)
            lineTo(x + 10f, y + 20f)
            close()
        },
        stroke = DrawStroke(width = 2f.dp),
        color = Color(1f, 1f, 1f, 1f),
    )
}
