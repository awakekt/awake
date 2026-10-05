/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.jvm.JvmInline
import kotlin.math.PI

/**
 * Type-safe representation of a planar angle with conversions between degrees and radians.
 *
 * @param value The internal angle magnitude in degrees.
 */
@JvmInline
value class Angle(private val value: Float) {
    /**
     * Factory methods for creating [Angle] instances.
     */
    companion object {
        private const val DEGREES_TO_RADIANS = PI.toFloat() / 180.0f
        private const val RADIANS_TO_DEGREES = 180.0f / PI.toFloat()

        /**
         * Creates an [Angle] from a measurement in degrees.
         *
         * @param degrees The angle magnitude in degrees.
         * @return The [Angle] instance.
         */
        fun fromDegrees(degrees: Float): Angle = Angle(degrees)

        /**
         * Creates an [Angle] from a measurement in radians.
         *
         * @param radians The angle magnitude in radians.
         * @return The [Angle] instance.
         */
        fun fromRadians(radians: Float): Angle = Angle(radians * RADIANS_TO_DEGREES)
    }

    /**
     * Converts this angle to degrees.
     *
     * @return The angle magnitude in degrees.
     */
    fun toDegrees(): Float = value

    /**
     * Converts this angle to radians.
     *
     * @return The angle magnitude in radians.
     */
    fun toRadians(): Float = value * DEGREES_TO_RADIANS
}

/** Reads a degree literal as degrees. Identity -- present so call sites can state the unit. */
val Int.angleDeg: Float
    get() = Angle(this.toFloat()).toDegrees()

/** Converts a degree literal to radians, e.g. `45.angleRad`. */
val Int.angleRad: Float
    get() = Angle(this.toFloat()).toRadians()

/** Reads a degree literal as degrees. Identity -- present so call sites can state the unit. */
val Float.angleDeg: Float
    get() = Angle(this).toDegrees()

/** Converts a degree literal to radians, e.g. `45f.angleRad`. */
val Float.angleRad: Float
    get() = Angle(this).toRadians()
