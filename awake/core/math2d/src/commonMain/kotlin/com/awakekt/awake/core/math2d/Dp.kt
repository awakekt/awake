/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.jvm.JvmInline

/** Device-independent authored size. Pixel conversion belongs to the UI runtime. */
@JvmInline
value class Dp(val value: Float) : Comparable<Dp> {
    operator fun plus(other: Dp): Dp = Dp(value + other.value)
    operator fun minus(other: Dp): Dp = Dp(value - other.value)
    operator fun times(other: Float): Dp = Dp(value * other)
    operator fun div(other: Float): Dp = Dp(value / other)
    operator fun div(other: Int): Dp = Dp(value / other.toFloat())

    override fun compareTo(other: Dp): Int = value.compareTo(other.value)

    /** False for [Unspecified], and for any other NaN that reached here by arithmetic. */
    val isSpecified: Boolean get() = !value.isNaN()

    override fun toString(): String = if (isSpecified) "${value}dp" else "Dp.Unspecified"

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

val Float.dp: Dp get() = Dp(this)
val Int.dp: Dp get() = Dp(toFloat())
