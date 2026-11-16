/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** [putFloats] writes what `copyInto` would, and returns the offset after it (#1621). */
class VectorPackingTest {
    @Test
    fun putFloatsWritesAtTheOffsetAndReturnsTheNextOne() {
        val out = FloatArray(6) { -1f }
        val next = out.putFloats(1, floatArrayOf(1f, 2f, 3f))
        assertEquals(4, next)
        assertContentEquals(floatArrayOf(-1f, 1f, 2f, 3f, -1f, -1f), out)
        assertEquals(4, out.putFloats(4, FloatArray(0)), "nothing to write")
    }

    @Test
    fun aMatrixPacksAsCopyIntoWouldPackIt() {
        val model = Mat4().translate(1f, 2f, 3f)
        val expected = FloatArray(32).also { model.data.copyInto(it, 16) }
        assertContentEquals(expected, FloatArray(32).also { it.putFloats(16, model.data) })
    }
}
