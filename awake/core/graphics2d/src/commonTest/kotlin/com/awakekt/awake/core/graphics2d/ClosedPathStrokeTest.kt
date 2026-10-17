/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A stroked closed loop is a ring: its outline is covered and its middle is not.
 *
 * A full arc ends a float's rounding away from where it began, so closing it adds a segment a
 * millionth of a pixel long. Its direction is noise, and offsetting it put a spike through the
 * stroke's inner edge, so the hole stopped being a hole and the whole disc filled. Every check here
 * runs on the geometry the renderer uploads, both the solid and the anti-aliased mesh.
 */
class ClosedPathStrokeTest {
    private val center = DrawPoint(100f, 100f)
    private val radius = 40f
    private val widths = listOf(0.5f, 1f, 2f, 4f, 8f, 16f, 30f)

    private val circles: Map<String, DrawPath> = mapOf(
        "a full arc, closed" to drawPath {
            moveTo(center.x + radius, center.y)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 0f, 360f)
            close()
        },
        "a full arc from 37 degrees, closed" to drawPath {
            val start = 37f * PI.toFloat() / 180f
            moveTo(center.x + cos(start) * radius, center.y + sin(start) * radius)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 37f, 360f)
            close()
        },
        "a full arc swept backwards, closed" to drawPath {
            moveTo(center.x + radius, center.y)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 0f, -360f)
            close()
        },
        "two half arcs, closed" to drawPath {
            moveTo(center.x + radius, center.y)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 0f, 180f)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 180f, 180f)
            close()
        },
        "a full arc, never closed" to drawPath {
            moveTo(center.x + radius, center.y)
            arcTo(center.x - radius, center.y - radius, center.x + radius, center.y + radius, 0f, 360f)
        },
        "the circle shape" to DrawShape.Circle.toPath(Rectangle(center.x - radius, center.y - radius, radius * 2f, radius * 2f)),
        "an off-grid circle shape" to DrawShape.Circle.toPath(Rectangle(59.95f, 60.05f, 80.1f, 80.1f)),
    )

    @Test
    fun aStrokedClosedCircleIsARing() {
        eachCase(circles, widths) { path, stroke, case -> assertRing(path, stroke, case) }
    }

    @Test
    fun aStrokedEllipseIsARing() {
        val long = 60f
        val short = 25f
        val boxes = mapOf("a wide ellipse" to (long to short), "a tall ellipse" to (short to long))
        eachCase(boxes, listOf(1f, 2f, 6f, 12f)) { (ax, ay), stroke, case ->
            val width = stroke.width.value
            val path = drawPath {
                moveTo(center.x + ax, center.y)
                arcTo(center.x - ax, center.y - ay, center.x + ax, center.y + ay, 0f, 360f)
                close()
            }
            val solid = path.tessellateStroke(stroke)
            val aa = path.tessellateStrokeAa(stroke, Color.White)
            val angles = (0 until 12).map { it * PI.toFloat() / 6f + 0.01f }
            val outline = angles.map { DrawPoint(center.x + cos(it) * ax, center.y + sin(it) * ay) }
            // Well inside the ring: the inner edge of an ellipse's stroke is nowhere nearer.
            val scale = (short - width) / short * 0.8f
            val inside = angles.map { DrawPoint(center.x + cos(it) * ax * scale, center.y + sin(it) * ay * scale) }
            for ((kind, covers) in listOf("solid" to solid::covers, "anti-aliased" to aa::covers)) {
                assertTrue(!covers(center), "the middle of $case ($kind) is filled")
                outline.forEach { assertTrue(covers(it), "$case ($kind) leaves its outline uncovered at $it") }
                inside.forEach { assertTrue(!covers(it), "$case ($kind) fills its inside at $it") }
            }
            assertReach(solid.points, long + width / 2f + 0.05f, "$case (solid)")
            assertReach(aa.vertices.map { it.position }, long + width / 2f + AA_FRINGE_PX + 0.05f, "$case (anti-aliased)")
        }
    }

    @Test
    fun strokedRoundedRectanglesAreOutlines() {
        val bounds = Rectangle(40.5f, 50.25f, 120f, 70f)
        val offGrid = Rectangle(10.1f, 20.7f, 99.9f, 33.3f)
        val shapes = mapOf(
            "rounded rectangle" to (DrawShape.RoundedRectangle(12f.dp) to bounds),
            "pill" to (DrawShape.Pill to bounds),
            "rounded corners" to (DrawShape.RoundedCorners(4f.dp, 16f.dp, 0f.dp, 30f.dp) to bounds),
            "off-grid rounded rectangle" to (DrawShape.RoundedRectangle(7.3f.dp) to offGrid),
        )
        eachCase(shapes, listOf(1f, 2f, 4f, 10f)) { (shape, box), stroke, case ->
            val path = shape.toPath(box)
            val middle = DrawPoint(box.x + box.width / 2f, box.y + box.height / 2f)
            val solid = path.tessellateStroke(stroke)
            val aa = path.tessellateStrokeAa(stroke, Color.White)
            for ((kind, covers) in listOf("solid" to solid::covers, "anti-aliased" to aa::covers)) {
                assertTrue(!covers(middle), "the middle of $case ($kind) is filled")
                assertTrue(covers(DrawPoint(middle.x, box.y)), "$case ($kind) misses its top edge")
                assertTrue(covers(DrawPoint(middle.x, box.y + box.height)), "$case ($kind) misses its bottom edge")
            }
            val slack = stroke.width.value / 2f + AA_FRINGE_PX + 0.05f
            (solid.points + aa.vertices.map { it.position }).forEach { point ->
                val inside = point.x >= box.x - slack && point.x <= box.x + box.width + slack &&
                    point.y >= box.y - slack && point.y <= box.y + box.height + slack
                assertTrue(inside, "$case spikes out to $point")
            }
        }
    }

    @Test
    fun aZeroLengthSegmentInAnOpenLineIsJoinedWithoutASpike() {
        val paths = mapOf(
            "a repeated point" to drawPath {
                moveTo(10f, 10f)
                lineTo(60f, 10f)
                lineTo(60f, 10f)
                lineTo(110f, 10f)
            },
            "a point a rounding error away" to drawPath {
                moveTo(10f, 10f)
                lineTo(60f, 10f)
                lineTo(60.000004f, 9.999998f)
                lineTo(110f, 10f)
            },
            "a corner a rounding error long" to drawPath {
                moveTo(10f, 10f)
                lineTo(60f, 10f)
                lineTo(60.000004f, 10.000002f)
                lineTo(60f, 60f)
            },
        )
        eachCase(paths, listOf(1f, 4f, 10f)) { path, stroke, case ->
            val mesh = path.tessellateStroke(stroke)
            assertTrue(mesh.covers(DrawPoint(35f, 10f)), "$case misses its first segment")
            val slack = stroke.width.value / 2f + 0.05f
            val straight = !case.contains("corner")
            mesh.points.forEach { point ->
                val reach = point.x in 10f - slack..110f + slack && point.y in 10f - slack..60f + slack
                assertTrue(reach, "$case spikes out to $point")
                // A straight run is just the run: nothing sticks out sideways at the repeated point.
                assertTrue(!straight || point.y in 10f - slack..10f + slack, "$case spikes sideways to $point")
            }
        }
    }

    /** Runs [check] on every item at every width and join, naming the case in its messages. */
    private fun <T> eachCase(items: Map<String, T>, widths: List<Float>, check: (T, DrawStroke, String) -> Unit) {
        val strokes = widths.flatMap { width -> StrokeJoin.entries.map { DrawStroke(width.dp, join = it) } }
        for ((name, item) in items) {
            strokes.forEach { check(item, it, "$name, width ${it.width.value}, ${it.join} join") }
        }
    }

    private fun assertRing(path: DrawPath, stroke: DrawStroke, case: String) {
        val half = stroke.width.value / 2f
        val solid = path.tessellateStroke(stroke)
        val aa = path.tessellateStrokeAa(stroke, Color.White)
        for ((kind, covers) in listOf("solid" to solid::covers, "anti-aliased" to aa::covers)) {
            assertTrue(!covers(center), "the middle of $case ($kind) is filled")
            for (step in 0 until 24) {
                val angle = step * PI.toFloat() / 12f + 0.01f
                val onLine = at(angle, radius)
                assertTrue(covers(onLine), "$case ($kind) leaves its outline uncovered at $onLine")
                val holeRadius = radius - half - AA_FRINGE_PX - 0.5f
                if (holeRadius > 0f) {
                    val hole = at(angle, holeRadius)
                    assertTrue(!covers(hole), "$case ($kind) fills its hole at $hole")
                }
            }
        }
        // The circle shape may sit a fraction off the centre; half a pixel covers it.
        assertReach(solid.points, radius + half + 0.5f, "$case (solid)")
        assertReach(aa.vertices.map { it.position }, radius + half + AA_FRINGE_PX + 0.5f, "$case (anti-aliased)")
    }

    private fun at(angle: Float, distance: Float) = DrawPoint(center.x + cos(angle) * distance, center.y + sin(angle) * distance)

    private fun assertReach(points: List<DrawPoint>, reach: Float, case: String) {
        points.forEach {
            val distance = hypot(it.x - center.x, it.y - center.y)
            assertTrue(distance <= reach, "$case spikes out to $it, $distance from the centre, past $reach")
        }
    }
}

private fun TriangleMesh.covers(point: DrawPoint): Boolean = coversAny(points, indices, point)

private fun ColoredTriangleMesh.covers(point: DrawPoint): Boolean = coversAny(vertices.map { it.position }, indices, point)

private fun coversAny(points: List<DrawPoint>, indices: IntArray, point: DrawPoint): Boolean {
    for (at in indices.indices step 3) {
        if (inTriangle(point, points[indices[at]], points[indices[at + 1]], points[indices[at + 2]])) return true
    }
    return false
}

private fun inTriangle(p: DrawPoint, a: DrawPoint, b: DrawPoint, c: DrawPoint): Boolean {
    // A zero-area sliver covers nothing.
    if (side(a, b, c) == 0f) return false
    val d1 = side(p, a, b)
    val d2 = side(p, b, c)
    val d3 = side(p, c, a)
    val hasNegative = d1 < 0f || d2 < 0f || d3 < 0f
    val hasPositive = d1 > 0f || d2 > 0f || d3 > 0f
    return !(hasNegative && hasPositive)
}

private fun side(p: DrawPoint, a: DrawPoint, b: DrawPoint): Float = (p.x - b.x) * (a.y - b.y) - (a.x - b.x) * (p.y - b.y)
