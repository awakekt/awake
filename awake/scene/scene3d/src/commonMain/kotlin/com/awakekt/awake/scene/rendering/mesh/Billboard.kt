/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * [world]'s position and scale, turned to face [lens]: the mesh's local +Z points back at the
 * eye, +X along the camera's right and +Y along its up. [world]'s own rotation is dropped.
 */
internal fun billboardMatrix(world: Mat4, lens: Lens, destination: Mat4 = Mat4()): Mat4 {
    // Plain floats rather than Vec3f arithmetic: this runs once per billboard per frame.
    val eye = lens.eye
    val center = lens.center
    val camUp = lens.up
    var fx = center.x - eye.x
    var fy = center.y - eye.y
    var fz = center.z - eye.z
    val forwardLength = sqrt(fx * fx + fy * fy + fz * fz)
    if (forwardLength != 0f) {
        fx /= forwardLength
        fy /= forwardLength
        fz /= forwardLength
    }
    val upLength = sqrt(camUp.x * camUp.x + camUp.y * camUp.y + camUp.z * camUp.z)
    val upScale = if (upLength != 0f) 1f / upLength else 1f
    val alongUp = fx * camUp.x * upScale + fy * camUp.y * upScale + fz * camUp.z * upScale
    // Looking straight along the camera's up leaves no right vector; any perpendicular axis does.
    val parallel = abs(alongUp) > PARALLEL_LIMIT
    val hx = if (parallel) 0f else camUp.x
    val hy = if (parallel) 0f else camUp.y
    val hz = if (parallel) 1f else camUp.z

    var rx = fy * hz - fz * hy
    var ry = fz * hx - fx * hz
    var rz = fx * hy - fy * hx
    val rightLength = sqrt(rx * rx + ry * ry + rz * rz)
    if (rightLength != 0f) {
        rx /= rightLength
        ry /= rightLength
        rz /= rightLength
    }
    val ux = ry * fz - rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy - ry * fx

    val m = world.data
    val sx = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
    val sy = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
    val sz = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
    val out = destination.data
    out[0] = rx * sx
    out[1] = ry * sx
    out[2] = rz * sx
    out[3] = 0f
    out[4] = ux * sy
    out[5] = uy * sy
    out[6] = uz * sy
    out[7] = 0f
    out[8] = -fx * sz
    out[9] = -fy * sz
    out[10] = -fz * sz
    out[11] = 0f
    out[12] = m[12]
    out[13] = m[13]
    out[14] = m[14]
    out[15] = 1f
    return destination
}

private const val PARALLEL_LIMIT = 0.999f
