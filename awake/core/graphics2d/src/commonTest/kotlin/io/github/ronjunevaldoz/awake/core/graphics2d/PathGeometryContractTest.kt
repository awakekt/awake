// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.color.Color
import kotlin.math.abs
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
}
