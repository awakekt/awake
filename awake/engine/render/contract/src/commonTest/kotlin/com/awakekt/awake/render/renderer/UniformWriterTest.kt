/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
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

    @Test
    fun writesReusableVec4FieldAtItsDeclaredLayoutOffset() {
        val out = FloatArray(layout.total)

        layout.writeVec4(out, b, 10f, 11f, 12f, 13f)

        assertEquals(listOf(0f, 0f, 0f, 0f, 10f, 11f, 12f, 13f, 0f), out.toList())
    }

    @Test
    fun rejectsWritingAReusableVec4IntoANonVec4Field() {
        assertFailsWith<IllegalArgumentException> {
            layout.writeVec4(FloatArray(layout.total), c, 1f, 2f, 3f, 4f)
        }
    }

    @Test
    fun writesAndReadsVec4ArrayElementsFromDeclaredStride() {
        val array = UniformField("array", GpuDataShape.Vec4, count = 3)
        val arrayLayout = UniformLayout(a, array)
        val out = FloatArray(arrayLayout.total)

        arrayLayout.writeVec4Element(out, array, index = 2, 20f, 21f, 22f, 23f)

        assertEquals(listOf(20f, 21f, 22f, 23f), out.takeLast(4))
        assertEquals(20f, arrayLayout.readVec4(out, array, index = 2).x)
        assertEquals(23f, arrayLayout.readVec4(out, array, index = 2).w)
    }

    @Test
    fun writesVec4ArrayElementsIntoAFieldSizedBuffer() {
        val array = UniformField("array", GpuDataShape.Vec4, count = 3)
        val out = FloatArray(array.floats)

        array.writeVec4Element(out, index = 2, 20f, 21f, 22f, 23f)

        assertEquals(listOf(20f, 21f, 22f, 23f), out.takeLast(4))
    }

    @Test
    fun writesMat4ArrayElementsIntoAFieldSizedBuffer() {
        val array = UniformField("matrices", GpuDataShape.Mat4, count = 2)
        val matrix = Mat4().apply { data[12] = 42f }
        val out = FloatArray(array.floats)

        array.writeMat4Element(out, index = 1, matrix)

        assertEquals(42f, out[16 + 12])
    }

    @Test
    fun rejectsVec4ArrayElementOutsideDeclaredCount() {
        val array = UniformField("array", GpuDataShape.Vec4, count = 2)
        val arrayLayout = UniformLayout(array)

        assertFailsWith<IllegalArgumentException> {
            arrayLayout.writeVec4Element(FloatArray(arrayLayout.total), array, index = 2, 0f, 0f, 0f, 0f)
        }
    }
}
