/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Vec4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class InstanceTintPackerTest {
    @Test
    fun omittedTintsOverwritePreviousColorsWithWhite() {
        val packer = InstanceTintPacker()
        assertContentEquals(
            floatArrayOf(1f, 0f, 0f, 0.5f, 0f, 0f, 1f, 1f),
            packer.pack(listOf(Vec4(1f, 0f, 0f, 0.5f), Vec4(0f, 0f, 1f, 1f)), 2, 2),
        )
        assertContentEquals(FloatArray(8) { 1f }, packer.pack(null, 2, 2))
    }

    @Test
    fun tintsMustMatchTheInstanceCountAndFitCapacity() {
        val packer = InstanceTintPacker()
        assertFailsWith<IllegalArgumentException> { packer.pack(emptyList(), 2, 2) }
        assertFailsWith<IllegalArgumentException> { packer.pack(null, 3, 2) }
    }
}
