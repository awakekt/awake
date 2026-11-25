/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PageMemoryGuardTest {
    private val mib = 1024L * 1024L

    @Test
    fun budgetUsesTighterOfDeviceShareAndHeapLimit() {
        assertEquals(1.0 / 3.0, PageMemoryBudget.DEVICE_SHARE)

        assertNull(PageMemoryBudget.of(deviceMemoryMb = null, heapLimit = null))
        assertNull(PageMemoryBudget.of(deviceMemoryMb = 0L, heapLimit = 0L))
        assertNull(PageMemoryBudget.of(deviceMemoryMb = -512L, heapLimit = -1L))

        // Device-only: 3072 MiB / 3 = 1024 MiB.
        assertEquals(1024L * mib, PageMemoryBudget.of(deviceMemoryMb = 3072L, heapLimit = null))

        // Heap-only: 512 MiB.
        assertEquals(512L * mib, PageMemoryBudget.of(deviceMemoryMb = null, heapLimit = 512L * mib))

        // Device-bound: 3072 MiB / 3 (1024 MiB) < 2048 MiB heap limit.
        assertEquals(
            1024L * mib,
            PageMemoryBudget.of(deviceMemoryMb = 3072L, heapLimit = 2048L * mib),
        )

        // Heap-bound: 16384 MiB / 3 (~5461 MiB) > 2048 MiB heap limit.
        assertEquals(
            2048L * mib,
            PageMemoryBudget.of(deviceMemoryMb = 16384L, heapLimit = 2048L * mib),
        )

        // Custom share validation.
        assertEquals(
            512L * mib,
            PageMemoryBudget.of(deviceMemoryMb = 2048L, heapLimit = null, deviceShare = 0.25),
        )
        assertFailsWith<IllegalArgumentException> {
            PageMemoryBudget.of(deviceMemoryMb = 2048L, heapLimit = null, deviceShare = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PageMemoryBudget.of(deviceMemoryMb = 2048L, heapLimit = null, deviceShare = 1.5)
        }
    }

    @Test
    fun guardCombinesJsHeapAndWasmLinearMemoryAgainstBudget() {
        val guard = PageMemoryGuard()
        // Budget = min(3072 MiB / 3, 2048 MiB) = 1024 MiB.
        // 400 MiB JS + 350 MiB Wasm = 750 MiB (~73.2% -> Collect, even though neither alone reaches 70%).
        val collectAction = guard.check(
            heapUsed = 400L * mib,
            wasmBytes = 350L * mib,
            deviceMemoryMb = 3072L,
            heapLimit = 2048L * mib,
        )
        assertEquals(HeapGuard.Action.Collect, collectAction)
        assertEquals(0, guard.consecutiveBreaches)

        // 500 MiB JS + 350 MiB Wasm = 850 MiB (~83.0% -> breaches closeAt 80%).
        assertEquals(
            HeapGuard.Action.Collect,
            guard.check(500L * mib, 350L * mib, 3072L, 2048L * mib),
        )
        assertEquals(
            HeapGuard.Action.Collect,
            guard.check(500L * mib, 350L * mib, 3072L, 2048L * mib),
        )
        assertEquals(
            HeapGuard.Action.Close,
            guard.check(500L * mib, 350L * mib, 3072L, 2048L * mib),
        )
        assertEquals(3, guard.consecutiveBreaches)

        guard.reset()
        assertEquals(0, guard.consecutiveBreaches)
    }

    @Test
    fun guardHandlesPartialOrMissingBrowserSignalsGracefully() {
        val guard = PageMemoryGuard()

        // Wasm-only reading (e.g. browser without performance.memory): 850 / 1024 MiB -> breaches.
        assertEquals(
            HeapGuard.Action.Collect,
            guard.check(heapUsed = null, wasmBytes = 850L * mib, deviceMemoryMb = 3072L, heapLimit = null),
        )
        assertEquals(1, guard.consecutiveBreaches)

        // Missing both usage readings resets streak and returns None.
        assertEquals(
            HeapGuard.Action.None,
            guard.check(heapUsed = null, wasmBytes = null, deviceMemoryMb = 3072L, heapLimit = null),
        )
        assertEquals(0, guard.consecutiveBreaches)

        // Heap-only reading with missing budget returns None.
        assertEquals(
            HeapGuard.Action.None,
            guard.check(heapUsed = 850L * mib, wasmBytes = null, deviceMemoryMb = null, heapLimit = null),
        )
        assertEquals(0, guard.consecutiveBreaches)

        // Overflow-safe addition near Long.MAX_VALUE.
        val overflowGuard = PageMemoryGuard(closeAfter = 1)
        assertEquals(
            HeapGuard.Action.Close,
            overflowGuard.check(
                heapUsed = Long.MAX_VALUE - 10L,
                wasmBytes = 100L,
                deviceMemoryMb = 3072L,
                heapLimit = 2048L * mib,
            ),
        )
    }
}
