/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct tests for the geometry helpers that only had indirect coverage.
 *
 * Written after a refactor rewrote three of them and shipped: `offsetPolygon` lost its winding
 * awareness and outset where it should inset, the clip discarded its exact intersection and
 * re-derived the position by lerping, and the convex fan early-out was deleted. The first two were
 * invisible to every existing test until they compounded into an AA failure and a snapshot drift --
 * findable only by diffing against an older worktree.
 *
 * Each of these fails immediately on the corresponding mistake.
 */
class PathGeometryContractTest {

    private val square = listOf(
        DrawPoint(0f, 0f),
        DrawPoint(100f, 0f),
        DrawPoint(100f, 100f),
        DrawPoint(0f, 100f),
    )

    // --- offsetPolygon ------------------------------------------------------

    @Test
    fun aNegativeDistanceInsets() {
        // The exact failure that shipped: without `outwardSign` this outsets to -0.5..100.5, and
        // every anti-aliased fill silently loses its fringe.
        val inset = offsetPolygon(square, -0.5f)

        assertTrue(inset.all { it.x >= 0f && it.x <= 100f }, "outset instead of inset: $inset")
        assertEquals(0.5f, inset[0].x, 0.01f)
        assertEquals(99.5f, inset[1].x, 0.01f)
    }

    @Test
    fun aPositiveDistanceOutsets() {
        val outset = offsetPolygon(square, 0.5f)

        assertEquals(-0.5f, outset[0].x, 0.01f)
        assertEquals(100.5f, outset[1].x, 0.01f)
    }

    @Test
    fun windingDoesNotChangeWhichSideIsOut() {
        // A reversed contour describes the same shape. Insetting it must still shrink it.
        val reversed = square.asReversed()
        val inset = offsetPolygon(reversed, -0.5f)

        assertTrue(inset.all { it.x >= 0f && it.x <= 100f }, "winding flipped the offset: $inset")
    }

    @Test
    fun theOffsetIsClampedToTheShorterAdjacentEdge() {
        // Without the clamp a large inset on a small feature turns the polygon inside out.
        val sliver = listOf(DrawPoint(0f, 0f), DrawPoint(2f, 0f), DrawPoint(2f, 2f), DrawPoint(0f, 2f))
        val inset = offsetPolygon(sliver, -50f)

        assertTrue(inset.all { it.x in -0.01f..2.01f }, "offset escaped the polygon: $inset")
    }

    @Test
    fun aDegeneratePolygonIsReturnedUnchanged() {
        val line = listOf(DrawPoint(0f, 0f), DrawPoint(1f, 0f))

        assertEquals(line, offsetPolygon(line, -1f))
    }

    // --- fill dispatch ------------------------------------------------------

    @Test
    fun aConvexShapeWithNoHolesFansFromItsCentroid() {
        // Deleted once already. The centroid fan is n+1 points; scanline triangulation is more.
        val mesh = drawPath {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 10f)
            lineTo(0f, 10f)
            close()
        }.tessellateFill()

        assertEquals(5, mesh.points.size, "4 corners plus a centroid -- the fan path was skipped")
    }

    @Test
    fun aShapeWithHolesIsTriangulatedNotFanned() {
        val ring = drawPath {
            moveTo(0f, 0f)
            lineTo(30f, 0f)
            lineTo(30f, 30f)
            lineTo(0f, 30f)
            close()
            moveTo(10f, 10f)
            lineTo(10f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 10f)
            close()
        }.tessellateFill()

        assertTrue(ring.points.size > 5, "a holed shape cannot be a centroid fan")
    }

    @Test
    fun roundStrokeCapsExtendPastAnOpenPathEndpoints() {
        // A prior tessellator emitted one rectangular quad per segment, silently discarding
        // StrokeCap.Round and StrokeJoin.Round. Small Lucide icons consequently looked jagged.
        val mesh = drawPath {
            moveTo(2f, 8f)
            lineTo(10f, 8f)
        }.tessellateStroke(DrawStroke(width = 4f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round))

        assertEquals(0f, mesh.points.minOf { it.x }, 0.01f)
        assertEquals(12f, mesh.points.maxOf { it.x }, 0.01f)
    }

    @Test
    fun roundStrokeOutlineContainsBothLegsOfACheck() {
        val outline = drawPath {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }.strokeToFillPath(DrawStroke(width = 2f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round))

        assertTrue(outline.containsPoint(15f, 11f), "the descending check leg is outside the stroke outline")
        assertTrue(outline.containsPoint(7f, 14f), "the ascending check leg is outside the stroke outline")
    }

    @Test
    fun roundStrokeChevronHasASharpInsideJoin() {
        val outline = drawPath {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }.strokeToSvgFillPath(DrawStroke(width = 2f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val points = outline.flattenContours().single().points

        assertTrue(
            points.any { abs(it.x - 12f) < 0.01f && abs(it.y - 13.586f) < 0.01f },
            "the chevron's inside edges must meet at the SVG stroke intersection",
        )
    }

    @Test
    fun aStrokeWiderThanItsLoopLeavesNoHole() {
        // An icon's dot: a 0.75-radius circle stroked 1.5 wide, as the Heroicons ellipsis draws it,
        // wound both ways round.
        for (sweep in listOf(180f, -180f)) {
            val dot = drawPath {
                moveTo(12.75f, 12f)
                arcTo(11.25f, 11.25f, 12.75f, 12.75f, 0f, sweep)
                arcTo(11.25f, 11.25f, 12.75f, 12.75f, sweep, sweep)
                close()
            }.strokeToFillPath(DrawStroke(width = 1.5f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // The inner offset of so small a loop is a knot of reversed slivers, and its edges'
            // antialiasing fringe draws a hollow dot.
            assertEquals(1, dot.flattenContours().size, "the dot kept an inner ring (sweep $sweep)")
            assertTrue(dot.containsPoint(12f, 12f), "the dot's center is a hole (sweep $sweep)")
            assertTrue(dot.containsPoint(13.4f, 12f), "the dot is not as wide as its stroke (sweep $sweep)")
        }
    }

    // --- anti-aliased fill --------------------------------------------------

    @Test
    fun anAaFillHasBothOpaqueAndTransparentVertices() {
        // The fringe is the whole point. Losing it renders a hard edge and nothing errors.
        val mesh = drawPath {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            lineTo(0f, 100f)
            close()
        }.tessellateFillAa(Color.White, fringePx = 1f)

        assertTrue(mesh.vertices.any { it.color.a > 0.99f }, "no opaque interior")
        assertTrue(mesh.vertices.any { it.color.a < 0.01f }, "no transparent fringe")
    }

    @Test
    fun theOpaqueInteriorStopsShortOfTheTrueEdge() {
        val mesh = drawPath {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            lineTo(0f, 100f)
            close()
        }.tessellateFillAa(Color.White, fringePx = 1f)
        val leftmostOpaque = mesh.vertices.filter { it.color.a > 0.99f }.minOf { it.position.x }

        assertTrue(leftmostOpaque > 0.01f, "opaque interior reached the true edge at $leftmostOpaque")
    }

    @Test
    fun aThinStrokeRetainsOpaqueCoverageBetweenItsAaEdges() {
        val mesh = drawPath {
            moveTo(2f, 2f)
            lineTo(98f, 2f)
            lineTo(98f, 18f)
            lineTo(2f, 18f)
            close()
        }.tessellateStrokeAa(DrawStroke(width = 1f.dp), Color.White)

        assertTrue(
            mesh.vertices.any { it.color.a > 0.99f },
            "a one-pixel border lost its opaque coverage to overlapping AA fringes",
        )
        assertTrue(
            mesh.vertices.any { it.color.a < 0.01f },
            "a one-pixel border lost its anti-aliased fringe",
        )
        // The fringe is centred on the outline at x = 1.5, so the stroke is half covered exactly
        // there and neither shrunk nor grown before anti-aliasing.
        val opaqueLeft = mesh.vertices.filter { it.color.a > 0.99f }.minOf { it.position.x }
        val clearLeft = mesh.vertices.filter { it.color.a < 0.01f }.minOf { it.position.x }
        assertEquals(1.5f, (opaqueLeft + clearLeft) / 2f, 0.01f, "the half-covered edge moved off the outline")
        assertEquals(0.5f, opaqueLeft - clearLeft, 0.01f, "the fringe is not min(AA_FRINGE_PX, width / 2)")
    }

    // --- clipping -----------------------------------------------------------

    @Test
    fun aClippedVertexLandsExactlyOnTheClipEdge() {
        // The bug that drifted the panel snapshots: the exact intersection was computed and then
        // discarded in favour of a lerp along whichever axis dominated. On a diagonal the two
        // differ, and the vertex moves off the clip boundary.
        val triangle = TriangleMesh(
            points = listOf(DrawPoint(0f, 0f), DrawPoint(20f, 10f), DrawPoint(0f, 20f)),
            indices = intArrayOf(0, 1, 2),
        )
        val clip = drawPath {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 20f)
            lineTo(0f, 20f)
            close()
        }

        val clipped = triangle.clipToConvexPath(clip)

        assertTrue(clipped.points.isNotEmpty(), "everything was clipped away")
        assertTrue(
            clipped.points.all { it.x <= 10.001f },
            "a vertex escaped the clip edge: ${clipped.points.filter { it.x > 10.001f }}",
        )
        assertTrue(
            clipped.points.any { abs(it.x - 10f) < 0.001f },
            "no vertex landed on the clip edge -- the exact intersection was not used",
        )
    }

    @Test
    fun aClippedColouredVertexKeepsItsInterpolatedColour() {
        val red = Color(1f, 0f, 0f, 1f)
        val blue = Color(0f, 0f, 1f, 1f)
        val mesh = ColoredTriangleMesh(
            vertices = listOf(
                ColoredVertex(DrawPoint(0f, 0f), red),
                ColoredVertex(DrawPoint(20f, 0f), blue),
                ColoredVertex(DrawPoint(0f, 20f), red),
            ),
            indices = intArrayOf(0, 1, 2),
        )
        val clip = drawPath {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 20f)
            lineTo(0f, 20f)
            close()
        }

        val clipped = mesh.clipToConvexPaths(listOf(clip))
        val onEdge = clipped.vertices.filter { abs(it.position.x - 10f) < 0.001f }

        assertTrue(onEdge.isNotEmpty(), "nothing landed on the clip edge")
        assertTrue(
            onEdge.all { it.color.r in 0.4f..0.6f && it.color.b in 0.4f..0.6f },
            "colour was not interpolated at the cut: ${onEdge.map { it.color }}",
        )
    }

    @Test
    fun theAntiAliasedFringeIsTheRequestedWidthHoweverFinelyTheContourIsFlattened() {
        // offsetPolygon used to clamp every vertex to half its shorter adjacent edge, so the fringe
        // came out as a function of arc-segment length rather than the width asked for. A rounded
        // outline flattens into sub-pixel segments, so this measured 0.003 px where 0.5 was
        // requested -- every curve in the engine shipped effectively unantialiased. Asserted on a
        // rounded shape specifically: a long-edged rectangle is exactly the case the old clamp
        // did not bite on, so it would pass either way.
        val stroke = DrawStroke(width = 1f.dp, join = StrokeJoin.Round)
        val path = DrawShape.RoundedRectangle(8f.dp).toPath(Rectangle(0.5f, 0.5f, 39f, 39f))
        val outlineMinX = path.strokeToFillPath(stroke).flattenContours()
            .flatMap { contour -> contour.points.map { it.x } }
            .min()

        val meshMinX = path.tessellateStrokeAa(stroke, Color(1f, 1f, 1f, 1f)).vertices.minOf { it.position.x }

        // fringe = min(AA_FRINGE_PX, strokeWidth / 2) = 0.5 here, centred on the outline: half of it
        // reaches outside.
        assertEquals(outlineMinX - 0.25f, meshMinX, 0.001f)
    }

    @Test
    fun aStrokeOutlinesFringeStraddlesEveryVertexByHalfAPixel() {
        // Lucide's chevron-down at 32 px, outlined the way VectorPainter outlines it. A centred
        // 1 px fringe moves each vertex 0.5 px in and 0.5 px out, up to 0.707 at the mitred inner
        // corner. Two bugs broke that, and this measured both: insetting a convex corner was capped
        // at half its adjacent edge, 0.07 px on a round cap's 0.14 px segments, so the fringe sat
        // outside the stroke and made it 7-24% heavier than Chromium's; and the outline closes on a
        // point a float epsilon from its first, whose sliver edge set the first vertex to 1.31 px
        // in and 0 px out, so the chevron's first arm drew lighter than its second.
        val s = 4f / 3f
        val path = DrawPath.build {
            moveTo(6f * s, 9f * s)
            lineTo(12f * s, 15f * s)
            lineTo(18f * s, 9f * s)
        }
        val ring = path.strokeToSvgFillPath(DrawStroke(width = (2f * s).dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
            .flattenContours().single().points

        for ((label, distance) in listOf("inset" to -0.5f, "outset" to 0.5f)) {
            val moved = offsetPolygon(ring, distance).mapIndexed { i, p -> hypot(p.x - ring[i].x, p.y - ring[i].y) }
            assertTrue(
                moved.all { it in 0.45f..0.75f },
                "$label moved vertices by ${moved.withIndex().filter { it.value !in 0.45f..0.75f }.take(3)}",
            )
        }
    }

    @Test
    fun everySolidCoreIsEmittedBeforeAnyFringe() {
        // Two sides of a partial border, the shape InputOtp's slots draw: separate groups meeting at
        // a corner. Emitted group by group, the first side's fringe reached a corner pixel the
        // second side's core also covers, and a mesh paints each pixel once -- the input-otp
        // baseline lost 10 border pixels that way.
        val sides = DrawPath.build {
            moveTo(0.5f, 0.5f)
            lineTo(35.5f, 0.5f)
            moveTo(35.5f, 0.5f)
            lineTo(35.5f, 35.5f)
        }
        val mesh = sides.tessellateStrokeAa(DrawStroke(width = 1f.dp), Color(1f, 1f, 1f, 1f))
        val fringe = mesh.indices.toList().chunked(3).map { triangle -> triangle.any { mesh.vertices[it].color.a < 1f } }

        assertTrue(fringe.any { it } && fringe.any { !it }, "expected both core and fringe triangles")
        assertEquals(fringe.sorted(), fringe, "a fringe triangle came before a core triangle")
    }

    @Test
    fun aHolesFringeFadesIntoTheHole() {
        // The hole's rings were offset with the outer ring's signs, so its transparent edge sat
        // 0.5 px into the solid band, under the core, and the core reached 0.5 px into the hole:
        // every hole -- an even-odd icon's, a stroked ring's inner edge -- drew hard and too small.
        val frame = DrawPath.build(fillRule = FillRule.EvenOdd) {
            moveTo(0f, 0f)
            lineTo(20f, 0f)
            lineTo(20f, 20f)
            lineTo(0f, 20f)
            close()
            moveTo(5f, 5f)
            lineTo(15f, 5f)
            lineTo(15f, 15f)
            lineTo(5f, 15f)
            close()
        }
        val transparent = frame.tessellateFillAa(Color(1f, 1f, 1f, 1f)).vertices
            .filter { it.color.a == 0f }
            .map { it.position }

        assertTrue(transparent.isNotEmpty(), "no fringe")
        assertTrue(
            transparent.all { p ->
                val outside = p.x < 0f || p.x > 20f || p.y < 0f || p.y > 20f
                val inHole = p.x > 5f && p.x < 15f && p.y > 5f && p.y < 15f
                outside || inHole
            },
            "a transparent fringe vertex sits in the solid band: ${transparent.filter { it.x in 0f..20f && it.y in 0f..20f }.take(4)}",
        )
    }

    @Test
    fun aStrokedLoopsInnerEdgeNeverDoublesBack() {
        // offsetClosedRing put a round-join arc on both sides of every vertex. On the inside of a
        // turn the arc runs backwards, so a flattened circle's inner edge was 96 tiny loops, and
        // the fringe built on it landed under the core: the ring's inner edge drew unantialiased.
        val circle = DrawPath.build {
            for (i in 0 until 96) {
                val angle = 2.0 * kotlin.math.PI * i / 96
                val x = 20f + 4f * kotlin.math.cos(angle).toFloat()
                val y = 20f + 4f * kotlin.math.sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val rings = circle.strokeToSvgFillPath(DrawStroke(width = (8f / 3f).dp, join = StrokeJoin.Round))
            .flattenContours()
            .map { it.points }
        val inner = rings.minBy { ring -> ring.sumOf { hypot(it.x - 20f, it.y - 20f).toDouble() } / ring.size }
        val steps = inner.indices.map { i ->
            val a = inner[i]
            val b = inner[(i + 1) % inner.size]
            val delta = kotlin.math.atan2(b.y - 20f, b.x - 20f) - kotlin.math.atan2(a.y - 20f, a.x - 20f)
            ((delta + 3 * kotlin.math.PI) % (2 * kotlin.math.PI) - kotlin.math.PI).toFloat()
        }.filter { abs(it) > 1e-6f }

        assertTrue(
            steps.all { it > 0f } || steps.all { it < 0f },
            "the inner ring turns back ${steps.count { it > 0f }} times one way, ${steps.count { it < 0f }} the other",
        )
    }

    @Test
    fun anArcIsFlattenedToWhatItsSagittaNeedsRatherThanAFixedMinimum() {
        // adaptiveArcSteps floored every arc at MIN_ADAPTIVE_STEPS. A stroke's round join runs it
        // once per centreline vertex, and between two segments of an already-flattened curve the
        // sweep is a fraction of a degree -- so four segments were emitted where one is exact to
        // 0.001 px. That floor was 36% of the vertices in a Studio frame.
        assertEquals(1, adaptiveArcSteps(sweepDegrees = 0.5f, radiusPx = 0.75f, maxStepDegrees = 6f))
        assertEquals(1, adaptiveArcSteps(sweepDegrees = 6f, radiusPx = 0.75f, maxStepDegrees = 6f))

        // Unchanged where the sweep genuinely needs the segments: the step size still comes from
        // the flatness tolerance and the caller's cap, and only the lower clamp moved.
        assertEquals(5, adaptiveArcSteps(sweepDegrees = 30f, radiusPx = 0.75f, maxStepDegrees = 6f))
        assertEquals(15, adaptiveArcSteps(sweepDegrees = 90f, radiusPx = 0.75f, maxStepDegrees = 6f))

        // A large radius makes the sagitta bound, not the cap, decide -- still honoured.
        assertTrue(
            adaptiveArcSteps(sweepDegrees = 10f, radiusPx = 400f, maxStepDegrees = 90f) > 1,
            "a 400px-radius arc needs more than one chord to stay within tolerance",
        )
    }

    @Test
    fun aStrokedArcsOutlineIsNotConvexAndKeepsItsHole() {
        // isConvex only compared turn signs, and a stroked arc's ring turns one way the whole way
        // round while wrapping about twice: out along one side, round the cap, back along the
        // other, cap again. It read as convex, tessellateFill centroid-fanned it, and the fan
        // covered the hole -- Heroicons' user-circle and light-bulb rendered as solid discs.
        val arc = DrawPath.build {
            arcTo(2f, 2f, 18f, 18f, startDegrees = 0f, sweepDegrees = 300f)
        }
        val ring = arc.strokeToFillPath(DrawStroke(width = 1.5f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round))

        assertTrue(ring.flattenContours().none { isConvex(it.points) }, "a stroke ring is not convex")
        assertTrue(!ring.containsPoint(10f, 10f), "the arc's centre stays empty")
        // Mid-arc (150 degrees), clear of both endpoints: on the centreline, so inside the band.
        assertTrue(ring.containsPoint(10f - 8f * 0.866f, 10f + 8f * 0.5f), "the band itself is filled")
    }
}
