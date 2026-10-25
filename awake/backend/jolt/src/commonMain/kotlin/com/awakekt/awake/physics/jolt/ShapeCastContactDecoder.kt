/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.InvalidShapeCastResultException
import com.awakekt.awake.physics.ShapeCastHit
import kotlin.math.sqrt

/** Shared conversion for JNI, JoltC and JS, with one fallback diagnostic per world. */
internal class ShapeCastContactDecoder {
    private var reportedFallback = false
    private val logger = Logger("JoltShapeCast")

    @Suppress("LongParameterList") // Raw backend contact fields and the query that produced them.
    fun decode(handle: BodyHandle, point: Vec3f, axisX: Float, axisY: Float, axisZ: Float, fraction: Float, from: Vec3f, to: Vec3f): ShapeCastHit {
        val length = magnitude(axisX.toDouble(), axisY.toDouble(), axisZ.toDouble())
        val normal: Vec3f
        val travel: Float
        if (length.isFinite() && length > MIN_AXIS_LENGTH) {
            normal = Vec3f((-axisX / length).toFloat(), (-axisY / length).toFloat(), (-axisZ / length).toFloat())
            travel = if (fraction in -FRACTION_EPSILON..1f + FRACTION_EPSILON) fraction.coerceIn(0f, 1f) else fraction
        } else {
            val dx = from.x.toDouble() - to.x
            val dy = from.y.toDouble() - to.y
            val dz = from.z.toDouble() - to.z
            val distance = magnitude(dx, dy, dz)
            if (!distance.isFinite() || distance == 0.0) {
                throw InvalidShapeCastResultException("Degenerate cast axis=($axisX,$axisY,$axisZ), no usable direction: from=$from to=$to")
            }
            normal = Vec3f((dx / distance).toFloat(), (dy / distance).toFloat(), (dz / distance).toFloat())
            travel = 0f // Uncertain contact blocks at the start; it must never become a miss.
            if (!reportedFallback) {
                reportedFallback = true
                logger.warn { "Blocked degenerate contact body=$handle axis=($axisX,$axisY,$axisZ) fraction=$fraction from=$from to=$to" }
            }
        }
        return ShapeCastHit(handle, point, normal, travel)
    }

    private fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    private companion object {
        const val MIN_AXIS_LENGTH = 1e-12
        const val FRACTION_EPSILON = 1e-6f
    }
}
