/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Browser-oriented wrapper over [HeapGuard] and [PageMemoryBudget] that combines JavaScript heap
 * bytes and WebAssembly linear memory bytes against the tighter of the device-RAM share and the
 * JS heap limit.
 *
 * @property collectAt Usage ratio (`0.0 < collectAt <= 1.0`) at or above which [HeapGuard.Action.Collect] is returned.
 * @property closeAt Usage ratio (`collectAt <= closeAt <= 1.0`) that counts toward [HeapGuard.Action.Close].
 * @property closeAfter Number of consecutive samples at or above [closeAt] required before
 *   returning [HeapGuard.Action.Close] (`>= 1`).
 * @property deviceShare Fraction of device RAM allocated to the page (`0.0 < deviceShare <= 1.0`).
 */
class PageMemoryGuard(
    val collectAt: Double = HeapGuard.COLLECT_AT,
    val closeAt: Double = HeapGuard.CLOSE_AT,
    val closeAfter: Int = HeapGuard.CLOSE_AFTER,
    val deviceShare: Double = PageMemoryBudget.DEVICE_SHARE,
) {
    private val heapGuard = HeapGuard(
        collectAt = collectAt,
        closeAt = closeAt,
        closeAfter = closeAfter,
    )

    init {
        require(deviceShare.isFinite() && deviceShare > 0.0 && deviceShare <= 1.0) {
            "deviceShare must be in (0.0, 1.0], was $deviceShare."
        }
    }

    /** Number of consecutive checks whose combined page memory stayed at or above [closeAt]. */
    val consecutiveBreaches: Int
        get() = heapGuard.consecutiveBreaches

    /**
     * Evaluates combined page memory (`heapUsed + wasmBytes`) against
     * `PageMemoryBudget.of(deviceMemoryMb, heapLimit, deviceShare)`.
     *
     * If either the budget or both usage signals are unavailable (or negative), resets the breach
     * streak and returns [HeapGuard.Action.None]. When only one of [heapUsed] or [wasmBytes] is
     * available, that reading alone is checked against the budget.
     *
     * @param heapUsed Current JS heap usage in bytes, or `null` when unavailable.
     * @param wasmBytes Current WebAssembly linear memory size in bytes, or `null` when unavailable.
     * @param deviceMemoryMb Approximate device RAM in megabytes (MiB), or `null` when unavailable.
     * @param heapLimit Runtime JS heap limit in bytes, or `null` when unavailable.
     * @return [HeapGuard.Action.None], [HeapGuard.Action.Collect], or [HeapGuard.Action.Close].
     */
    fun check(
        heapUsed: Long?,
        wasmBytes: Long?,
        deviceMemoryMb: Long?,
        heapLimit: Long?,
    ): HeapGuard.Action {
        val budget = PageMemoryBudget.of(
            deviceMemoryMb = deviceMemoryMb,
            heapLimit = heapLimit,
            deviceShare = deviceShare,
        )
        val validHeap = heapUsed?.takeIf { it >= 0L }
        val validWasm = wasmBytes?.takeIf { it >= 0L }
        if (budget == null || (validHeap == null && validWasm == null)) {
            heapGuard.reset()
            return HeapGuard.Action.None
        }
        val used = saturatingAdd(validHeap ?: 0L, validWasm ?: 0L)
        return heapGuard.check(used = used, limit = budget)
    }

    /** Resets [consecutiveBreaches] back to zero. */
    fun reset() {
        heapGuard.reset()
    }

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
