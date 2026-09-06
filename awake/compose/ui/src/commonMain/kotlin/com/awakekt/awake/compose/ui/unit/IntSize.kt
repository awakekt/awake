/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import kotlin.jvm.JvmInline

/**
 * A 2D integer size packed into a single 64-bit value to avoid heap allocation.
 */
@JvmInline
value class IntSize internal constructor(val packed: Long) {
    val width: Int get() = (packed shr 32).toInt()
    val height: Int get() = (packed and 0xFFFFFFFFL).toInt()

    constructor(width: Int, height: Int) : this((width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL))

    operator fun component1(): Int = width
    operator fun component2(): Int = height

    override fun toString(): String = "${width}x$height"

    companion object {
        val Zero = IntSize(0, 0)
    }
}
