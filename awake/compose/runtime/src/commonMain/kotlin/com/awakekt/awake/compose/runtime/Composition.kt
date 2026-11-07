/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * Retains `remember` slots and node identity across root passes.
 *
 * The free [reconcile] function stays one-shot, for tests and small callers that build a tree once.
 * A host that composes every frame owns a Composition and calls this method each time.
 *
 * Each pass records the [State] objects it reads. A host that composes only when something changed
 * asks [isInvalid] before a pass; a host that composes every frame, such as the UI host, ignores it.
 * Invalidation covers the whole composition: there is no restarting a single scope.
 */
class Composition(
    applier: Applier,
    root: Any? = null,
    parentLocals: ProvidedLocals? = null,
) {
    private val composer = Composer(applier, root, parentLocals)
    private val reads = ReadObserver()

    /**
     * Whether the latest pass is out of date: no pass has run yet, a [SnapshotMutableState] it read
     * was written with a different value, a [PolledState] it read now returns a different value, or
     * [invalidate] was called. A [reconcile] clears it; a write during the pass, after the state was
     * read, leaves it set.
     */
    val isInvalid: Boolean get() = reads.isInvalid()

    /** Marks the latest pass out of date, for a change no tracked [State] carries. */
    fun invalidate() {
        reads.invalid = true
    }

    /**
     * Runs one root pass of [content] against the retained tree: reuses what matches, creates what is
     * new, drops what this pass no longer declares, and returns the composer it ran with.
     *
     * The pass's [State] reads replace the previous pass's. A nested pass -- a subcomposition run
     * inside this one -- records its reads to its own composition, not this one. A pass that throws
     * stays invalid.
     */
    fun reconcile(content: context(Composer) () -> Unit): Composer {
        val enclosing = ReadTracking.current
        reads.beginPass()
        ReadTracking.current = reads
        var completed = false
        try {
            composer.beginRoot()
            content(composer)
            composer.finishRoot()
            completed = true
        } finally {
            ReadTracking.current = enclosing
            if (!completed) reads.invalid = true
        }
        return composer
    }

    internal fun forgetReads() = reads.forget()

    /**
     * Drops the whole retained tree: every node goes, and every remembered [RememberObserver] is told
     * it was forgotten. Call it when the host stops composing this tree. A later [reconcile] starts
     * from an empty tree. The composition forgets what it read, so no write reaches it afterwards.
     */
    fun dispose() {
        reads.forget()
        composer.disposeRoot()
    }
}
