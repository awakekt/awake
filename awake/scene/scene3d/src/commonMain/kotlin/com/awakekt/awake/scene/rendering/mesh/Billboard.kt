/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * [world]'s position and scale, turned to face [lens]: the mesh's local +Z points back at the
 * eye, +X along the camera's right and +Y along its up. [world]'s own rotation is dropped.
 */
internal fun billboardMatrix(world: Mat4, lens: Lens): Mat4 {
    val forward = (lens.center - lens.eye).normalized()
    // Looking straight along the camera's up leaves no right vector; any perpendicular axis does.
    val upHint = if (abs(forward.dot(lens.up.normalized())) > PARALLEL_LIMIT) Vec3f(0f, 0f, 1f) else lens.up
    val right = forward.cross(upHint).normalized()
    val up = right.cross(forward)
    val m = world.data
    val sx = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
    val sy = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
    val sz = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
    return Mat4().also {
        val out = it.data
        out[0] = right.x * sx
        out[1] = right.y * sx
        out[2] = right.z * sx
        out[4] = up.x * sy
        out[5] = up.y * sy
        out[6] = up.z * sy
        out[8] = -forward.x * sz
        out[9] = -forward.y * sz
        out[10] = -forward.z * sz
        out[12] = m[12]
        out[13] = m[13]
        out[14] = m[14]
        out[15] = 1f
    }
}

private const val PARALLEL_LIMIT = 0.999f
