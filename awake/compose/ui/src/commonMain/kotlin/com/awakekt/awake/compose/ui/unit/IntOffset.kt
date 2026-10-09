/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import kotlin.jvm.JvmInline

/**
 * A 2D integer offset packed into a single 64-bit value to avoid heap allocation.
 *
 * @property packed Both components in one value: [x] in the high 32 bits and [y] in the low 32 bits.
 */
@JvmInline
value class IntOffset internal constructor(val packed: Long) {
    /** The horizontal component. */
    val x: Int get() = (packed shr 32).toInt()

    /** The vertical component. */
    val y: Int get() = (packed and 0xFFFFFFFFL).toInt()

    constructor(x: Int, y: Int) : this((x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL))

    /** The [x] component, for destructuring. */
    operator fun component1(): Int = x

    /** The [y] component, for destructuring. */
    operator fun component2(): Int = y

    override fun toString(): String = "($x, $y)"

    /** Constants for [IntOffset]. */
    companion object {
        /** An offset of zero on both axes. */
        val Zero = IntOffset(0, 0)
    }
}
