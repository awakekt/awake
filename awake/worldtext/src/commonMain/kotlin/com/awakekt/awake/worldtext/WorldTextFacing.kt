/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import kotlin.math.sqrt

/**
 * Sets this matrix to [placement]'s position and per-axis scale, turned to face [camera]: its +X the
 * camera's right, +Y the camera's up and +Z back toward the camera, so text in its XY plane reads
 * upright and square to the view. Allocates nothing.
 */
fun Mat4.setFacing(placement: Mat4, camera: Lens): Mat4 {
    var fx = camera.center.x - camera.eye.x
    var fy = camera.center.y - camera.eye.y
    var fz = camera.center.z - camera.eye.z
    val forward = length(fx, fy, fz)
    fx /= forward
    fy /= forward
    fz /= forward
    // right = forward x up
    var rx = fy * camera.up.z - fz * camera.up.y
    var ry = fz * camera.up.x - fx * camera.up.z
    var rz = fx * camera.up.y - fy * camera.up.x
    val right = length(rx, ry, rz)
    rx /= right
    ry /= right
    rz /= right
    // up = right x forward
    val ux = ry * fz - rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy - ry * fx
    val sx = length(placement.m00, placement.m10, placement.m20)
    val sy = length(placement.m01, placement.m11, placement.m21)
    val sz = length(placement.m02, placement.m12, placement.m22)
    identity()
    m00 = rx * sx
    m10 = ry * sx
    m20 = rz * sx
    m01 = ux * sy
    m11 = uy * sy
    m21 = uz * sy
    m02 = -fx * sz
    m12 = -fy * sz
    m22 = -fz * sz
    m03 = placement.m03
    m13 = placement.m13
    m23 = placement.m23
    return this
}

private fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
