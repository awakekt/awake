/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.times
import kotlin.math.PI

/** The six light-space views needed to render one point light into a cubemap. */
data class PointShadowMatrices(
    val position: Vec3f,
    val nearPlane: Float,
    val farPlane: Float,
    val viewProjections: List<Mat4>,
)

/**
 * Builds a cubemap-compatible point-shadow projection without involving a backend.
 *
 * The projection is 90 degrees and aspect one for every face. [clipSpace] is supplied by the
 * renderer, exactly as for camera and directional-shadow matrices; geometry never bakes NDC
 * conventions. Face order is +X, -X, +Y, -Y, +Z, -Z.
 */
fun pointShadowMatrices(
    position: Vec3f,
    range: Float,
    clipSpace: ClipSpace,
    nearPlane: Float = DEFAULT_POINT_SHADOW_NEAR,
): PointShadowMatrices {
    require(range > nearPlane) { "point-shadow range must exceed near plane: $range <= $nearPlane" }
    val projection = Mat4.perspective(
        fovY = (PI / 2.0).toFloat(),
        aspect = 1f,
        near = nearPlane,
        far = range,
        clipSpace = clipSpace,
    ).also { if (clipSpace.flipY) it.m11 *= -1f }
    val faces = arrayOf(
        Face(Vec3f.RIGHT, Vec3f.DOWN),
        Face(Vec3f.LEFT, Vec3f.DOWN),
        Face(Vec3f.UP, Vec3f.FORWARD),
        Face(Vec3f.DOWN, Vec3f.BACK),
        Face(Vec3f.BACK, Vec3f.DOWN),
        Face(Vec3f.FORWARD, Vec3f.DOWN),
    )
    val matrices = faces.map { face ->
        val target = position + face.direction
        Mat4.setLookAt(position, target, face.up) * projection
    }
    return PointShadowMatrices(position, nearPlane, range, matrices)
}

private data class Face(val direction: Vec3f, val up: Vec3f)

private const val DEFAULT_POINT_SHADOW_NEAR = 0.05f
