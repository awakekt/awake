/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.SphereShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a shape's wireframe is drawn where its collider is, with no scene involved.
 *
 * Extents and placement rather than "some lines came back": an overlay drawn half a field off, or at
 * the wrong scale, hides exactly the bug it exists to show.
 */
class PhysicsShapeWireframeTest {

    private fun linesOf(
        shape: PhysicsShape,
        position: Vec3f = Vec3f(0f, 0f, 0f),
        rotation: Quat = Quat(),
    ): List<Drawn> = RecordedLines().also { PhysicsShapeWireframe().write(shape, position, rotation, RED, it) }.drawn

    @Test
    fun aBoxIsItsTwelveEdgesAtItsHalfExtents() {
        val lines = linesOf(BoxShape(Vec3f(1f, 2f, 3f)), Vec3f(10f, 0f, 0f))

        assertEquals(BOX_EDGES, lines.size)
        val (min, max) = lines.bounds()
        assertTrue(min.near(Vec3f(9f, -2f, -3f)) && max.near(Vec3f(11f, 2f, 3f)), "$min..$max")
        assertTrue(lines.all { it.color == RED })
    }

    @Test
    fun aSphereIsThreeRings() {
        val lines = linesOf(SphereShape(2.5f), Vec3f(0f, 5f, 0f))

        assertEquals(3 * PhysicsShapeWireframe.CIRCLE_SEGMENTS, lines.size)
        val (min, max) = lines.bounds()
        // Rings are polygons, so they sit just inside the true radius.
        assertTrue(max.x in 2.4f..2.5f, "x extent ${max.x}")
        assertTrue(abs(min.y - 2.5f) < 0.11f && abs(max.y - 7.5f) < 0.11f, "y $min..$max")
    }

    @Test
    fun aCapsuleReachesARadiusBeyondItsCylinder() {
        val lines = linesOf(CapsuleShape(halfHeight = 0.9f, radius = 0.3f))

        assertEquals(2 * PhysicsShapeWireframe.CIRCLE_SEGMENTS + 1 + 4, lines.size)
        val (min, max) = lines.bounds()
        assertTrue(abs(max.y - 1.2f) < EPSILON && abs(min.y + 1.2f) < EPSILON, "y $min..$max")
    }

    @Test
    fun aCornerHeightfieldGrowsIntoPlusXAndPlusZFromItsPosition() {
        val shape = HeightFieldShape(
            heights = FloatArray(16) { it - 2f },
            sampleCount = 4,
            scale = Vec3f(2f, 0.5f, 3f),
            origin = GridOrigin.Corner,
        )

        val lines = linesOf(shape, Vec3f(10f, 1f, -5f))

        // Three cells each way: six units across x, nine along z, at the lowest sample (-2 * 0.5).
        val expected = listOf(Vec3f(10f, 0f, -5f), Vec3f(16f, 0f, -5f), Vec3f(16f, 0f, 4f), Vec3f(10f, 0f, 4f))
        assertEquals(4, lines.size)
        lines.forEachIndexed { index, line ->
            assertTrue(line.a.near(expected[index]), "corner $index at ${line.a}, not ${expected[index]}")
            assertTrue(line.b.near(expected[(index + 1) % 4]), "edge $index ends at ${line.b}")
        }
    }

    @Test
    fun aCentredHeightfieldStaysCentredOnItsPosition() {
        val shape = HeightFieldShape(heights = FloatArray(16), sampleCount = 4, scale = Vec3f(2f, 1f, 3f))

        val (min, max) = linesOf(shape, Vec3f(10f, 1f, -5f)).bounds()

        assertTrue(min.near(Vec3f(7f, 1f, -9.5f)) && max.near(Vec3f(13f, 1f, -0.5f)), "$min..$max")
    }

    @Test
    fun aRotatedShapeIsTurnedAboutItsPosition() {
        val rotation = Quat.fromEuler(Vec3f(0.3f, 0.7f, -0.2f))
        val position = Vec3f(2f, 3f, 4f)

        val first = linesOf(BoxShape(Vec3f(1f, 2f, 3f)), position, rotation).first()

        val corner = position + rotation.rotate(Vec3f(-1f, -2f, -3f))
        val along = position + rotation.rotate(Vec3f(1f, -2f, -3f))
        assertTrue(first.a.near(corner) && first.b.near(along), "$first, not $corner to $along")
    }

    @Test
    fun theCallersPoseIsLeftAsItWas() {
        val rotation = Quat.fromEuler(Vec3f(0f, 0.6f, 0f))
        val position = Vec3f(2f, 0f, 4f)
        val rotationBefore = rotation.copy()
        val positionBefore = position.copy()

        linesOf(CapsuleShape(halfHeight = 1f, radius = 0.5f), position, rotation)

        assertEquals(rotationBefore, rotation)
        assertEquals(positionBefore, position)
    }

    @Test
    fun aMeshAndAHullAreTheBoxAroundTheirPoints() {
        val mesh = MeshShape(
            vertices = floatArrayOf(-2f, 0f, -3f, 2f, 0f, -3f, 2f, 1f, 3f, -2f, 1f, 3f),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )
        val hull = ConvexHullShape(floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f))

        val meshLines = linesOf(mesh)
        val hullLines = linesOf(hull, Vec3f(10f, 0f, 0f))

        assertEquals(BOX_EDGES, meshLines.size)
        val (meshMin, meshMax) = meshLines.bounds()
        assertTrue(meshMin.near(Vec3f(-2f, 0f, -3f)) && meshMax.near(Vec3f(2f, 1f, 3f)), "$meshMin..$meshMax")
        val (hullMin, hullMax) = hullLines.bounds()
        assertTrue(hullMin.near(Vec3f(10f, 0f, 0f)) && hullMax.near(Vec3f(12f, 2f, 2f)), "$hullMin..$hullMax")
    }

    private data class Drawn(val a: Vec3f, val b: Vec3f, val color: Color)

    /** Copies each line's points, as a host does: the writer reuses its own. */
    private class RecordedLines : DebugLines {
        val drawn = ArrayList<Drawn>()

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            drawn += Drawn(a.copy(), b.copy(), color)
        }
    }

    private fun List<Drawn>.bounds(): Pair<Vec3f, Vec3f> {
        val points = flatMap { listOf(it.a, it.b) }
        return Vec3f(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z }) to
            Vec3f(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z })
    }

    private fun Vec3f.near(other: Vec3f): Boolean =
        abs(x - other.x) < EPSILON && abs(y - other.y) < EPSILON && abs(z - other.z) < EPSILON

    private companion object {
        const val BOX_EDGES = 12
        const val EPSILON = 1e-4f
        val RED = Color(r = 1f, g = 0f, b = 0f)
    }
}
