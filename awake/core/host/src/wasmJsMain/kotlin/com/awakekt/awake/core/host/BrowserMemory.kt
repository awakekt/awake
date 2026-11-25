/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.host

import kotlin.math.roundToLong

private const val MEGABYTES_PER_GIGABYTE = 1024.0

/**
 * Returns the browser's current JavaScript heap usage in bytes from
 * `performance.memory.usedJSHeapSize`, or `null` when unavailable.
 *
 * @return Current JS heap usage in bytes, or `null` if the browser does not expose it.
 */
fun browserHeapUsedBytes(): Long? {
    val bytes = readUsedJsHeapSize()
    return if (bytes >= 0.0 && bytes.isFinite()) bytes.toLong() else null
}

/**
 * Returns the browser's JavaScript heap limit in bytes from
 * `performance.memory.jsHeapSizeLimit`, or `null` when unavailable.
 *
 * @return JS heap limit in bytes, or `null` if the browser does not expose it.
 */
fun browserHeapLimitBytes(): Long? {
    val bytes = readJsHeapSizeLimit()
    return if (bytes > 0.0 && bytes.isFinite()) bytes.toLong() else null
}

/**
 * Returns the browser's approximate device RAM in megabytes (MiB), converted from
 * `navigator.deviceMemory` (which the W3C Device Memory API reports in GiB), or `null` when
 * unavailable.
 *
 * @return Approximate device memory in MiB, or `null` when unsupported or blocked.
 */
fun browserDeviceMemoryMegabytes(): Long? {
    val gigabytes = readNavigatorDeviceMemoryGb()
    return if (gigabytes > 0.0 && gigabytes.isFinite()) {
        (gigabytes * MEGABYTES_PER_GIGABYTE).roundToLong().takeIf { it > 0L }
    } else {
        null
    }
}

private fun readUsedJsHeapSize(): Double = js(
    "(() => { try { const v = typeof performance !== 'undefined' && performance && performance.memory ? performance.memory.usedJSHeapSize : -1; return typeof v === 'number' ? v : -1; } catch (_) { return -1; } })()",
)

private fun readJsHeapSizeLimit(): Double = js(
    "(() => { try { const v = typeof performance !== 'undefined' && performance && performance.memory ? performance.memory.jsHeapSizeLimit : -1; return typeof v === 'number' ? v : -1; } catch (_) { return -1; } })()",
)

private fun readNavigatorDeviceMemoryGb(): Double = js(
    "(() => { try { const v = typeof navigator !== 'undefined' && navigator ? navigator.deviceMemory : -1; return typeof v === 'number' ? v : -1; } catch (_) { return -1; } })()",
)
