/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import kotlin.math.abs
import kotlin.math.hypot

// The corner geometry offsetPolygon is built from.

internal fun edgeNormal(a: DrawPoint, b: DrawPoint, outwardSign: Float): Pair<Float, Float> {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy)
    if (length <= 0f) return 0f to 0f
    return (dy / length * outwardSign) to (-dx / length * outwardSign)
}

/**
 * tan(half the turn) at a corner that folds inward under an offset of [distance] -- a convex
 * corner when shrinking, a reflex one when growing -- and 0 at any other. Offsetting by d slides
 * each end of an edge along it by d * tan(turn / 2), so an edge of length L folds once d exceeds
 * L / (tanA + tanB).
 */
internal fun foldTangent(prev: DrawPoint, curr: DrawPoint, next: DrawPoint, distance: Float, outwardSign: Float): Float {
    val (pNx, pNy) = edgeNormal(prev, curr, outwardSign)
    val (nNx, nNy) = edgeNormal(curr, next, outwardSign)
    val crossZ = (curr.x - prev.x) * (next.y - curr.y) - (curr.y - prev.y) * (next.x - curr.x)
    val convex = crossZ * outwardSign > 0f
    val cosTurn = (pNx * nNx + pNy * nNy).coerceIn(-1f, 1f)
    return when {
        (distance >= 0f) == convex -> 0f
        // A reversal folds at once: tan(90 degrees).
        cosTurn <= -1f + MITER_EPSILON -> 1f / MITER_EPSILON
        // tan(theta / 2) = sin / (1 + cos), from the normals' cross and dot.
        else -> abs(pNx * nNy - pNy * nNx) / (1f + cosTurn)
    }
}

/** The unit direction a corner moves along, and how far it moves per unit of offset. */
internal fun miterDirection(prevNormal: Pair<Float, Float>, nextNormal: Pair<Float, Float>): Triple<Float, Float, Float> {
    val (prevNx, prevNy) = prevNormal
    val sumX = prevNx + nextNormal.first
    val sumY = prevNy + nextNormal.second
    val sumLength = hypot(sumX, sumY)
    if (sumLength <= 1e-4f) return Triple(prevNx, prevNy, 1f)
    val ux = sumX / sumLength
    val uy = sumY / sumLength
    val cosHalfAngle = prevNx * ux + prevNy * uy
    // A reversing or degenerate join has no stable miter direction. Use a bevel-sized offset
    // instead of allowing 1 / cosHalfAngle to poison the fringe mesh with NaN.
    val miterScale = if (cosHalfAngle > MITER_EPSILON) (1f / cosHalfAngle).coerceAtMost(MAX_MITER_SCALE) else 1f
    return Triple(ux, uy, miterScale)
}
