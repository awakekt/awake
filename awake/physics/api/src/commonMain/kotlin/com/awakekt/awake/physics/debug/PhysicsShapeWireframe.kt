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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Writes a [PhysicsShape]'s wireframe as world-space lines: the shape a body was built with, at the
 * pose it is given, which is what physics actually collides against.
 *
 * - A [BoxShape] is its twelve edges.
 * - A [SphereShape] is three rings, one round each axis.
 * - A [CapsuleShape] is a ring at each cap centre, the line between its extremes and four sides.
 *   Not the rounded ends: they cost more lines than the diagnostic is worth, and the line between
 *   the extremes already shows the capsule's full extent.
 * - A [HeightFieldShape] is the outline of its footprint at its lowest sample, laid out as the
 *   backend places the collider: a [GridOrigin.Centered] field spans `-extent/2 .. +extent/2` round
 *   the position and a [GridOrigin.Corner] one `0 .. extent` from it, turned by the rotation. A
 *   terrain of any size is thousands of quads, and the useful question about its collider is almost
 *   always where its edges are.
 * - A [MeshShape] and a [ConvexHullShape] are the box enclosing their points. A level mesh is
 *   thousands of triangles and a hull's real faces are the backend's to compute; the question this
 *   answers about either is where it is.
 *
 * Needs no scene: the scene's physics debug source walks its bodies and calls [write] for each one.
 * It keeps its own rotation and points and reuses them for every line, so writing allocates nothing;
 * a [DebugLines] that keeps a line copies its two points. Not thread-safe: one writer per thread,
 * which for a debug overlay is the frame thread.
 */
class PhysicsShapeWireframe {
    private val rotation = Quat()
    private val corners = Array(CORNER_COUNT) { Vec3f(0f, 0f, 0f) }
    private val a = Vec3f(0f, 0f, 0f)
    private val b = Vec3f(0f, 0f, 0f)
    private val top = Vec3f(0f, 0f, 0f)
    private val bottom = Vec3f(0f, 0f, 0f)
    private val offset = Vec3f(0f, 0f, 0f)
    private val centre = Vec3f(0f, 0f, 0f)

    /**
     * Writes [shape]'s wireframe into [out], placed at [position] and turned by [rotation], every line
     * in [color]. Neither [position] nor [rotation] is kept or changed.
     */
    fun write(shape: PhysicsShape, position: Vec3f, rotation: Quat, color: Color, out: DebugLines) {
        this.rotation.set(rotation)
        when (shape) {
            is BoxShape -> box(out, shape.halfExtents, position, color)
            // Three rings rather than a mesh: enough to read as a sphere and to show where it is.
            is SphereShape -> {
                ring(out, position, shape.radius, Axis.Y, color)
                ring(out, position, shape.radius, Axis.X, color)
                ring(out, position, shape.radius, Axis.Z, color)
            }
            is CapsuleShape -> capsule(out, shape, position, color)
            is HeightFieldShape -> heightFieldFootprint(out, shape, position, color)
            is MeshShape -> pointCloudBounds(out, shape.vertices, position, color)
            is ConvexHullShape -> pointCloudBounds(out, shape.points, position, color)
        }
    }

    /** Twelve edges around [origin], each corner rotated into world space first. */
    private fun box(out: DebugLines, halfExtents: Vec3f, origin: Vec3f, color: Color) {
        for (index in 0 until CORNER_COUNT) {
            val sx = if (index and 1 == 0) -1f else 1f
            val sy = if (index and 2 == 0) -1f else 1f
            val sz = if (index and 4 == 0) -1f else 1f
            val corner = corners[index]
            rotate(halfExtents.x * sx, halfExtents.y * sy, halfExtents.z * sz, corner)
            corner.set(origin.x + corner.x, origin.y + corner.y, origin.z + corner.z)
        }
        // Corner bits are x, y, z, so a pair differing in exactly one bit is an edge -- which is the
        // whole edge list without writing twelve index pairs out by hand.
        for (from in 0 until CORNER_COUNT) {
            for (bit in 0 until 3) {
                val to = from or (1 shl bit)
                if (to != from) out.line(corners[from], corners[to], color)
            }
        }
    }

    /**
     * A capsule as its two cap centres, the rings around them and the lines joining them.
     *
     * Deliberately not the rounded ends: the caps are where a capsule differs from a cylinder, and
     * drawing them costs more lines than the diagnostic is worth. The rings sit at the cap centres, so
     * the shape's true extent is a radius beyond each -- which the line between the extremes makes
     * obvious.
     */
    private fun capsule(out: DebugLines, shape: CapsuleShape, origin: Vec3f, color: Color) {
        rotate(0f, shape.halfHeight, 0f, offset)
        top.set(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z)
        bottom.set(origin.x - offset.x, origin.y - offset.y, origin.z - offset.z)
        ring(out, top, shape.radius, Axis.Y, color)
        ring(out, bottom, shape.radius, Axis.Y, color)
        // The extremes, so the caps are visible as extent even though they are not drawn as geometry.
        rotate(0f, shape.radius, 0f, offset)
        a.set(top.x + offset.x, top.y + offset.y, top.z + offset.z)
        b.set(bottom.x - offset.x, bottom.y - offset.y, bottom.z - offset.z)
        out.line(a, b, color)
        for (quarter in 0 until QUARTERS) {
            val angle = quarter * (PI.toFloat() / 2f)
            rotate(cos(angle) * shape.radius, 0f, sin(angle) * shape.radius, offset)
            a.set(top.x + offset.x, top.y + offset.y, top.z + offset.z)
            b.set(bottom.x + offset.x, bottom.y + offset.y, bottom.z + offset.z)
            out.line(a, b, color)
        }
    }

    /**
     * The outline of a heightfield's extent, at the height of its lowest sample.
     *
     * Laid out exactly as the backend places the collider: a [GridOrigin.Centered] field spans
     * `-extent/2 .. +extent/2` around the body and a [GridOrigin.Corner] one `0 .. extent` from it, with
     * no half-cell shift either way, all turned by the body's rotation. Drawing every field centred put
     * a corner-anchored one half a field from where it collides -- the very disagreement this overlay
     * exists to make visible, so it must not reproduce it.
     */
    private fun heightFieldFootprint(out: DebugLines, shape: HeightFieldShape, origin: Vec3f, color: Color) {
        val width = (shape.sampleCount - 1) * shape.scale.x
        val depth = (shape.sampleCount - 1) * shape.scale.z
        val minX: Float
        val minZ: Float
        when (shape.origin) {
            GridOrigin.Centered -> {
                minX = -width * HALF
                minZ = -depth * HALF
            }
            GridOrigin.Corner -> {
                minX = 0f
                minZ = 0f
            }
        }
        val heights = shape.heights
        var lowest = 0f
        if (heights.isNotEmpty()) {
            lowest = heights[0]
            for (index in 1 until heights.size) lowest = minOf(lowest, heights[index])
        }
        val y = lowest * shape.scale.y
        // Corners in order round the edge: (min, min), (max, min), (max, max), (min, max).
        for (index in 0 until FOOTPRINT_CORNERS) {
            val x = if (index == 1 || index == 2) minX + width else minX
            val z = if (index >= 2) minZ + depth else minZ
            val corner = corners[index]
            rotate(x, y, z, corner)
            corner.set(origin.x + corner.x, origin.y + corner.y, origin.z + corner.z)
        }
        for (index in 0 until FOOTPRINT_CORNERS) {
            out.line(corners[index], corners[(index + 1) % FOOTPRINT_CORNERS], color)
        }
    }

    /**
     * The box enclosing a set of `x, y, z` points, which is what a mesh and a hull are drawn as.
     *
     * Not the triangles themselves, and not the hull's own faces. A level mesh is thousands of
     * triangles and a hull's real faces are the backend's to compute, not ours -- while the question
     * this overlay answers about either is almost always "where is it, and is it where the mesh is".
     * Draw the geometry itself if that ever stops being enough; it is a bigger feature than a wireframe.
     */
    private fun pointCloudBounds(out: DebugLines, points: FloatArray, origin: Vec3f, color: Color) {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var index = 0
        while (index + 2 < points.size) {
            minX = minOf(minX, points[index])
            maxX = maxOf(maxX, points[index])
            minY = minOf(minY, points[index + 1])
            maxY = maxOf(maxY, points[index + 1])
            minZ = minOf(minZ, points[index + 2])
            maxZ = maxOf(maxZ, points[index + 2])
            index += VALUES_PER_POINT
        }
        rotate((minX + maxX) * HALF, (minY + maxY) * HALF, (minZ + maxZ) * HALF, offset)
        top.set((maxX - minX) * HALF, (maxY - minY) * HALF, (maxZ - minZ) * HALF)
        centre.set(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z)
        box(out, top, centre, color)
    }

    /** A ring of [CIRCLE_SEGMENTS] lines around [centre], each starting where the last ended. */
    private fun ring(out: DebugLines, centre: Vec3f, radius: Float, axis: Axis, color: Color) {
        ringPoint(centre, radius, axis, 0, a)
        for (step in 1..CIRCLE_SEGMENTS) {
            ringPoint(centre, radius, axis, step, b)
            out.line(a, b, color)
            a.set(b)
        }
    }

    private fun ringPoint(centre: Vec3f, radius: Float, axis: Axis, step: Int, target: Vec3f) {
        val angle = step.toFloat() / CIRCLE_SEGMENTS * 2f * PI.toFloat()
        val u = cos(angle) * radius
        val v = sin(angle) * radius
        when (axis) {
            Axis.X -> rotate(0f, u, v, target)
            Axis.Y -> rotate(u, 0f, v, target)
            Axis.Z -> rotate(u, v, 0f, target)
        }
        target.set(centre.x + target.x, centre.y + target.y, centre.z + target.z)
    }

    /** `(x, y, z)` turned by [rotation] into [target]: [Quat.rotate] without its result. */
    private fun rotate(x: Float, y: Float, z: Float, target: Vec3f) {
        val q = rotation
        val tx = 2f * (q.y * z - q.z * y)
        val ty = 2f * (q.z * x - q.x * z)
        val tz = 2f * (q.x * y - q.y * x)
        target.set(
            x + q.w * tx + (q.y * tz - q.z * ty),
            y + q.w * ty + (q.z * tx - q.x * tz),
            z + q.w * tz + (q.x * ty - q.y * tx),
        )
    }

    private enum class Axis { X, Y, Z }

    /** How finely round shapes are drawn. */
    companion object {
        /** The lines in each ring: enough to read as a circle at arm's length, few enough to draw one per body cheaply. */
        const val CIRCLE_SEGMENTS: Int = 16

        private const val CORNER_COUNT = 8
        private const val FOOTPRINT_CORNERS = 4
        private const val QUARTERS = 4
        private const val HALF = 0.5f

        /** x, y, z per point, in both a mesh's vertices and a hull's points. */
        private const val VALUES_PER_POINT = 3
    }
}
