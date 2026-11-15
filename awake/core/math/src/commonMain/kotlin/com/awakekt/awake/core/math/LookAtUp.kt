/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs

/**
 * The up vector a view looking along [forward] is built with: [up] itself, or, when [up] is parallel
 * to [forward] or zero, +Z for a view looking straight up or down and +Y otherwise.
 *
 * A camera looking straight up or down with the default +Y up has no side vector: `forward x up` is
 * zero. Its view matrix then collapses every point onto one, and its frustum has no planes, so
 * culling admitted everything a capture camera pointed up could never see (#1621). The shadow
 * cascades pick their up the same way for a near-vertical light.
 *
 * Returns [up] itself, allocating nothing, whenever it is usable.
 */
internal fun lookAtUp(forward: Vec3f, up: Vec3f): Vec3f {
    val cx = forward.y * up.z - forward.z * up.y
    val cy = forward.z * up.x - forward.x * up.z
    val cz = forward.x * up.y - forward.y * up.x
    val upLengthSquared = up.x * up.x + up.y * up.y + up.z * up.z
    if (cx * cx + cy * cy + cz * cz > PARALLEL_EPSILON * upLengthSquared) return up
    return if (abs(forward.y) > VERTICAL_LIMIT) Vec3f(0f, 0f, 1f) else Vec3f(0f, 1f, 0f)
}

// Squared sine of the angle between forward and up below which they count as parallel (~0.0006 degrees):
// an up a thousandth off parallel is still the caller's.
private const val PARALLEL_EPSILON = 1e-10f

// Above this |forward.y| the view is near-vertical, and +Y would be parallel to it as well.
private const val VERTICAL_LIMIT = 0.99f
