/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.imagepass.ImagePassRect

/** Below this, a corner is at or behind the camera plane, and its screen position means nothing. */
private const val MIN_CLIP_W = 1e-6f

/**
 * Where a world box falls on screen, in texture coordinates from the top left (#1709): the rectangle
 * around its eight corners under [viewProjection], as the scene's shaders read a screen position
 * (`ndcToUv`, which only [ClipSpace.flipY] changes). It may reach past 0 and 1. Null when a corner is
 * at or behind the camera plane, where a box can cover any part of the screen.
 *
 * [box] holds min xyz then max xyz, from [at].
 */
internal fun screenBounds(box: FloatArray, at: Int, viewProjection: Mat4, clipSpace: ClipSpace): ImagePassRect? {
    val m = viewProjection.data
    var minU = Float.POSITIVE_INFINITY
    var minV = Float.POSITIVE_INFINITY
    var maxU = Float.NEGATIVE_INFINITY
    var maxV = Float.NEGATIVE_INFINITY
    for (corner in 0 until CORNERS) {
        val x = box[at + if (corner and 1 == 0) 0 else 3]
        val y = box[at + if (corner and 2 == 0) 1 else 4]
        val z = box[at + if (corner and 4 == 0) 2 else 5]
        // Column-major, as the shaders multiply it: clip = M * (x, y, z, 1).
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (w <= MIN_CLIP_W) return null
        val ndcX = (m[0] * x + m[4] * y + m[8] * z + m[12]) / w
        val ndcY = (m[1] * x + m[5] * y + m[9] * z + m[13]) / w
        val u = (ndcX + 1f) * HALF
        val v = if (clipSpace.flipY) (ndcY + 1f) * HALF else (1f - ndcY) * HALF
        minU = minOf(minU, u)
        maxU = maxOf(maxU, u)
        minV = minOf(minV, v)
        maxV = maxOf(maxV, v)
    }
    return ImagePassRect(minU, minV, maxU - minU, maxV - minV)
}

/**
 * Allocation-free counterpart of [screenBounds] that writes `(minU, minV, maxU, maxV)` into
 * [out] starting at [outAt] and returns `false` when any corner is at or behind the camera plane.
 */
@Suppress("LongParameterList")
internal fun writeScreenBounds(
    box: FloatArray,
    at: Int,
    viewProjection: Mat4,
    clipSpace: ClipSpace,
    out: FloatArray,
    outAt: Int,
): Boolean {
    val m = viewProjection.data
    var minU = Float.POSITIVE_INFINITY
    var minV = Float.POSITIVE_INFINITY
    var maxU = Float.NEGATIVE_INFINITY
    var maxV = Float.NEGATIVE_INFINITY
    for (corner in 0 until CORNERS) {
        val x = box[at + if (corner and 1 == 0) 0 else 3]
        val y = box[at + if (corner and 2 == 0) 1 else 4]
        val z = box[at + if (corner and 4 == 0) 2 else 5]
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (w <= MIN_CLIP_W) return false
        val ndcX = (m[0] * x + m[4] * y + m[8] * z + m[12]) / w
        val ndcY = (m[1] * x + m[5] * y + m[9] * z + m[13]) / w
        val u = (ndcX + 1f) * HALF
        val v = if (clipSpace.flipY) (ndcY + 1f) * HALF else (1f - ndcY) * HALF
        minU = minOf(minU, u)
        maxU = maxOf(maxU, u)
        minV = minOf(minV, v)
        maxV = maxOf(maxV, v)
    }
    out[outAt] = minU
    out[outAt + 1] = minV
    out[outAt + 2] = maxU
    out[outAt + 3] = maxV
    return true
}

private const val CORNERS = 8
private const val HALF = 0.5f
