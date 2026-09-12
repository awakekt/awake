/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.LineSegment

/**
 * Pure geometry -- turns a [Lens]'s frustum or a world-space [Aabb] into [LineSegment]s for
 * [Renderer.drawDebugLines]. No `World`/`Entity` dependency: a caller (e.g. a
 * `DebugVisualizationSystem`) decides *when* and *for which entities* to call these, this file
 * only decides *what the lines look like*.
 */

/** One line per [Frustum.EDGES] entry, camera/[aspect]'s frustum -- same corner order
 * [Frustum.intersects] already uses. */
fun frustumDebugLines(camera: Lens, aspect: Float, color: Color): List<LineSegment> {
    val corners = Frustum.corners(camera, aspect)
    return Frustum.EDGES.map { (a, b) -> LineSegment(corners[a], corners[b], color) }
}

/** One line per [Aabb.EDGES] entry, [bounds] transformed into world space by [worldMatrix] --
 * mirrors [frustumDebugLines]'s shape for a box instead of a frustum. */
fun boundsDebugLines(bounds: Aabb, worldMatrix: Mat4, color: Color): List<LineSegment> {
    val corners = bounds.transformed(worldMatrix).corners()
    return Aabb.EDGES.map { (a, b) -> LineSegment(corners[a], corners[b], color) }
}

/**
 * Produces 3D aura outline lines around [bounds] transformed by [worldMatrix]:
 * - Luminous corner brackets (arms wrapping each of the 8 corners)
 * - Ground/base circular aura ring
 * - Subtle connecting contour edges
 */
fun objectAuraLines(
    bounds: Aabb,
    worldMatrix: Mat4,
    color: Color,
    includeGroundRing: Boolean = true,
): List<LineSegment> {
    val transformed = bounds.transformed(worldMatrix)
    val corners = transformed.corners()
    val lines = ArrayList<LineSegment>(48)

    // 1. Subtle full edge outlines (35% alpha)
    val subtleColor = color.copy(a = color.a * 0.35f)
    Aabb.EDGES.forEach { (a, b) ->
        lines += LineSegment(corners[a], corners[b], subtleColor)
    }

    // 2. High-intensity corner bracket arms (25% length from each corner)
    val bracketFraction = 0.25f
    Aabb.EDGES.forEach { (a, b) ->
        val ca = corners[a]
        val cb = corners[b]
        val armA = ca + (cb - ca) * bracketFraction
        val armB = cb + (ca - cb) * bracketFraction
        lines += LineSegment(ca, armA, color)
        lines += LineSegment(cb, armB, color)
    }

    // 3. Ground / base circular aura ring
    if (includeGroundRing) {
        val center = (transformed.min + transformed.max) * 0.5f
        val baseY = transformed.min.y
        val rx = (transformed.max.x - transformed.min.x) * 0.55f
        val rz = (transformed.max.z - transformed.min.z) * 0.55f
        val segments = 24
        var prev = Vec3f(center.x + rx, baseY, center.z)
        for (i in 1..segments) {
            val angle = (i * 2.0 * kotlin.math.PI / segments).toFloat()
            val curr = Vec3f(
                center.x + rx * kotlin.math.cos(angle),
                baseY,
                center.z + rz * kotlin.math.sin(angle),
            )
            lines += LineSegment(prev, curr, color)
            prev = curr
        }
    }

    return lines
}

/** A directional-light gizmo at [origin]: one line pointing along [direction] (fixed visual
 * length, not to scale with the scene) plus a small cross of 4 perpendicular segments at
 * [origin] -- reads as a light icon even from an angle where the direction line foreshortens
 * to a point. */
fun lightGizmoLines(origin: Vec3f, direction: Vec3f, color: Color): List<LineSegment> {
    val d = direction.normalized()
    val arrowEnd = origin + d * LIGHT_GIZMO_ARROW_LENGTH
    val reference = if (kotlin.math.abs(d.y) > 0.99f) Vec3f(1f, 0f, 0f) else Vec3f(0f, 1f, 0f)
    val right = d.cross(reference).normalized()
    val up = right.cross(d).normalized()
    val armLength = LIGHT_GIZMO_CROSS_ARM_LENGTH
    return listOf(
        LineSegment(origin, arrowEnd, color),
        LineSegment(origin - right * armLength, origin + right * armLength, color),
        LineSegment(origin - up * armLength, origin + up * armLength, color),
    )
}

private const val LIGHT_GIZMO_ARROW_LENGTH = 3f
private const val LIGHT_GIZMO_CROSS_ARM_LENGTH = 0.5f
