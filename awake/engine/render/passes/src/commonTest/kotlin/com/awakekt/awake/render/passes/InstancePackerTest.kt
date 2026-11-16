/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * [InstancePacker]'s writer and source are interfaces with a primitive `Int` (#1621); the released
 * function-typed constructor and indexed `pack` stay for code compiled against them, and pack the
 * same floats.
 */
class InstancePackerTest {
    private val items = listOf(Vec4(1f, 2f, 3f, 4f), Vec4(5f, 6f, 7f, 8f), Vec4(9f, 10f, 11f, 12f))
    private val expected = FloatArray(12) { (it + 1).toFloat() }

    @Test
    fun theWriterInterfacePacksEveryInstanceInOrder() {
        val packer = InstancePacker(GpuDataShape.Vec4, "test", write = InstanceWriter<Vec4> { out, offset, v -> v.copyTo(out, offset) })
        assertContentEquals(expected, packer.pack(items, MAX))
        assertContentEquals(expected, packer.pack(items.size, MAX, InstanceSource { items[it] }))
        assertNull(packer.pack(emptyList(), MAX), "nothing to upload")
    }

    @Suppress("DEPRECATION")
    @Test
    fun theReleasedFunctionTypedFormsPackTheSameFloats() {
        val released: (FloatArray, Int, Vec4) -> Unit = { out, offset, v -> v.copyTo(out, offset) }
        val packer = InstancePacker(GpuDataShape.Vec4, "test", 1, released)
        assertContentEquals(expected, packer.pack(items, MAX), "the released constructor")
        val byIndex: (Int) -> Vec4 = { items[it] }
        assertContentEquals(expected, packer.pack(items.size, MAX, byIndex), "the released indexed pack")
    }

    private fun Vec4.copyTo(out: FloatArray, offset: Int) {
        out[offset] = x
        out[offset + 1] = y
        out[offset + 2] = z
        out[offset + 3] = w
    }

    private companion object {
        const val MAX = 8
    }
}
