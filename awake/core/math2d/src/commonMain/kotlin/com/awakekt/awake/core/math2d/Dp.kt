/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.jvm.JvmInline

/**
 * Device-independent authored size. Pixel conversion belongs to the UI runtime.
 *
 * @property value Authored density-independent pixel magnitude.
 */
@JvmInline
value class Dp(val value: Float) : Comparable<Dp> {
    /** Adds [other] to this density-independent dimension. */
    operator fun plus(other: Dp): Dp = Dp(value + other.value)

    /** Subtracts [other] from this density-independent dimension. */
    operator fun minus(other: Dp): Dp = Dp(value - other.value)

    /** Multiplies this density-independent dimension by [other] scalar factor. */
    operator fun times(other: Float): Dp = Dp(value * other)

    /** Divides this density-independent dimension by [other] scalar divisor. */
    operator fun div(other: Float): Dp = Dp(value / other)

    /** Divides this density-independent dimension by [other] integer divisor. */
    operator fun div(other: Int): Dp = Dp(value / other.toFloat())

    override fun compareTo(other: Dp): Int = value.compareTo(other.value)

    /** False for [Unspecified], and for any other NaN that reached here by arithmetic. */
    val isSpecified: Boolean get() = !value.isNaN()

    override fun toString(): String = if (isSpecified) "${value}dp" else "Dp.Unspecified"

    /**
     * Sentinel values and factory constants for [Dp].
     */
    companion object {
        /**
         * "No value", so an optional [Dp] parameter needs no boxing.
         *
         * NaN rather than a null `Dp?`: this is a value class over a Float, and making it nullable
         * boxes it at every call site that omits the argument -- which for `widthIn(max = ...)` is
         * most of them, on a per-frame path. Compose uses the same sentinel for the same reason.
         */
        val Unspecified: Dp = Dp(Float.NaN)
    }
}

/** Converts this [Float] value to density-independent pixels ([Dp]). */
val Float.dp: Dp get() = Dp(this)

/** Converts this [Int] value to density-independent pixels ([Dp]). */
val Int.dp: Dp get() = Dp(toFloat())
