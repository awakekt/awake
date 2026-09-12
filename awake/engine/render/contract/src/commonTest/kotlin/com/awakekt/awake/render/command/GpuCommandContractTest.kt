/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpuCommandContractTest {
    @Test
    fun bufferRangesRejectInvalidOwnershipCoordinates() {
        val buffer = object : BufferHandle {}

        assertFailsWith<IllegalArgumentException> { GpuBufferRange(buffer, offsetBytes = -1L) }
        assertFailsWith<IllegalArgumentException> { GpuBufferRange(buffer, sizeBytes = -1L) }
    }

    @Test
    fun bufferRangesPreserveTheCompilerDeclaredView() {
        val buffer = object : BufferHandle {}
        assertEquals(
            GpuBufferRange(buffer, offsetBytes = 64L, sizeBytes = 128L),
            GpuBufferRange(buffer, offsetBytes = 64L, sizeBytes = 128L),
        )
    }
}
