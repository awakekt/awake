/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.unit

import kotlin.jvm.JvmInline

/**
 * A 2D integer offset packed into a single 64-bit value to avoid heap allocation.
 */
@JvmInline
value class IntOffset internal constructor(val packed: Long) {
    val x: Int get() = (packed shr 32).toInt()
    val y: Int get() = (packed and 0xFFFFFFFFL).toInt()

    constructor(x: Int, y: Int) : this((x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL))

    operator fun component1(): Int = x
    operator fun component2(): Int = y

    override fun toString(): String = "($x, $y)"

    companion object {
        val Zero = IntOffset(0, 0)
    }
}
