/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * World-space wireframes of what physics is actually colliding against.
 *
 * The shape a body was built with, at the pose the simulation has it in -- which is the whole
 * point, because the collider and the mesh drawn for it are two different things and every bug
 * that matters lives in the gap between them. A capsule sunk into terrain, a heightfield offset by
 * half a tile, a box whose collider never moved with its transform: all of them look like
 * rendering faults until this is switched on.
 *
 * Returns lines rather than drawing them, for the reason `navGridDebugLines` gives:
 * `Renderer.drawDebugLines` replaces the frame's line buffer instead of appending, so a caller
 * with more than one source has to merge them itself or silently keep only the last.
 * [PhysicsDebugSource] draws the same lines through the debug registry, with no list and no
 * allocation per frame.
 *
 * Coloured by motion type rather than by layer. Motion type is what explains a body behaving
 * unexpectedly -- static things that should fall, dynamic things that should not -- and layers are
 * a matrix rather than a colour.
 *
 * A [HeightFieldShape] is drawn as its footprint rather than its samples: a terrain of any size is
 * thousands of quads, and the useful question about a heightfield collider is almost always where
 * its edges are, not what its middle looks like.
 */
fun physicsDebugLines(world: World): List<LineSegment> {
    val lines = mutableListOf<LineSegment>()
    PhysicsDebugWriter().write(world) { a, b, color -> lines += LineSegment(a.copy(), b.copy(), color) }
    return lines
}

/**
 * Writes every body's collider wireframe as lines: the one writer behind [physicsDebugLines] and
 * [PhysicsDebugSource].
 *
 * It keeps its own rotation and points and reuses them for every line, so writing allocates
 * nothing; a [DebugLines] that keeps a line copies its two points. Frame thread only.
 */
internal class PhysicsDebugWriter {
    private val rotation = Quat()
    private val corners = Array(CORNER_COUNT) { Vec3f(0f, 0f, 0f) }
    private val a = Vec3f(0f, 0f, 0f)
    private val b = Vec3f(0f, 0f, 0f)
    private val top = Vec3f(0f, 0f, 0f)
    private val bottom = Vec3f(0f, 0f, 0f)
    private val offset = Vec3f(0f, 0f, 0f)
    private val centre = Vec3f(0f, 0f, 0f)

    /** Writes the wireframe of every entity with a [Transform] and a [PhysicsBody] into [out]. */
    fun write(world: World, out: DebugLines) {
        world.queryEach(Transform::class, PhysicsBody::class) { _, transform, body ->
            setFromEuler(transform.rotation)
            shape(out, body.shape, transform.position, colorOf(body.motionType))
        }
    }

    private fun shape(out: DebugLines, shape: PhysicsShape, origin: Vec3f, color: Color) {
        when (shape) {
            is BoxShape -> box(out, shape.halfExtents, origin, color)
            // Three rings rather than a mesh: enough to read as a sphere and to show where it is.
            is SphereShape -> {
                ring(out, origin, shape.radius, Axis.Y, color)
                ring(out, origin, shape.radius, Axis.X, color)
                ring(out, origin, shape.radius, Axis.Z, color)
            }
            is CapsuleShape -> capsule(out, shape, origin, color)
            is HeightFieldShape -> heightFieldFootprint(out, shape, origin, color)
            is MeshShape -> pointCloudBounds(out, shape.vertices, origin, color)
            is ConvexHullShape -> pointCloudBounds(out, shape.points, origin, color)
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
     * the shape's true extent is half a radius beyond each -- which the vertical lines make obvious.
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
     * A heightfield is laid out from its centre, matching the engine's own convention, so this draws
     * where the collider believes its edges are -- the half-tile disagreements that convention exists
     * to prevent are exactly what this makes visible.
     */
    private fun heightFieldFootprint(out: DebugLines, shape: HeightFieldShape, origin: Vec3f, color: Color) {
        val halfX = (shape.sampleCount - 1) * shape.scale.x * 0.5f
        val halfZ = (shape.sampleCount - 1) * shape.scale.z * 0.5f
        val heights = shape.heights
        var lowest = 0f
        if (heights.isNotEmpty()) {
            lowest = heights[0]
            for (index in 1 until heights.size) lowest = minOf(lowest, heights[index])
        }
        val y = origin.y + lowest * shape.scale.y
        corners[0].set(origin.x - halfX, y, origin.z - halfZ)
        corners[1].set(origin.x + halfX, y, origin.z - halfZ)
        corners[2].set(origin.x + halfX, y, origin.z + halfZ)
        corners[3].set(origin.x - halfX, y, origin.z + halfZ)
        for (index in 0 until FOOTPRINT_CORNERS) {
            out.line(corners[index], corners[(index + 1) % FOOTPRINT_CORNERS], color)
        }
    }

    /**
     * The box enclosing a set of `x, y, z` points, which is what a mesh and a hull are drawn as.
     *
     * Not the triangles themselves, and not the hull's own faces. A level mesh is thousands of
     * triangles and a hull's real faces are Jolt's to compute, not ours -- while the question this
     * overlay answers about either is almost always "where is it, and is it where the mesh is". Draw
     * the geometry itself if that ever stops being enough; it is a bigger feature than a wireframe.
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
        rotate((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f, offset)
        top.set((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f)
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

    /**
     * [rotation] set to [Quat.fromEuler] of [radians] without its five quaternions: the axis rotations
     * X = (sin hx, 0, 0, cos hx), Y = (0, sin hy, 0, cos hy) and Z = (0, 0, sin hz, cos hz), multiplied
     * as `(X * Y) * Z` by the formula [Quat.times] uses, with the terms that are zero left out.
     */
    private fun setFromEuler(radians: Vec3f) {
        val sx = sin(radians.x * HALF)
        val cx = cos(radians.x * HALF)
        val sy = sin(radians.y * HALF)
        val cy = cos(radians.y * HALF)
        val sz = sin(radians.z * HALF)
        val cz = cos(radians.z * HALF)
        // X * Y.
        val x = cy * sx
        val y = sy * cx
        val z = -sy * sx
        val w = cy * cx
        // (X * Y) * Z.
        rotation.x = cz * x - sz * y
        rotation.y = cz * y + sz * x
        rotation.z = cz * z + sz * w
        rotation.w = cz * w - sz * z
    }

    private enum class Axis { X, Y, Z }

    companion object {
        /** The colour a body of [motionType] is drawn in. */
        fun colorOf(motionType: MotionType): Color = when (motionType) {
            MotionType.STATIC -> PhysicsDebugSource.STATIC
            MotionType.DYNAMIC -> PhysicsDebugSource.DYNAMIC
            MotionType.KINEMATIC -> PhysicsDebugSource.KINEMATIC
        }

        /** Enough segments to read as a circle at arm's length, few enough to draw one per body cheaply. */
        const val CIRCLE_SEGMENTS = 16

        private const val CORNER_COUNT = 8
        private const val FOOTPRINT_CORNERS = 4
        private const val QUARTERS = 4
        private const val HALF = 0.5f

        /** x, y, z per point, in both a mesh's vertices and a hull's points. */
        private const val VALUES_PER_POINT = 3
    }
}
