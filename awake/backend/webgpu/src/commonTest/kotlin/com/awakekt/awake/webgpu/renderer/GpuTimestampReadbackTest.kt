/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
class GpuTimestampReadbackTest {
    @Test
    fun passDurationsAreSummedWithTheNativeQueuePeriod() {
        val bytes = timestamps(1_000uL, 2_000uL, 8_000uL, 12_000uL)
        assertEquals(0.01, GpuFrameTimer.durationMs(bytes, 4, nanosPerTick = 2f))
    }

    @Test
    fun largeAbsoluteClocksKeepSmallDifferencesAndUnsignedWrapWorks() {
        val bytes = timestamps(ULong.MAX_VALUE - 9u, 10uL, (1uL shl 62), (1uL shl 62) + 1_000_000u)
        assertEquals(1.00002, GpuFrameTimer.durationMs(bytes, 4, nanosPerTick = 1f))
    }

    private fun timestamps(vararg values: ULong): ByteArray = ByteArray(values.size * Long.SIZE_BYTES).also { bytes ->
        values.forEachIndexed { index, value ->
            repeat(Long.SIZE_BYTES) { byte -> bytes[index * Long.SIZE_BYTES + byte] = (value shr (byte * 8)).toByte() }
        }
    }
}
