/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalForeignApi::class)

package io.github.awakelab.awake.physics.jolt

import cnames.structs.JPC_Body
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.ContactPhase
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.joltc.JPC_BodyID
import platform.joltc.JPC_Body_GetID
import platform.joltc.JPC_CollideShapeResult
import platform.joltc.JPC_ContactManifold
import platform.joltc.JPC_Vec3
import platform.joltc.JPC_ContactSettings
import platform.joltc.JPC_SubShapeIDPair
import platform.joltc.JPC_ValidateResult
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

/**
 * Buffers Jolt's contact callbacks so they can be replayed on the frame thread.
 *
 * **Jolt calls this from its worker threads, several at once, in the middle of a step.** Acting on
 * a contact there would mean mutating a running simulation from a thread that does not own it, so
 * nothing happens here except recording; `PhysicsWorld.drainContacts` replays the recording after
 * the step, where creating and destroying bodies is an ordinary call.
 *
 * The lock is a pthread mutex rather than anything from the standard library: this is entered from
 * threads Jolt owns and Kotlin never started, which rules out the coroutine primitives, and the
 * JVM backends' `synchronized` has no Kotlin/Native equivalent.
 *
 * Only contacts touching a body created as a sensor are kept. Jolt reports every touching pair in
 * the scene on every step, so without that filter a settled pile of crates would produce an event
 * per pair per frame for events nothing reads.
 */
internal class JoltContactQueue {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>()
    private val reportingIds = LinkedHashSet<JPC_BodyID>()
    private val pending = ArrayList<ContactEvent>()

    init {
        pthread_mutex_init(mutex.ptr, null)
    }

    /**
     * Starts or stops reporting contacts for a body.
     *
     * Stopping matters on destruction as much as on demand: Jolt reuses body ids, so an id left in
     * here makes the next body handed that id report contacts nobody asked for.
     */
    fun setReporting(id: JPC_BodyID, reporting: Boolean) = locked {
        if (reporting) reportingIds.add(id) else reportingIds.remove(id)
        Unit
    }

    fun record(id1: JPC_BodyID, id2: JPC_BodyID, phase: ContactPhase) = locked {
        if (id1 in reportingIds || id2 in reportingIds) {
            // Lower id first, always: Jolt reports a pair in its own order, which is not stable
            // between runs, so normalising makes a pair's identity the same every time.
            val low = minOf(id1, id2)
            val high = maxOf(id1, id2)
            pending += ContactEvent(BodyHandle(low.toLong()), BodyHandle(high.toLong()), phase)
        }
    }

    fun drain(action: (ContactEvent) -> Unit) {
        val drained = locked {
            if (pending.isEmpty()) emptyList() else ArrayList(pending).also { pending.clear() }
        }.sortedWith(contactOrder)
        // Replayed outside the lock. The callback is where a game destroys the pickup it just
        // collected, and that path takes this same lock.
        drained.forEach(action)
    }

    fun dispose() {
        pthread_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex.ptr)
    }

    private companion object {
        /**
         * Sorted before delivery, because the order these arrived in is the order Jolt's worker
         * threads happened to reach the lock -- which differs between two runs of the same scene.
         */
        val contactOrder: Comparator<ContactEvent> = compareBy({ it.a.id }, { it.b.id }, { it.phase })
    }

    private fun <T> locked(block: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }
}

private fun contactQueueFrom(self: COpaquePointer?): JoltContactQueue? =
    self?.asStableRef<JoltContactQueue>()?.get()

// All four slots are filled even though only two record anything: the callback table is a plain C
// struct of function pointers, and whether JoltC checks each for null before calling it is not
// visible from the headers this module binds against.
// Every parameter is fixed by the C callback slot's signature; accepting all contacts needs none
// of them.
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun contactValidate(
    self: COpaquePointer?,
    body1: CPointer<JPC_Body>?,
    body2: CPointer<JPC_Body>?,
    baseOffset: CValue<JPC_Vec3>,
    collisionResult: CPointer<JPC_CollideShapeResult>?,
): JPC_ValidateResult = JPC_ValidateResult.JPC_VALIDATE_RESULT_ACCEPT_ALL_CONTACTS

@Suppress("UnusedParameter") // The manifold and settings are the slot's, not this listener's.
internal fun contactAdded(
    self: COpaquePointer?,
    body1: CPointer<JPC_Body>?,
    body2: CPointer<JPC_Body>?,
    manifold: CPointer<JPC_ContactManifold>?,
    settings: CPointer<JPC_ContactSettings>?,
) {
    val queue = contactQueueFrom(self)
    if (queue != null && body1 != null && body2 != null) {
        queue.record(JPC_Body_GetID(body1), JPC_Body_GetID(body2), ContactPhase.BEGAN)
    }
}

/** Persisting contacts are not events -- only the transitions are. */
@Suppress("UnusedParameter") // Signature fixed by the C callback slot.
internal fun contactPersisted(
    self: COpaquePointer?,
    body1: CPointer<JPC_Body>?,
    body2: CPointer<JPC_Body>?,
    manifold: CPointer<JPC_ContactManifold>?,
    settings: CPointer<JPC_ContactSettings>?,
) = Unit

internal fun contactRemoved(self: COpaquePointer?, pair: CPointer<JPC_SubShapeIDPair>?) {
    val queue = contactQueueFrom(self) ?: return
    // Removal carries the sub-shape pair and nothing else -- no bodies, no manifold. Either body
    // may already have been destroyed by the time this arrives, which is why only ids are read
    // here and never the bodies behind them.
    val ids = pair?.pointed ?: return
    queue.record(ids.Body1ID, ids.Body2ID, ContactPhase.ENDED)
}
