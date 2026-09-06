/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.color.Color
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
import com.awakekt.awake.scene.physics.PhysicsBody
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Enough segments to read as a circle at arm's length, few enough to draw one per body cheaply. */
private const val CIRCLE_SEGMENTS = 16

/** Static world: the things that never move, drawn coolest so they recede. */
private val STATIC_COLOR = Color(r = 0.25f, g = 0.55f, b = 0.95f)

/** Dynamic: what the solver is actually pushing around. */
private val DYNAMIC_COLOR = Color(r = 0.35f, g = 0.95f, b = 0.45f)

/** Kinematic: driven by something outside the simulation, so neither of the above. */
private val KINEMATIC_COLOR = Color(r = 0.95f, g = 0.75f, b = 0.25f)

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
    world.queryEach(Transform::class, PhysicsBody::class) { _, transform, body ->
        val color = when (body.motionType) {
            MotionType.STATIC -> STATIC_COLOR
            MotionType.DYNAMIC -> DYNAMIC_COLOR
            MotionType.KINEMATIC -> KINEMATIC_COLOR
        }
        lines.addShape(body.shape, transform.position, Quat.fromEuler(transform.rotation), color)
    }
    return lines
}

private fun MutableList<LineSegment>.addShape(
    shape: PhysicsShape,
    origin: Vec3f,
    rotation: Quat,
    color: Color,
) {
    when (shape) {
        is BoxShape -> addBox(shape.halfExtents, origin, rotation, color)
        is SphereShape -> addSphere(shape.radius, origin, rotation, color)
        is CapsuleShape -> addCapsule(shape, origin, rotation, color)
        is HeightFieldShape -> addHeightFieldFootprint(shape, origin, color)
        is MeshShape -> addPointCloudBounds(shape.vertices, origin, rotation, color)
        is ConvexHullShape -> addPointCloudBounds(shape.points, origin, rotation, color)
    }
}

/** Twelve edges, each corner rotated into world space first. */
private fun MutableList<LineSegment>.addBox(
    halfExtents: Vec3f,
    origin: Vec3f,
    rotation: Quat,
    color: Color,
) {
    val corners = Array(CORNER_COUNT) { index ->
        val sx = if (index and 1 == 0) -1f else 1f
        val sy = if (index and 2 == 0) -1f else 1f
        val sz = if (index and 4 == 0) -1f else 1f
        origin + rotation.rotate(Vec3f(halfExtents.x * sx, halfExtents.y * sy, halfExtents.z * sz))
    }
    // Corner bits are x, y, z, so a pair differing in exactly one bit is an edge -- which is the
    // whole edge list without writing twelve index pairs out by hand.
    for (a in 0 until CORNER_COUNT) {
        for (bit in 0 until 3) {
            val b = a or (1 shl bit)
            if (b != a) add(LineSegment(corners[a], corners[b], color))
        }
    }
}

private fun MutableList<LineSegment>.addSphere(
    radius: Float,
    origin: Vec3f,
    rotation: Quat,
    color: Color,
) {
    // Three rings rather than a mesh: enough to read as a sphere and to show where it is.
    addRing(origin, rotation, radius, Axis.Y, color)
    addRing(origin, rotation, radius, Axis.X, color)
    addRing(origin, rotation, radius, Axis.Z, color)
}

/**
 * A capsule as its two cap centres, the rings around them and the lines joining them.
 *
 * Deliberately not the rounded ends: the caps are where a capsule differs from a cylinder, and
 * drawing them costs more lines than the diagnostic is worth. The rings sit at the cap centres, so
 * the shape's true extent is half a radius beyond each -- which the vertical lines make obvious.
 */
private fun MutableList<LineSegment>.addCapsule(
    shape: CapsuleShape,
    origin: Vec3f,
    rotation: Quat,
    color: Color,
) {
    val up = rotation.rotate(Vec3f(0f, shape.halfHeight, 0f))
    val top = origin + up
    val bottom = origin - up
    addRing(top, rotation, shape.radius, Axis.Y, color)
    addRing(bottom, rotation, shape.radius, Axis.Y, color)
    // The extremes, so the caps are visible as extent even though they are not drawn as geometry.
    val capOffset = rotation.rotate(Vec3f(0f, shape.radius, 0f))
    add(LineSegment(top + capOffset, bottom - capOffset, color))
    for (quarter in 0 until 4) {
        val angle = quarter * (PI.toFloat() / 2f)
        val offset = rotation.rotate(
            Vec3f(cos(angle) * shape.radius, 0f, sin(angle) * shape.radius),
        )
        add(LineSegment(top + offset, bottom + offset, color))
    }
}

/**
 * The outline of a heightfield's extent, at the height of its lowest sample.
 *
 * A heightfield is laid out from its centre, matching the engine's own convention, so this draws
 * where the collider believes its edges are -- the half-tile disagreements that convention exists
 * to prevent are exactly what this makes visible.
 */
private fun MutableList<LineSegment>.addHeightFieldFootprint(
    shape: HeightFieldShape,
    origin: Vec3f,
    color: Color,
) {
    val halfX = (shape.sampleCount - 1) * shape.scale.x * 0.5f
    val halfZ = (shape.sampleCount - 1) * shape.scale.z * 0.5f
    val y = origin.y + (shape.heights.minOrNull() ?: 0f) * shape.scale.y
    val corners = listOf(
        Vec3f(origin.x - halfX, y, origin.z - halfZ),
        Vec3f(origin.x + halfX, y, origin.z - halfZ),
        Vec3f(origin.x + halfX, y, origin.z + halfZ),
        Vec3f(origin.x - halfX, y, origin.z + halfZ),
    )
    for (index in corners.indices) {
        add(LineSegment(corners[index], corners[(index + 1) % corners.size], color))
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
private fun MutableList<LineSegment>.addPointCloudBounds(
    points: FloatArray,
    origin: Vec3f,
    rotation: Quat,
    color: Color,
) {
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
    val centre = Vec3f((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f)
    val halfExtents = Vec3f((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f)
    addBox(halfExtents, origin + rotation.rotate(centre), rotation, color)
}

/** x, y, z per point, in both a mesh's vertices and a hull's points. */
private const val VALUES_PER_POINT = 3

private enum class Axis { X, Y, Z }

private fun MutableList<LineSegment>.addRing(
    centre: Vec3f,
    rotation: Quat,
    radius: Float,
    axis: Axis,
    color: Color,
) {
    var previous = ringPoint(centre, rotation, radius, axis, 0)
    for (step in 1..CIRCLE_SEGMENTS) {
        val next = ringPoint(centre, rotation, radius, axis, step)
        add(LineSegment(previous, next, color))
        previous = next
    }
}

private fun ringPoint(
    centre: Vec3f,
    rotation: Quat,
    radius: Float,
    axis: Axis,
    step: Int,
): Vec3f {
    val angle = step.toFloat() / CIRCLE_SEGMENTS * 2f * PI.toFloat()
    val u = cos(angle) * radius
    val v = sin(angle) * radius
    val local = when (axis) {
        Axis.X -> Vec3f(0f, u, v)
        Axis.Y -> Vec3f(u, 0f, v)
        Axis.Z -> Vec3f(u, v, 0f)
    }
    return centre + rotation.rotate(local)
}

private const val CORNER_COUNT = 8
