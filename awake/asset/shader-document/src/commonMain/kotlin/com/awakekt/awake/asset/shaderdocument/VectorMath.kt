/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlin.math.max
import kotlin.math.sqrt

// Component-by-component arithmetic on folded constants, in WGSL's terms: a one-component operand
// applies to every component of the other.

internal inline fun mapComponents(value: FloatArray, f: (Float) -> Float): FloatArray = FloatArray(value.size) { f(value[it]) }

/** [a] and [b] combined component by component, a one-component side applying to every component. */
internal inline fun zipComponents(a: FloatArray, b: FloatArray, f: (Float, Float) -> Float): FloatArray =
    FloatArray(max(a.size, b.size)) { i -> f(a[if (a.size == 1) 0 else i], b[if (b.size == 1) 0 else i]) }

internal fun dotOf(a: FloatArray, b: FloatArray): Float = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()

internal fun lengthOf(value: FloatArray): Float = sqrt(dotOf(value, value))

internal fun crossOf(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
)

/**
 * WGSL's Hermite step. WGSL leaves it undefined when `low` is not below `high`, so a constant that
 * asks for that folds to NaN and is rejected like any other value that is not finite.
 */
internal fun smoothstepOf(low: FloatArray, high: FloatArray, x: FloatArray): FloatArray {
    val size = maxOf(low.size, high.size, x.size)
    return FloatArray(size) { i ->
        val lo = low[if (low.size == 1) 0 else i]
        val hi = high[if (high.size == 1) 0 else i]
        if (lo >= hi) {
            Float.NaN
        } else {
            val t = ((x[if (x.size == 1) 0 else i] - lo) / (hi - lo)).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }
    }
}

/** `a + (b - a) * t`. */
internal fun mixOf(a: FloatArray, b: FloatArray, t: FloatArray): FloatArray {
    val delta = zipComponents(b, a) { x, y -> x - y }
    return zipComponents(a, zipComponents(delta, t) { d, s -> d * s }) { x, d -> x + d }
}
