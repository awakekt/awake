/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f

/**
 * The first thing a swept shape ran into, from [PhysicsWorld.shapeCast].
 *
 * [normal] is the reason this is not a [RaycastHit]. Collide-and-slide -- the whole of a
 * kinematic character controller -- is projecting the remaining motion onto the plane that
 * stopped it, so a hit without the plane's normal cannot be slid along and the caller is left
 * guessing from geometry it does not have.
 *
 * [fraction] is how far along the sweep the contact happened, in `[0, 1]` of the `from`-to-`to`
 * vector. A fraction rather than a distance because that is what a caller advancing a character
 * actually multiplies by, and it stays meaningful for a zero-length sweep where a distance
 * would not.
 *
 * Construction rejects a non-finite point/normal, a zero normal, or a fraction outside `[0, 1]`.
 * A malformed backend result must fail before a caller uses it to advance a position. It is not
 * a clear path, and no fallback contact direction is invented. Vectors remain mutable; their owner
 * must preserve these invariants after construction.
 *
 * @property handle The body that was struck by the swept shape.
 * @property point World-space position where contact occurred.
 * @property normal Surface normal pointing away from the struck body at [point].
 * @property fraction Normalized distance along the sweep vector in range `[0, 1]`.
 */
data class ShapeCastHit(
    val handle: BodyHandle,
    val point: Vec3f,
    val normal: Vec3f,
    val fraction: Float,
) {
    init {
        require(point.hasFiniteComponents()) { "ShapeCastHit point must be finite: $point" }
        require(normal.hasFiniteComponents()) { "ShapeCastHit normal must be finite: $normal" }
        require(normal.x != 0f || normal.y != 0f || normal.z != 0f) { "ShapeCastHit normal must be nonzero: $normal" }
        require(fraction.isFinite() && fraction in 0f..1f) { "ShapeCastHit fraction must be finite and in [0, 1]: $fraction" }
    }
}

private fun Vec3f.hasFiniteComponents(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
