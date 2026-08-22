// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui

import io.github.ronjunevaldoz.awake.core.graphics2d.DrawShape
import io.github.ronjunevaldoz.awake.core.graphics2d.FillRule
import io.github.ronjunevaldoz.awake.core.graphics2d.PathCommand
import io.github.ronjunevaldoz.awake.core.graphics2d.TriangleMesh
import io.github.ronjunevaldoz.awake.core.graphics2d.TexturedTriangleMesh
import io.github.ronjunevaldoz.awake.core.graphics2d.TexturedVertex
import io.github.ronjunevaldoz.awake.core.graphics2d.DrawPoint
import io.github.ronjunevaldoz.awake.core.graphics2d.DrawStroke
import io.github.ronjunevaldoz.awake.core.graphics2d.bounds
import io.github.ronjunevaldoz.awake.core.graphics2d.clipToConvexPath
import io.github.ronjunevaldoz.awake.core.graphics2d.containsPoint
import io.github.ronjunevaldoz.awake.core.graphics2d.drawPath
import io.github.ronjunevaldoz.awake.core.graphics2d.safeInteriorMargin
import io.github.ronjunevaldoz.awake.core.graphics2d.tessellateFill
import io.github.ronjunevaldoz.awake.core.graphics2d.tessellateStroke
import io.github.ronjunevaldoz.awake.core.graphics2d.toPath
import io.github.ronjunevaldoz.awake.core.graphics2d.transform
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.core.math2d.size
import io.github.ronjunevaldoz.awake.ui.style.Style
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiPathTest {

    @Test
    fun builderPreservesCommandOrder() {
        val path = drawPath(FillRule.EvenOdd) {
            moveTo(1f, 2f)
            lineTo(3f, 4f)
            quadTo(5f, 6f, 7f, 8f)
            cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
            arcTo(15f, 16f, 17f, 18f, 90f, 180f)
            close()
        }

        assertEquals(FillRule.EvenOdd, path.fillRule)
        assertEquals(
            listOf(
                PathCommand.MoveTo(1f, 2f),
                PathCommand.LineTo(3f, 4f),
                PathCommand.QuadTo(5f, 6f, 7f, 8f),
                PathCommand.CubicTo(9f, 10f, 11f, 12f, 13f, 14f),
                PathCommand.ArcTo(15f, 16f, 17f, 18f, 90f, 180f),
                PathCommand.Close,
            ),
            path.commands,
        )
    }

    @Test
    fun roundedRectangleExpandsToDeterministicArcSequence() {
        val path = DrawShape.RoundedRectangle(8f.dp).toPath(Rectangle(0f, 0f, 40f, 20f))

        assertEquals(
            listOf(
                PathCommand.MoveTo(8f, 0f),
                PathCommand.LineTo(32f, 0f),
                PathCommand.ArcTo(24f, 0f, 40f, 16f, -90f, 90f),
                PathCommand.LineTo(40f, 12f),
                PathCommand.ArcTo(24f, 4f, 40f, 20f, 0f, 90f),
                PathCommand.LineTo(8f, 20f),
                PathCommand.ArcTo(0f, 4f, 16f, 20f, 90f, 90f),
                PathCommand.LineTo(0f, 8f),
                PathCommand.ArcTo(0f, 0f, 16f, 16f, 180f, 90f),
                PathCommand.Close,
            ),
            path.commands,
        )
    }

    @Test
    fun circleUsesCenteredSquareWithinNonSquareBounds() {
        val path = DrawShape.Circle.toPath(Rectangle(0f, 0f, 40f, 20f))

        assertEquals(PathCommand.MoveTo(20f, 0f), path.commands.first())
        assertEquals(
            PathCommand.ArcTo(10f, 0f, 30f, 20f, -90f, 90f),
            path.commands[2],
            "circle should be centered using the largest inscribed square",
        )
    }

    @Test
    fun pillClampsToHalfOfShortestDimension() {
        val path = DrawShape.Pill.toPath(Rectangle(0f, 0f, 60f, 20f))

        assertEquals(PathCommand.MoveTo(10f, 0f), path.commands.first())
        assertEquals(PathCommand.LineTo(50f, 0f), path.commands[1])
    }

    @Test
    fun customShapeSpecOverridesLegacyRadiusInStyleResolution() {
        val resolved = Style {
            shape(UiShape.sm)
            shape(DrawShape.CutCorner(6f.dp))
        }.resolve()

        assertEquals(DrawShape.CutCorner(6f.dp), resolved.shapeSpec)
        assertEquals(UiShape.none, resolved.shape)
    }

    @Test
    fun legacyRadiusClearsCustomShapeSpecWhenAppliedLast() {
        val resolved = Style {
            shape(DrawShape.Circle)
            shape(12f.dp)
        }.resolve()

        assertEquals(12f.dp, resolved.shape)
        assertNull(resolved.shapeSpec)
    }

    @Test
    fun boundsCoversEntireExpandedPath() {
        val path = DrawShape.CutCorner(6f.dp).toPath(Rectangle(10f, 20f, 40f, 30f))

        assertEquals(Rectangle(10f, 20f, 40f, 30f), path.bounds())
    }

    @Test
    fun transformScalesAndTranslatesPathCommands() {
        val path = drawPath {
            moveTo(1f, 2f)
            lineTo(3f, 4f)
            arcTo(0f, 1f, 4f, 5f, 0f, 90f)
        }.transform(scaleX = 2f, scaleY = 3f, translateX = 5f, translateY = 7f)

        assertEquals(
            listOf(
                PathCommand.MoveTo(7f, 13f),
                PathCommand.LineTo(11f, 19f),
                PathCommand.ArcTo(5f, 10f, 13f, 22f, 0f, 90f),
            ),
            path.commands,
        )
    }

    @Test
    fun cutCornerFillTessellatesIntoTriangleFan() {
        val mesh = DrawShape.CutCorner(6f.dp).toPath(Rectangle(0f, 0f, 40f, 20f)).tessellateFill()

        // Fan is built from the polygon's centroid (not vertex 0) for numeric robustness on
        // wide/shallow shapes -- see UiPath.tessellateFill -- so the mesh gains one extra
        // (centroid) point and one triangle per polygon edge instead of per interior vertex.
        assertEquals(9, mesh.points.size, "8 polygon points plus 1 centroid point")
        assertEquals(24, mesh.indices.size, "8-point convex polygon should tessellate to 8 centroid-fan triangles")
    }

    @Test
    fun roundedRectangleStrokeTessellatesIntoSegmentQuads() {
        val mesh = DrawShape.RoundedRectangle(8f.dp).toPath(Rectangle(0f, 0f, 40f, 20f)).tessellateStroke(DrawStroke(2f.dp))

        assertTrue(mesh.points.size >= 16, "rounded corners should flatten into multiple stroke segments")
        assertEquals(0, mesh.indices.size % 6, "stroke geometry should be emitted as quads split into 2 triangles each")
    }

    @Test
    fun cutCornerPathContainsInteriorButNotClippedCorner() {
        val path = DrawShape.CutCorner(6f.dp).toPath(Rectangle(0f, 0f, 40f, 20f))

        assertTrue(path.containsPoint(20f, 10f))
        assertTrue(!path.containsPoint(1f, 1f), "the top-left clipped corner should sit outside the path")
    }

    @Test
    fun triangleMeshClipsAgainstConvexCutCornerPath() {
        val quadMesh = TriangleMesh(
            points = listOf(
                DrawPoint(0f, 0f),
                DrawPoint(40f, 0f),
                DrawPoint(40f, 20f),
                DrawPoint(0f, 20f),
            ),
            indices = intArrayOf(0, 1, 2, 2, 3, 0),
        )

        val clipped = quadMesh.clipToConvexPath(
            DrawShape.CutCorner(6f.dp).toPath(
                Rectangle(
                    0f,
                    0f,
                    40f,
                    20f,
                ),
            ),
        )

        assertTrue(clipped.points.size > 4, "clipping should introduce intersection vertices")
        assertTrue(clipped.points.none { it.x == 0f && it.y == 0f }, "the fully clipped corner vertex should be removed from the output mesh")
    }

    @Test
    fun texturedTriangleMeshClipsAgainstConvexCutCornerPathAndPreservesUvInterpolation() {
        val quadMesh = TexturedTriangleMesh(
            vertices = listOf(
                TexturedVertex(DrawPoint(0f, 0f), u = 0f, v = 0f),
                TexturedVertex(DrawPoint(40f, 0f), u = 1f, v = 0f),
                TexturedVertex(DrawPoint(40f, 20f), u = 1f, v = 1f),
                TexturedVertex(DrawPoint(0f, 20f), u = 0f, v = 1f),
            ),
            indices = intArrayOf(0, 1, 2, 2, 3, 0),
        )

        val clipped = quadMesh.clipToConvexPath(
            DrawShape.CutCorner(6f.dp).toPath(
                Rectangle(
                    0f,
                    0f,
                    40f,
                    20f,
                ),
            ),
        )

        assertTrue(clipped.vertices.size > 4, "clipping should introduce intersection vertices")
        assertTrue(clipped.vertices.none { it.position.x == 0f && it.position.y == 0f }, "the fully clipped corner vertex should be removed from the output mesh")

        val topEdgeVertex = clipped.vertices.firstOrNull { abs(it.position.x - 6f) < 0.001f && abs(it.position.y) < 0.001f }
        assertTrue(topEdgeVertex != null, "expected an interpolated top-edge clip vertex at x=6")
        assertTrue(abs(topEdgeVertex.u - 0.15f) < 0.001f, "u should interpolate along the top edge")
        assertTrue(abs(topEdgeVertex.v) < 0.001f, "v should stay pinned to the top edge")
    }

    @Test
    fun imageVectorFitsInsideTargetSlotAndKeepsAspectRatio() {
        val vector = uiImageVector(
            defaultWidth = 12f.dp,
            defaultHeight = 12f.dp,
            viewportWidth = 12f,
            viewportHeight = 12f,
        ) {
            path {
                moveTo(0f, 0f)
                lineTo(12f, 0f)
                lineTo(12f, 12f)
                close()
            }
        }

        val fitted = vector.fitTo(Rectangle(10f, 20f, 24f, 12f)).single().path

        assertEquals(
            listOf(
                PathCommand.MoveTo(16f, 20f),
                PathCommand.LineTo(28f, 20f),
                PathCommand.LineTo(28f, 32f),
                PathCommand.Close,
            ),
            fitted.commands,
        )
    }

    /**
     * A segmented control's leading member: rounded on the outside, square where it meets its
     * neighbour. One uniform radius cannot say that, so a zero corner must emit the corner POINT
     * and no arc -- otherwise the member's fill overhangs the group's rounded border.
     */
    @Test
    fun roundedCornersRoundsOnlyTheCornersGivenARadius() {
        val bounds = Rectangle(0f, 0f, 40f, 20f)
        val path = DrawShape.RoundedCorners(
            topLeft = 6f.dp,
            topRight = 0f.dp,
            bottomRight = 0f.dp,
            bottomLeft = 6f.dp,
        ).toPath(bounds)

        val arcs = path.commands.filterIsInstance<PathCommand.ArcTo>()
        assertEquals(2, arcs.size, "only the two left corners round")

        val points = path.commands.mapNotNull {
            when (it) {
                is PathCommand.MoveTo -> it.x to it.y
                is PathCommand.LineTo -> it.x to it.y
                else -> null
            }
        }
        assertTrue(points.any { it == 40f to 0f }, "square top-right reaches the corner point")
        assertTrue(points.any { it == 40f to 20f }, "square bottom-right reaches the corner point")
        assertTrue(points.none { it.first == 0f && it.second == 0f }, "rounded top-left is cut")
    }

    @Test
    fun roundedCornersClampsEachCornerIndependently() {
        val path = DrawShape.RoundedCorners(
            topLeft = 999f.dp,
            topRight = 0f.dp,
            bottomRight = 0f.dp,
            bottomLeft = 0f.dp,
        ).toPath(Rectangle(0f, 0f, 40f, 20f))

        // Clamped to half the SHORTER side (10), so the arc's box is that diameter -- an
        // over-large corner cannot eat the edge its neighbour needs.
        val arc = path.commands.filterIsInstance<PathCommand.ArcTo>().single()
        assertEquals(20f, arc.right - arc.left)
        assertEquals(20f, arc.bottom - arc.top)
    }

    @Test
    fun shapesScaleCornerRadiiWithExplicitDensity() {
        val bounds = Rectangle(0f, 0f, 100f, 100f)
        val shape = DrawShape.RoundedRectangle(8f.dp)

        val path1x = shape.toPath(bounds, density = 1f)
        val arc1x = path1x.commands.filterIsInstance<PathCommand.ArcTo>().first()
        assertEquals(16f, arc1x.right - arc1x.left, "at 1x density, radius 8dp yields 16px diameter")
        assertEquals(8f, shape.safeInteriorMargin(bounds, density = 1f))

        val path2x = shape.toPath(bounds, density = 2f)
        val arc2x = path2x.commands.filterIsInstance<PathCommand.ArcTo>().first()
        assertEquals(32f, arc2x.right - arc2x.left, "at 2x density, radius 8dp yields 32px diameter")
        assertEquals(16f, shape.safeInteriorMargin(bounds, density = 2f))
    }
}
