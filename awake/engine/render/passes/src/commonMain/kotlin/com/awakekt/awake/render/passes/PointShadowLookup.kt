/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs

/** Cubemap face and UV lookup derived from a light-to-fragment direction. */
data class PointShadowLookup(val face: Int, val uv: Vec3f)

/**
 * Selects the six-layer point-shadow face and returns projected UV coordinates in 0..1.
 *
 * Face order matches [pointShadowMatrices]: +X, -X, +Y, -Y, +Z, -Z. The returned z component
 * is the positive radial depth ratio and is intentionally retained for the comparison reference.
 * The signs are the camera-space right/up basis used by [pointShadowMatrices], converted to
 * texture coordinates (V increases down).
 */
fun pointShadowLookup(direction: Vec3f): PointShadowLookup {
    val ax = abs(direction.x)
    val ay = abs(direction.y)
    val az = abs(direction.z)
    require(ax > 0f || ay > 0f || az > 0f) { "point-shadow direction cannot be zero" }
    val (face, major, u, v) = when {
        ax >= ay && ax >= az && direction.x >= 0f -> Quad(0, ax, -direction.z, direction.y)
        ax >= ay && ax >= az -> Quad(1, ax, direction.z, direction.y)
        ay >= ax && ay >= az && direction.y >= 0f -> Quad(2, ay, -direction.x, direction.z)
        ay >= ax && ay >= az -> Quad(3, ay, -direction.x, -direction.z)
        direction.z >= 0f -> Quad(4, az, direction.x, direction.y)
        else -> Quad(5, az, -direction.x, direction.y)
    }
    return PointShadowLookup(face, Vec3f(0.5f * (u / major + 1f), 0.5f * (v / major + 1f), major))
}

private data class Quad(val face: Int, val major: Float, val u: Float, val v: Float)
