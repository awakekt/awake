/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

internal expect fun hostNanoTime(): Long

internal expect fun hostCurrentTimeMillis(): Long

internal expect suspend fun hostSleepUntilNanos(targetNanos: Long)

internal expect class TickMonitor() {
    fun lock()

    fun unlock()

    fun signalAll()

    fun awaitNanos(nanos: Long): Long
}

internal inline fun <T> TickMonitor.withMonitorLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
