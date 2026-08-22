// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.math2d

import kotlin.jvm.JvmInline

/** Device-independent authored size. Pixel conversion belongs to the UI runtime. */
@JvmInline
value class Dp(val value: Float) {
    operator fun plus(other: Dp): Dp = Dp(value + other.value)
    operator fun minus(other: Dp): Dp = Dp(value - other.value)
    operator fun times(other: Float): Dp = Dp(value * other)
    operator fun div(other: Float): Dp = Dp(value / other)
}

val Float.dp: Dp get() = Dp(this)
val Int.dp: Dp get() = Dp(toFloat())
