/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs
import kotlin.math.round

/** Specification for an infinite or extended editor reference grid. */
data class GridSpec(
    val size: Float = 100f,
    val primaryStep: Float = 1.0f,
    val subStep: Float = 0.1f,
    val showAxisLines: Boolean = true,
)

/**
 * A square reference/floor grid on the XZ plane at a fixed height -- pure geometry helper, zero GPU dependency.
 *
 * Returns `(start, end)` point pairs rather than the renderer's own `LineSegment`, mirroring
 * [Frustum.corners]/[Frustum.EDGES]: points here, and a caller that already depends on both this
 * module and the renderer assembles the line type with its own colour.
 */
object Grid {
    /**
     * Produces `2 * (divisions + 1)` line segments: `divisions + 1` lines parallel to X
     * (each at a fixed Z) and `divisions + 1` lines parallel to Z (each at a fixed X),
     * spanning `[-size / 2, size / 2]` on both axes at height [y] -- a `divisions`-by-
     * `divisions` cell grid, lines crossing at right angles.
     */
    fun lines(
        size: Float,
        divisions: Int,
        y: Float = 0f,
    ): List<Pair<Vec3f, Vec3f>> {
        require(divisions >= 1) { "divisions must be >= 1, was $divisions" }
        val half = size / 2f
        val step = size / divisions
        val result = ArrayList<Pair<Vec3f, Vec3f>>((divisions + 1) * 2)

        // Lines parallel to X, one per Z coordinate.
        for (i in 0..divisions) {
            val z = -half + i * step
            result += Vec3f(-half, y, z) to Vec3f(half, y, z)
        }
        // Lines parallel to Z, one per X coordinate.
        for (i in 0..divisions) {
            val x = -half + i * step
            result += Vec3f(x, y, -half) to Vec3f(x, y, half)
        }

        return result
    }

    /**
     * Intersects a ray starting at [rayOrigin] with direction [rayDir] with the horizontal plane at [y].
     * Returns the world-space intersection point, or null if the ray is parallel to the plane or points away.
     */
    fun intersectGroundPlane(rayOrigin: Vec3f, rayDir: Vec3f, y: Float = 0f): Vec3f? {
        val denom = rayDir.y
        if (abs(denom) < 1e-6f) return null
        val t = (y - rayOrigin.y) / denom
        return if (t >= 0f) rayOrigin + rayDir * t else null
    }

    /** Snaps [value] to the nearest multiple of [step]. */
    fun snapToGrid(value: Float, step: Float): Float {
        if (step <= 0f) return value
        return round(value / step) * step
    }

    /**
     * Generates world-space line pairs for an editor reference grid centered optionally around [center].
     * Emits primary grid lines spaced by [step] spanning `[-extent, extent]` around snapped center.
     */
    fun generateGridLines(
        extent: Float = 50f,
        step: Float = 1.0f,
        center: Vec3f = Vec3f.ZERO,
        y: Float = 0f,
    ): List<Pair<Vec3f, Vec3f>> {
        require(step > 0f) { "step must be > 0, was $step" }
        val cx = snapToGrid(center.x, step)
        val cz = snapToGrid(center.z, step)
        val count = (extent / step).toInt()
        val totalLines = (count * 2 + 1) * 2
        val result = ArrayList<Pair<Vec3f, Vec3f>>(totalLines)

        val minX = cx - count * step
        val maxX = cx + count * step
        val minZ = cz - count * step
        val maxZ = cz + count * step

        // Lines parallel to X (stepping along Z)
        for (i in -count..count) {
            val z = cz + i * step
            result += Vec3f(minX, y, z) to Vec3f(maxX, y, z)
        }
        // Lines parallel to Z (stepping along X)
        for (i in -count..count) {
            val x = cx + i * step
            result += Vec3f(x, y, minZ) to Vec3f(x, y, maxZ)
        }
        return result
    }
}
