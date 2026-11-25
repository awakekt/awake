/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Computes a conservative memory ceiling in bytes for a browser page or sandboxed runtime from the
 * device's reported RAM and the engine's JS heap limit.
 */
object PageMemoryBudget {
    /** Default share of device RAM (`1/3`) budgeted to a single web page before mobile browsers risk killing the tab. */
    const val DEVICE_SHARE: Double = 1.0 / 3.0

    private const val BYTES_PER_MEGABYTE: Double = 1024.0 * 1024.0

    /**
     * Computes the page memory budget in bytes using [DEVICE_SHARE] of [deviceMemoryMb] capped by
     * [heapLimit].
     *
     * When both inputs are positive, returns the smaller ceiling. When only one input is positive,
     * returns that ceiling. When neither input is positive, returns `null`.
     *
     * @param deviceMemoryMb Approximate device RAM in megabytes (MiB), or `null` when unavailable.
     * @param heapLimit Runtime JS heap limit in bytes, or `null` when unavailable.
     * @return Effective page memory budget in bytes, or `null` when no ceiling can be derived.
     */
    fun of(deviceMemoryMb: Long?, heapLimit: Long?): Long? =
        of(deviceMemoryMb = deviceMemoryMb, heapLimit = heapLimit, deviceShare = DEVICE_SHARE)

    /**
     * Computes the page memory budget in bytes using [deviceShare] of [deviceMemoryMb] capped by
     * [heapLimit].
     *
     * @param deviceMemoryMb Approximate device RAM in megabytes (MiB), or `null` when unavailable.
     * @param heapLimit Runtime JS heap limit in bytes, or `null` when unavailable.
     * @param deviceShare Fraction of device RAM allocated to the page (`0.0 < deviceShare <= 1.0`).
     * @return Effective page memory budget in bytes, or `null` when no ceiling can be derived.
     */
    fun of(deviceMemoryMb: Long?, heapLimit: Long?, deviceShare: Double): Long? {
        require(deviceShare.isFinite() && deviceShare > 0.0 && deviceShare <= 1.0) {
            "deviceShare must be in (0.0, 1.0], was $deviceShare."
        }
        val deviceCap = deviceMemoryMb
            ?.takeIf { it > 0L }
            ?.let { mb -> (mb.toDouble() * BYTES_PER_MEGABYTE * deviceShare).toLong() }
            ?.takeIf { it > 0L }
        val heapCap = heapLimit?.takeIf { it > 0L }
        return when {
            deviceCap != null && heapCap != null -> minOf(deviceCap, heapCap)
            deviceCap != null -> deviceCap
            else -> heapCap
        }
    }
}
