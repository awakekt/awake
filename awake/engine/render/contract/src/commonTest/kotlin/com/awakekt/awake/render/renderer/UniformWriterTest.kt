/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UniformWriterTest {

    private val a = UniformField("a", GpuDataShape.Vec4)
    private val b = UniformField("b", GpuDataShape.Vec4)
    private val c = UniformField("c", GpuDataShape.Float)
    private val layout = UniformLayout(a, b, c)

    @Test
    fun writesFieldsInOrderIntoOneBuffer() {
        val out = UniformWriter(layout)
            .put(floatArrayOf(1f, 2f, 3f, 4f), a)
            .put(floatArrayOf(5f, 6f, 7f, 8f), b)
            .put(floatArrayOf(9f), c)
            .build()
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), out.toList())
    }

    @Test
    fun acceptsOneWriteSpanningSeveralAdjacentFields() {
        // How a packer that emits a contiguous group is written -- e.g. the scene light's 8
        // floats covering both LightDirection and LightColor.
        val out = UniformWriter(layout)
            .put(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f), a, b)
            .put(floatArrayOf(9f), c)
            .build()
        assertEquals(9, out.size)
        assertEquals(5f, out[4])
    }

    @Test
    fun rejectsFieldsWrittenOutOfOrder() {
        // The bug this exists for: right total, wrong shape. Hand-concatenation could not catch it.
        val error = assertFailsWith<IllegalArgumentException> {
            UniformWriter(layout)
                .put(floatArrayOf(5f, 6f, 7f, 8f), b)
                .put(floatArrayOf(1f, 2f, 3f, 4f), a)
        }
        assertEquals(true, error.message?.contains("'a'") == true)
    }

    @Test
    fun rejectsAWriteThatIsNotTheFieldsFloatCount() {
        assertFailsWith<IllegalArgumentException> {
            UniformWriter(layout).put(floatArrayOf(1f, 2f), a)
        }
    }

    @Test
    fun rejectsAnIncompleteBlock() {
        val error = assertFailsWith<IllegalArgumentException> {
            UniformWriter(layout).put(floatArrayOf(1f, 2f, 3f, 4f), a).build()
        }
        assertEquals(true, error.message?.contains("missing") == true)
    }

    @Test
    fun rejectsWritingPastTheLayout() {
        assertFailsWith<IllegalArgumentException> {
            UniformWriter(layout)
                .put(floatArrayOf(1f, 2f, 3f, 4f), a)
                .put(floatArrayOf(5f, 6f, 7f, 8f), b)
                .put(floatArrayOf(9f), c)
                .put(floatArrayOf(10f), c)
        }
    }
}
