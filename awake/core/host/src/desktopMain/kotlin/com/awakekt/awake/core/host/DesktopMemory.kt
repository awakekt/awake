/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Returns the JVM's currently used heap bytes (`totalMemory() - freeMemory()`), clamped to zero.
 *
 * @return Estimated live and uncollected heap usage in bytes.
 */
fun runtimeHeapUsedBytes(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
}

/**
 * Returns the JVM's configured maximum heap size in bytes (`maxMemory()`), falling back to
 * `totalMemory()` when the runtime reports no finite limit.
 *
 * @return Maximum heap capacity in bytes.
 */
fun runtimeHeapLimitBytes(): Long {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory()
    return if (max == Long.MAX_VALUE || max <= 0L) {
        runtime.totalMemory().coerceAtLeast(0L)
    } else {
        max
    }
}
