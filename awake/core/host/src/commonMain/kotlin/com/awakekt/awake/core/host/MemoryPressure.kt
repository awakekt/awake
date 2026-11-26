/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

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
    private val versionCounter = AtomicInt(0)
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
        val incremented = versionCounter.addAndFetch(1)
        version = maxOf(incremented, versionCounter.load())
        val snapshot = listeners.load()
        for (subscription in snapshot) {
            if (!subscription.isClosed) {
                runCatching { subscription.listener(level) }.getOrDefault(Unit)
            }
        }
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

    /** Clears all registered listeners and resets [version] to zero for isolated unit tests. */
    internal fun resetForTesting() {
        listeners.store(emptyList())
        versionCounter.store(0)
        version = 0
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
