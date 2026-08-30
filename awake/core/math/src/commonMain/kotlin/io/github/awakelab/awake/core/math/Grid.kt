/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

/**
 * A square reference/floor grid on the XZ plane at a fixed height -- the same "pure geometry
 * helper, zero GPU dependency, unit-tested" role [Frustum] plays for the debug-line wireframe.
 *
 * Returns `(start, end)` point pairs rather than the renderer's own `LineSegment`, mirroring
 * [Frustum.corners]/[Frustum.EDGES]: points here, and a caller that already depends on both this
 * module and the renderer assembles the line type with its own colour. `awake:core:math` has no
 * dependencies at all, so returning a render type is not possible without inverting that.
 *
 * No caller today. Kept as debug-rendering surface, not wired to anything.
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
    ): List<Pair<io.github.awakelab.awake.core.math.Vec3f, io.github.awakelab.awake.core.math.Vec3f>> {
        require(divisions >= 1) { "divisions must be >= 1, was $divisions" }
        val half = size / 2f
        val step = size / divisions
        val result =
            ArrayList<Pair<io.github.awakelab.awake.core.math.Vec3f, io.github.awakelab.awake.core.math.Vec3f>>(
                (divisions + 1) * 2,
            )

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
}
