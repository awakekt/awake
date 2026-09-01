/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Vec3f

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
 */
data class ShapeCastHit(
    val handle: BodyHandle,
    val point: Vec3f,
    val normal: Vec3f,
    val fraction: Float,
)
