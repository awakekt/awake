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

    /** Whether the first change that put this pass out of date is kept, in [cause]; see [PhaseReads.explains]. */
    var explains = false

    /** The first change found since the pass, while [explains]; [PhaseReadCause.None] otherwise. */
    var cause = PhaseReadCause.None
        private set
    var causeSource: Any? = null
        private set
    var causeOwner: Any? = null
        private set

    /** Marks the pass out of date, keeping why if it was not already and [explains] is on. */
    fun markInvalid(why: PhaseReadCause, source: Any? = null, owner: Any? = null) {
        if (explains && !invalid) explain(why, source, owner)
        invalid = true
    }

    private fun explain(why: PhaseReadCause, source: Any?, owner: Any?) {
        if (cause != PhaseReadCause.None) return
        cause = why
        causeSource = source
        causeOwner = owner
    }

    private fun forgetCause() {
        cause = PhaseReadCause.None
        causeSource = null
        causeOwner = null
    }

    /** Whether the pass wants reads of plain holders reported to it. See [readsAreTracked]. */
    var tracksChanges = false

    private var polled = arrayOfNulls<PolledState<*>>(INITIAL_READS)
    private var polledSeen = arrayOfNulls<Any?>(INITIAL_READS)
    private var polledOwner = arrayOfNulls<Any?>(INITIAL_READS)
    private var polledCount = 0

    /**
     * Whether compositions that run inside this pass are held, so the pass is out of date whenever
     * one of theirs is: set for a [PhaseReads], whose measure pass runs subcompositions again. A
     * [Composition] does not, so a composition nested in another keeps its own invalidation.
     */
    var collectsNested = false

    private var nested = arrayOfNulls<ReadObserver>(0)
    private var nestedOwner = arrayOfNulls<Any?>(0)
    private var nestedCount = 0

    /** Starts a pass: older read stamps go stale and the polled reads are forgotten. */
    fun beginPass() {
        pass++
        invalid = false
        forgetCause()
        clearPolled()
    }

    /** Forgets every read, so no state still held by this observer can invalidate it. */
    fun forget() {
        pass++
        invalid = true
        forgetCause()
        if (explains) explain(PhaseReadCause.NoPass, null, null)
        clearPolled()
    }

    /** Records that [observer]'s pass ran inside this one; see [nested]. */
    fun recordNested(observer: ReadObserver) {
        if (!collectsNested) return
        for (i in 0 until nestedCount) if (nested[i] === observer) return
        if (nestedCount == nested.size) {
            nested = nested.copyOf(maxOf(INITIAL_READS, nestedCount * 2))
            nestedOwner = nestedOwner.copyOf(nested.size)
        }
        nestedOwner[nestedCount] = if (explains) ReadOwner.current else null
        nested[nestedCount++] = observer
    }

    fun recordPolled(source: PolledState<*>, seen: Any?) {
        // A source this pass already recorded remembers where, so a pass over many polled sources (an
        // anchor per nameplate) stays linear. Any other source scans: one that another observer read
        // since may still be here.
        if (source.recordedBy === this && source.recordedPass == pass) {
            polledSeen[source.recordedAt] = seen
            return
        }
        for (i in 0 until polledCount) {
            if (polled[i] === source) {
                polledSeen[i] = seen
                source.markRecorded(this, i)
                return
            }
        }
        if (polledCount == polled.size) {
            polled = polled.copyOf(polledCount * 2)
            polledSeen = polledSeen.copyOf(polledCount * 2)
            polledOwner = polledOwner.copyOf(polledCount * 2)
        }
        polled[polledCount] = source
        polledSeen[polledCount] = seen
        polledOwner[polledCount] = if (explains) ReadOwner.current else null
        source.markRecorded(this, polledCount)
        polledCount++
    }

    /**
     * Whether a write or [Composition.invalidate] invalidated the pass, a polled read changed, or a
     * composition that ran inside the pass is invalid.
     */
    fun isInvalid(): Boolean {
        var changed = invalid
        var i = 0
        while (!changed && i < polledCount) {
            changed = polled[i]?.poll() != polledSeen[i]
            if (changed && explains) explain(PhaseReadCause.PolledChanged, polled[i], polledOwner[i])
            i++
        }
        i = 0
        while (!changed && i < nestedCount) {
            changed = nested[i]?.isInvalid() == true
            if (changed && explains) explain(PhaseReadCause.CompositionInvalid, null, nestedOwner[i])
            i++
        }
        return changed
    }

    private fun clearPolled() {
        for (i in 0 until polledCount) {
            polled[i] = null
            polledSeen[i] = null
            polledOwner[i] = null
        }
        polledCount = 0
        for (i in 0 until nestedCount) {
            nested[i] = null
            nestedOwner[i] = null
        }
        nestedCount = 0
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
    private var owners = arrayOfNulls<Any?>(INITIAL_READERS)
    private var count = 0

    fun record(observer: ReadObserver) {
        for (i in 0 until count) {
            if (observers[i] === observer) {
                stamps[i] = observer.pass
                owners[i] = if (observer.explains) ReadOwner.current else null
                return
            }
        }
        // Readers whose latest pass did not read the state go first, so a state read by many short-lived
        // compositions holds only the live ones.
        retainCurrent(invalidate = false)
        if (count == observers.size) {
            observers = observers.copyOf(count * 2)
            stamps = stamps.copyOf(count * 2)
            owners = owners.copyOf(count * 2)
        }
        observers[count] = observer
        stamps[count] = observer.pass
        owners[count] = if (observer.explains) ReadOwner.current else null
        count++
    }

    /** Invalidates every observer whose latest pass read the state, [source], and drops the rest. */
    fun invalidate(source: Any?) = retainCurrent(invalidate = true, source)

    private fun retainCurrent(invalidate: Boolean, source: Any? = null) {
        var kept = 0
        for (i in 0 until count) {
            val observer = observers[i]
            if (observer != null && stamps[i] == observer.pass) {
                if (invalidate) observer.markInvalid(PhaseReadCause.StateWritten, source, owners[i])
                observers[kept] = observer
                stamps[kept] = stamps[i]
                owners[kept] = owners[i]
                kept++
            }
        }
        for (i in kept until count) {
            observers[i] = null
            owners[i] = null
        }
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

    // Where the observer that read this last keeps it; see ReadObserver.recordPolled.
    internal var recordedBy: ReadObserver? = null
        private set
    internal var recordedPass = 0
        private set
    internal var recordedAt = 0
        private set

    internal fun markRecorded(observer: ReadObserver, index: Int) {
        recordedBy = observer
        recordedPass = observer.pass
        recordedAt = index
    }
}
