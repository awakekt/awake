/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Severity of a host memory-pressure signal dispatched through [MemoryPressure].
 */
enum class MemoryPressureLevel {
    /** Moderate pressure: optional caches may begin trimming low-priority entries. */
    Moderate,

    /** Low memory: caches should evict non-essential and backgroundable resources. */
    Low,

    /** Critical pressure: caches should aggressively release all reclaimable memory immediately. */
    Critical,
}

/**
 * Process-wide memory-pressure hub for host OS warnings and runtime heap guards.
 *
 * Calling [trim] increments [version] and synchronously notifies every registered subscriber on a
 * snapshot of the listener list, so caches can release memory immediately even when the render loop
 * is paused or backgrounded. Subscribers receive an idempotent [AutoCloseable] handle from
 * [subscribe] and should close it on teardown.
 */
@OptIn(ExperimentalAtomicApi::class)
object MemoryPressure {
    /** Default wait budget for [trimAndAwaitDrain] to let frame-thread watchers drain caches before GC. */
    const val DEFAULT_DRAIN_TIMEOUT_MILLIS: Long = 150L

    /** Default polling interval for [trimAndAwaitDrain]. */
    const val DEFAULT_DRAIN_POLL_MILLIS: Long = 8L

    private val versionCounter = AtomicInt(0)
    private val drainedVersionCounter = AtomicInt(0)
    private val listeners = AtomicReference<List<Subscription>>(emptyList())

    /**
     * Monotonically increasing counter incremented on every [trim] call.
     *
     * Frame-loop systems can poll this single volatile integer without allocating to detect whether
     * a memory trim occurred since their last tick.
     */
    @Volatile
    var version: Int = 0
        private set

    /**
     * Highest [version] known to have been observed across a complete frame-loop tick via
     * [markFrameDrained].
     */
    @Volatile
    var drainedVersion: Int = 0
        private set

    /**
     * Signals a memory trim at [level], incrementing [version] and synchronously invoking all
     * currently subscribed listeners.
     *
     * Listeners run against an immutable snapshot captured at invocation time so subscribing or
     * unsubscribing inside a callback is safe. An exception thrown by one listener is isolated and
     * never prevents subsequent listeners from running.
     *
     * @param level Severity of the memory-pressure event. Defaults to [MemoryPressureLevel.Low].
     */
    fun trim(level: MemoryPressureLevel = MemoryPressureLevel.Low) {
        trimInternal(level)
    }

    /**
     * Records that a full frame tick has drained all frame-thread [MemoryPressureWatcher]s up to
     * [drainedUpTo] (typically captured from [version] immediately before stepping frame systems).
     */
    fun markFrameDrained(drainedUpTo: Int = version) {
        if (drainedUpTo <= drainedVersion) return
        val target = minOf(drainedUpTo, version)
        var current = drainedVersionCounter.load()
        while (target > current) {
            if (drainedVersionCounter.compareAndSet(current, target)) {
                drainedVersion = maxOf(target, drainedVersionCounter.load())
                break
            }
            current = drainedVersionCounter.load()
        }
    }

    /** Returns `true` when [drainedVersion] has caught up to [targetVersion]. */
    fun hasFrameDrained(targetVersion: Int = version): Boolean = drainedVersion >= targetVersion

    /**
     * Signals a memory trim at [level] and suspends until the frame loop acknowledges draining
     * frame-thread watchers via [markFrameDrained] or [timeoutMillis] elapses.
     *
     * Use this from background heap-guard coroutines before forcing GC and measuring live heap, so
     * GPU/scene/ECS caches trimmed on the frame thread are released before live heap is evaluated.
     *
     * @param level Severity of the memory-pressure event.
     * @param timeoutMillis Maximum wait time in milliseconds (`0` returns immediately after [trim]).
     * @param pollMillis Delay between drain checks in milliseconds.
     * @return `true` if the frame thread drained the trim within [timeoutMillis], `false` on timeout.
     */
    suspend fun trimAndAwaitDrain(
        level: MemoryPressureLevel = MemoryPressureLevel.Low,
        timeoutMillis: Long = DEFAULT_DRAIN_TIMEOUT_MILLIS,
        pollMillis: Long = DEFAULT_DRAIN_POLL_MILLIS,
    ): Boolean {
        require(timeoutMillis >= 0L) { "timeoutMillis must be >= 0, was $timeoutMillis." }
        require(pollMillis > 0L) { "pollMillis must be > 0, was $pollMillis." }
        val targetVersion = trimInternal(level)
        var elapsedMillis = 0L
        while (!hasFrameDrained(targetVersion) && elapsedMillis < timeoutMillis) {
            val step = minOf(pollMillis, timeoutMillis - elapsedMillis)
            delay(step)
            elapsedMillis += step
        }
        return hasFrameDrained(targetVersion)
    }

    private fun trimInternal(level: MemoryPressureLevel): Int {
        val incremented = versionCounter.addAndFetch(1)
        version = maxOf(incremented, versionCounter.load())
        val snapshot = listeners.load()
        for (subscription in snapshot) {
            if (!subscription.isClosed) {
                runCatching { subscription.listener(level) }.getOrDefault(Unit)
            }
        }
        return incremented
    }

    /**
     * Registers [listener] to be invoked synchronously whenever [trim] is called.
     *
     * @param listener Callback invoked with the reported [MemoryPressureLevel].
     * @return An idempotent [AutoCloseable] handle that unsubscribes [listener] when closed.
     */
    fun subscribe(listener: (MemoryPressureLevel) -> Unit): AutoCloseable {
        val subscription = Subscription(listener)
        while (true) {
            val current = listeners.load()
            if (listeners.compareAndSet(current, current + subscription)) {
                return subscription
            }
        }
    }

    /**
     * Creates a [MemoryPressureWatcher] tracking [version] for zero-allocation frame-loop polling,
     * paired with an optional immediate [onTrim] callback invoked synchronously during [trim]
     * (useful when the render loop is paused or backgrounded).
     *
     * Close the returned watcher on teardown to release its subscription.
     *
     * @param onTrim Optional synchronous trim callback invoked on every [trim] call.
     * @return A per-owner [MemoryPressureWatcher] handle.
     */
    fun watch(onTrim: ((MemoryPressureLevel) -> Unit)? = null): MemoryPressureWatcher =
        MemoryPressureWatcher(onTrim?.let(::subscribe))

    /** Clears all registered listeners and resets [version] and [drainedVersion] to zero for isolated unit tests. */
    internal fun resetForTesting() {
        listeners.store(emptyList())
        versionCounter.store(0)
        drainedVersionCounter.store(0)
        version = 0
        drainedVersion = 0
    }

    private fun unsubscribe(target: Subscription) {
        while (true) {
            val current = listeners.load()
            val updated = current.filterNot { it === target }
            if (updated.size == current.size || listeners.compareAndSet(current, updated)) {
                return
            }
        }
    }

    private class Subscription(
        val listener: (MemoryPressureLevel) -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        val isClosed: Boolean
            get() = closed.load()

        override fun close() {
            if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
            unsubscribe(this)
        }
    }
}

/**
 * Per-system handle to [MemoryPressure] combining zero-allocation frame-loop polling ([check] /
 * [drain]) with an optional immediate subscription owned and released by [close].
 */
@OptIn(ExperimentalAtomicApi::class)
class MemoryPressureWatcher internal constructor(
    private val subscription: AutoCloseable?,
) : AutoCloseable {
    private var seenVersion: Int = MemoryPressure.version
    private val closed = AtomicBoolean(false)

    /**
     * Returns `true` once per [MemoryPressure.trim] signal observed since the previous [check] or
     * [drain], advancing the watcher's watermark without allocating.
     */
    fun check(): Boolean {
        val current = MemoryPressure.version
        if (current == seenVersion) return false
        seenVersion = current
        return true
    }

    /**
     * Invokes [onTrim] once when [MemoryPressure.trim] has fired since the previous [check] or
     * [drain].
     */
    inline fun drain(onTrim: () -> Unit) {
        if (check()) onTrim()
    }

    /** Idempotently unsubscribes the optional immediate trim listener owned by this watcher. */
    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        subscription?.close()
    }
}

internal const val ANDROID_TRIM_MEMORY_RUNNING_MODERATE: Int = 5
internal const val ANDROID_TRIM_MEMORY_RUNNING_LOW: Int = 10
internal const val ANDROID_TRIM_MEMORY_RUNNING_CRITICAL: Int = 15
internal const val ANDROID_TRIM_MEMORY_UI_HIDDEN: Int = 20
internal const val ANDROID_TRIM_MEMORY_BACKGROUND: Int = 40
internal const val ANDROID_TRIM_MEMORY_MODERATE: Int = 60
internal const val ANDROID_TRIM_MEMORY_COMPLETE: Int = 80

internal fun mapAndroidTrimLevel(trimLevel: Int): MemoryPressureLevel? = when {
    trimLevel == ANDROID_TRIM_MEMORY_RUNNING_MODERATE -> MemoryPressureLevel.Moderate
    trimLevel == ANDROID_TRIM_MEMORY_RUNNING_LOW ||
        trimLevel == ANDROID_TRIM_MEMORY_UI_HIDDEN -> MemoryPressureLevel.Low
    trimLevel == ANDROID_TRIM_MEMORY_RUNNING_CRITICAL ||
        trimLevel >= ANDROID_TRIM_MEMORY_BACKGROUND -> MemoryPressureLevel.Critical
    else -> null
}
