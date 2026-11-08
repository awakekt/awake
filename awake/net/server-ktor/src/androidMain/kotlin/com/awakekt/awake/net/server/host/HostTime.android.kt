/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.nanoseconds

private const val SPIN_THRESHOLD_NANOS = 1_500_000L
private const val PARK_SLICE_NANOS = 200_000L

internal actual fun hostNanoTime(): Long = System.nanoTime()

internal actual fun hostCurrentTimeMillis(): Long = System.currentTimeMillis()

internal actual suspend fun hostSleepUntilNanos(targetNanos: Long) {
    val initialLeft = targetNanos - System.nanoTime()
    if (initialLeft <= 0L) return
    if (initialLeft > SPIN_THRESHOLD_NANOS) {
        delay((initialLeft - SPIN_THRESHOLD_NANOS).nanoseconds)
    } else {
        yield()
    }
    while (currentCoroutineContext().isActive) {
        val left = targetNanos - System.nanoTime()
        if (left <= 0L) break
        LockSupport.parkNanos(minOf(left, PARK_SLICE_NANOS))
    }
}

internal actual class TickMonitor actual constructor() {
    private val reentrant = ReentrantLock()
    private val condition = reentrant.newCondition()

    actual fun lock() {
        reentrant.lock()
    }

    actual fun unlock() {
        reentrant.unlock()
    }

    actual fun signalAll() {
        condition.signalAll()
    }

    actual fun awaitNanos(nanos: Long): Long = condition.awaitNanos(nanos)
}
