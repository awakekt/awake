/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * What one [Composition] read in its latest pass, and whether that pass is out of date.
 *
 * A pass is stamped with [pass] when it starts. A [SnapshotMutableState] the pass reads keeps this
 * observer with that stamp, and a write invalidates only the observers whose stamp is still the
 * current one: a state the latest pass stopped reading cannot invalidate it. A [PolledState] the pass
 * reads is held here with the value it returned, and [isInvalid] compares it again.
 *
 * Everything is grown once and reused, so recording a read allocates nothing in steady state.
 */
internal class ReadObserver {
    var pass = 0
        private set

    /** Set by a write to a state the latest pass read, or by [Composition.invalidate]. */
    var invalid = true

    /** Whether the pass wants reads of plain holders reported to it. See [readsAreTracked]. */
    var tracksChanges = false

    private var polled = arrayOfNulls<PolledState<*>>(INITIAL_READS)
    private var polledSeen = arrayOfNulls<Any?>(INITIAL_READS)
    private var polledCount = 0

    /** Starts a pass: older read stamps go stale and the polled reads are forgotten. */
    fun beginPass() {
        pass++
        invalid = false
        clearPolled()
    }

    /** Forgets every read, so no state still held by this observer can invalidate it. */
    fun forget() {
        pass++
        invalid = true
        clearPolled()
    }

    fun recordPolled(source: PolledState<*>, seen: Any?) {
        for (i in 0 until polledCount) {
            if (polled[i] === source) {
                polledSeen[i] = seen
                return
            }
        }
        if (polledCount == polled.size) {
            polled = polled.copyOf(polledCount * 2)
            polledSeen = polledSeen.copyOf(polledCount * 2)
        }
        polled[polledCount] = source
        polledSeen[polledCount] = seen
        polledCount++
    }

    /** Whether a write or [Composition.invalidate] invalidated the pass, or a polled read changed. */
    fun isInvalid(): Boolean {
        var changed = invalid
        var i = 0
        while (!changed && i < polledCount) {
            changed = polled[i]?.poll() != polledSeen[i]
            i++
        }
        return changed
    }

    private fun clearPolled() {
        for (i in 0 until polledCount) {
            polled[i] = null
            polledSeen[i] = null
        }
        polledCount = 0
    }

    private companion object {
        const val INITIAL_READS = 8
    }
}

/**
 * Whether the pass running now asks plain holders to report their reads (see
 * [Composition.tracksChanges]). A holder that keeps a counter or flag in a plain field checks this
 * before creating a [State] for the pass to read, so a host that does not skip unchanged passes
 * pays nothing: no state, no recorded read.
 */
val readsAreTracked: Boolean get() = ReadTracking.current?.tracksChanges == true

/**
 * The composition whose pass is running, if any. Composition is synchronous on the frame thread, so
 * one slot serves; a nested pass saves and restores it.
 */
internal object ReadTracking {
    var current: ReadObserver? = null
}

/**
 * The compositions that read one [SnapshotMutableState], each with the stamp of the pass that read
 * it. Created on the first tracked read and reused for the state's lifetime.
 */
internal class StateReaders {
    private var observers = arrayOfNulls<ReadObserver>(INITIAL_READERS)
    private var stamps = IntArray(INITIAL_READERS)
    private var count = 0

    fun record(observer: ReadObserver) {
        for (i in 0 until count) {
            if (observers[i] === observer) {
                stamps[i] = observer.pass
                return
            }
        }
        // Readers whose latest pass did not read the state go first, so a state read by many short-lived
        // compositions holds only the live ones.
        retainCurrent(invalidate = false)
        if (count == observers.size) {
            observers = observers.copyOf(count * 2)
            stamps = stamps.copyOf(count * 2)
        }
        observers[count] = observer
        stamps[count] = observer.pass
        count++
    }

    /** Invalidates every observer whose latest pass read the state, and drops the rest. */
    fun invalidate() = retainCurrent(invalidate = true)

    private fun retainCurrent(invalidate: Boolean) {
        var kept = 0
        for (i in 0 until count) {
            val observer = observers[i]
            if (observer != null && stamps[i] == observer.pass) {
                if (invalidate) observer.invalid = true
                observers[kept] = observer
                stamps[kept] = stamps[i]
                kept++
            }
        }
        for (i in kept until count) observers[i] = null
        count = kept
    }

    private companion object {
        const val INITIAL_READERS = 2
    }
}

/**
 * A [State] whose writes the runtime cannot see, such as a value another thread or a flow updates.
 *
 * A composition that reads it keeps the value it read, and [Composition.isInvalid] reads it again to
 * compare: it is invalid when the two are no longer equal. Reading is cheap and allocation-free by
 * contract, because [isInvalid][Composition.isInvalid] may poll every frame.
 */
abstract class PolledState<out T> : State<T> {
    /** The source's current value, read without being tracked. */
    protected abstract fun read(): T

    final override val value: T
        get() {
            val current = read()
            ReadTracking.current?.recordPolled(this, current)
            return current
        }

    internal fun poll(): T = read()
}
