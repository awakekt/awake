/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlinx.coroutines.delay
import platform.posix.usleep
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

private val nativeClock = TimeSource.Monotonic
private val nativeMark = nativeClock.markNow()

internal actual fun hostNanoTime(): Long = (nativeClock.markNow() - nativeMark).inWholeNanoseconds

internal actual fun hostCurrentTimeMillis(): Long = (nativeClock.markNow() - nativeMark).inWholeMilliseconds

internal actual suspend fun hostSleepUntilNanos(targetNanos: Long) {
    val remaining = targetNanos - hostNanoTime()
    if (remaining > 0L) {
        delay(remaining.nanoseconds)
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal actual class TickMonitor actual constructor() {
    private val held = AtomicInt(0)
    private val signal = AtomicInt(0)

    actual fun lock() {
        while (!held.compareAndSet(0, 1)) {
            usleep(100u)
        }
    }

    actual fun unlock() {
        held.store(0)
    }

    actual fun signalAll() {
        signal.addAndFetch(1)
    }

    actual fun awaitNanos(nanos: Long): Long {
        if (nanos <= 0L) return 0L
        val observed = signal.load()
        val started = hostNanoTime()
        unlock()
        try {
            while (signal.load() == observed && hostNanoTime() - started < nanos) {
                usleep(500u)
            }
        } finally {
            lock()
        }
        return (nanos - (hostNanoTime() - started)).coerceAtLeast(0L)
    }
}
