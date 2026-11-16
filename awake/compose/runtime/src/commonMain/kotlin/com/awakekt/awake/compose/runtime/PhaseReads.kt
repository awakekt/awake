/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * What a phase that is not composition -- a layout's measure pass -- read in its latest pass, and
 * whether that pass is out of date. A [Composition] keeps the same record for its own passes.
 *
 * A host starts a pass with [beginPass], then brackets each stretch of work that belongs to it with
 * [resume] and [pause] (or [recording]). A [SnapshotMutableState] read in between keeps this record
 * with the pass's stamp, a [PolledState] read is held with the value it returned, and a
 * [Composition] that runs in between, such as a subcomposition, is held too. [isInvalid] is then
 * true once any of them changed. Reads made outside a bracket -- placement, drawing -- are not
 * recorded, which is the point: a value read only there never makes the phase run again.
 *
 * Plain holders that report reads only when asked ([readsAreTracked]) are asked while recording,
 * so a [com.awakekt.awake.compose.runtime.State] behind a plain-looking field is seen. A plain `var`
 * is not: a phase that reads one must be re-run by [invalidate].
 *
 * Not reentrant, and not thread-safe: one host drives it on the frame thread. Recording allocates
 * nothing in steady state.
 */
class PhaseReads {
    private val reads = ReadObserver().also {
        it.tracksChanges = true
        it.collectsNested = true
    }
    private var enclosing: ReadObserver? = null
    private var active = false

    /**
     * Whether the latest pass is out of date: no pass has run yet, a state it read was written with a
     * different value, a polled state it read now differs, a composition that ran inside it is
     * invalid, or [invalidate] was called.
     */
    val isInvalid: Boolean get() = reads.isInvalid()

    /** Starts a pass: what the previous one read can no longer invalidate it. */
    fun beginPass() {
        reads.beginPass()
    }

    /** Records reads from here until [pause]. */
    fun resume() {
        check(!active) { "PhaseReads is already recording" }
        enclosing = ReadTracking.current
        ReadTracking.current = reads
        active = true
    }

    /** Stops recording and gives reads back to whatever recorded before [resume]. */
    fun pause() {
        check(active) { "PhaseReads is not recording" }
        ReadTracking.current = enclosing
        enclosing = null
        active = false
    }

    /** Marks the latest pass out of date, for a change no tracked state carries. */
    fun invalidate() {
        reads.markInvalid(PhaseReadCause.Invalidated)
    }

    /**
     * Whether the first change that puts a pass out of date is kept, for [cause] (#1512): a debug aid
     * for a host that wants to say why a phase it hoped to skip ran. Off by default, and while off
     * nothing is kept and nothing is allocated. While on, each read also keeps [ReadOwner.current].
     */
    var explains: Boolean
        get() = reads.explains
        set(value) {
            reads.explains = value
        }

    /** What first put the latest pass out of date, while [explains]; read it after [isInvalid]. */
    val cause: PhaseReadCause
        get() = if (reads.cause == PhaseReadCause.None && reads.pass == 0 && reads.invalid) PhaseReadCause.NoPass else reads.cause

    /** The state, polled source or composition behind [cause], if it has one. */
    val causeSource: Any? get() = reads.causeSource

    /** The [ReadOwner.current] of the read behind [cause]: who read what changed. */
    val causeOwner: Any? get() = reads.causeOwner

    /** Forgets every read and marks the record out of date, as before the first pass. */
    fun forget() {
        reads.forget()
    }
}

/**
 * Runs [block] while [reads] records, and marks the pass out of date if it throws, since what it
 * read is then incomplete.
 */
inline fun <R> PhaseReads.recording(block: () -> R): R {
    resume()
    var completed = false
    try {
        return block().also { completed = true }
    } finally {
        pause()
        if (!completed) invalidate()
    }
}
