/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Monotonic nanosecond clock and deadline sleeper driving a server tick loop ([TickSchedule]).
 *
 * Production servers use [Machine], which yields the coroutine dispatcher up to the final
 * sub-millisecond tail and finishes with short parks so coarse OS timer quanta (such as Windows'
 * ~15.6 ms default interval) do not clump 30 Hz or 60 Hz ticks into bursts. Deterministic tests
 * pass [ManualTickClock] to simulate stalls, GC pauses, or CPU quota holds without wall-clock sleeps.
 */
interface TickClock {
    /**
     * Returns the current monotonic timestamp in nanoseconds from an arbitrary origin.
     *
     * @return Monotonic timestamp in nanoseconds.
     */
    fun nanoTime(): Long

    /**
     * Suspends until [nanoTime] reaches [nanos], returning immediately when already at or past [nanos].
     *
     * @param nanos Target monotonic deadline in nanoseconds.
     */
    suspend fun sleepUntil(nanos: Long)

    /** Monotonic host clock backed by the platform's high-resolution timer. */
    object Machine : TickClock {
        override fun nanoTime(): Long = hostNanoTime()

        override suspend fun sleepUntil(nanos: Long) {
            hostSleepUntilNanos(nanos)
        }
    }
}

/**
 * Deterministic [TickClock] that stands still until advanced explicitly by a test via [advanceTo]
 * or [advanceBy].
 *
 * Advancing past the deadline awaited by [sleepUntil] wakes the sleeping tick loop, and
 * [awaitSleeping] blocks the test caller until the tick loop has processed all due catch-up ticks
 * and parked on a future deadline.
 *
 * @param start Initial clock reading in nanoseconds.
 */
class ManualTickClock(start: Long = 0L) : TickClock {
    private val monitor = TickMonitor()
    private var now = start
    private var sleeperUntil = 0L
    private var sleeperContinuation: CancellableContinuation<Unit>? = null

    override fun nanoTime(): Long = monitor.withMonitorLock { now }

    override suspend fun sleepUntil(nanos: Long) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                monitor.withMonitorLock {
                    if (sleeperContinuation === continuation) {
                        sleeperContinuation = null
                    }
                }
            }
            val alreadyDue = monitor.withMonitorLock {
                if (nanos <= now) {
                    true
                } else {
                    sleeperUntil = nanos
                    sleeperContinuation = continuation
                    monitor.signalAll()
                    false
                }
            }
            if (alreadyDue) {
                continuation.resume(Unit)
            }
        }
    }

    /**
     * Advances the clock monotonically to `maxOf(nanoTime(), nanos)` and wakes any sleeper whose
     * target deadline has now been reached.
     *
     * @param nanos Target monotonic timestamp in nanoseconds.
     */
    fun advanceTo(nanos: Long) {
        val wake = monitor.withMonitorLock {
            now = maxOf(now, nanos)
            val continuation = sleeperContinuation
            if (continuation != null && sleeperUntil <= now) {
                sleeperContinuation = null
                continuation
            } else {
                null
            }
        }
        wake?.resume(Unit)
    }

    /**
     * Advances the clock forward by [nanos] nanoseconds (clamped to non-negative progress).
     *
     * @param nanos Nanoseconds to add to the current clock reading.
     */
    fun advanceBy(nanos: Long) {
        if (nanos > 0L) {
            advanceTo(nanoTime() + nanos)
        }
    }

    /**
     * Blocks until the tick loop is parked inside [sleepUntil] waiting for a deadline strictly
     * greater than the current [nanoTime], or until [timeoutMillis] of wall time elapses.
     *
     * @param timeoutMillis Maximum wall-clock wait in milliseconds.
     * @return `true` once the tick loop is parked on a future deadline, or `false` on timeout.
     */
    fun awaitSleeping(timeoutMillis: Long): Boolean =
        monitor.withMonitorLock {
            var leftNanos = timeoutMillis.coerceAtLeast(0L) * NANOS_PER_MILLI
            var timedOut = false
            while (!timedOut && (sleeperContinuation == null || sleeperUntil <= now)) {
                if (leftNanos <= 0L) {
                    timedOut = true
                } else {
                    leftNanos = monitor.awaitNanos(leftNanos)
                }
            }
            !timedOut
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
