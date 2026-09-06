/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.ContactPhase
import com.github.stephengold.joltjni.Body
import com.github.stephengold.joltjni.CustomContactListener
import com.github.stephengold.joltjni.SubShapeIdPair

/**
 * Buffers Jolt's contact callbacks so they can be replayed on the frame thread.
 *
 * **Jolt calls this from its worker threads, several at once, in the middle of a step.** Acting on
 * a contact there would mean mutating a running simulation from a thread that does not own it, so
 * nothing happens here except recording; `PhysicsWorld.drainContacts` replays the recording after
 * the step, where creating and destroying bodies is an ordinary call.
 *
 * Only contacts touching a body that was passed to `PhysicsWorld.setSensor` are kept. Jolt reports
 * every touching pair in the scene on every step, so without that filter a settled pile of crates
 * would produce an event per pair per frame for events nothing reads.
 */
internal class JoltContactQueue : CustomContactListener() {
    private val lock = Any()

    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val reportingIds = LinkedHashSet<Int>()
    private val pending = ArrayList<ContactEvent>()

    /**
     * Starts or stops reporting contacts for a body.
     *
     * Stopping matters on destruction as much as on demand: Jolt reuses body IDs, so an ID left in
     * here makes the next body to be handed that ID report contacts nobody asked for.
     */
    fun setReporting(id: Int, reporting: Boolean) {
        synchronized(lock) {
            if (reporting) reportingIds.add(id) else reportingIds.remove(id)
        }
    }

    fun drain(action: (ContactEvent) -> Unit) {
        val drained = synchronized(lock) {
            if (pending.isEmpty()) return
            ArrayList(pending).also { pending.clear() }
        }
        // Sorted, because the order these arrived in is the order Jolt's worker threads happened to
        // reach the lock -- which differs between two runs of the same scene. Game logic that
        // branches on which contact came first (the first pickup wins, the first trigger fires)
        // would diverge on a replay. Sorting costs one pass over a list that is empty most frames.
        drained.sortWith(contactOrder)
        // Replayed outside the lock. The callback is where a game destroys the pickup it just
        // collected, and that path takes this same lock.
        drained.forEach(action)
    }

    override fun onContactAdded(body1Va: Long, body2Va: Long, manifoldVa: Long, settingsVa: Long) {
        // Wrapping is what reading an ID costs: jolt-jni's static id accessor is private, so the
        // address has to become a Body first. The wrapper borrows the pointer rather than owning
        // it, so there is nothing to close -- but it is an allocation per contact per step, and the
        // sensor filter below is what keeps that proportional to what a game listens for rather
        // than to how many bodies are touching.
        record(Body(body1Va).id, Body(body2Va).id, ContactPhase.BEGAN)
    }

    override fun onContactRemoved(pairVa: Long) {
        // Removal carries the sub-shape pair and nothing else -- no bodies, no manifold. Either
        // body may already have been destroyed by the time this arrives, which is why only ids are
        // read here and never the bodies behind them.
        val pair = SubShapeIdPair(pairVa)
        record(pair.body1Id, pair.body2Id, ContactPhase.ENDED)
    }

    private fun record(id1: Int, id2: Int, phase: ContactPhase) {
        synchronized(lock) {
            if (id1 !in reportingIds && id2 !in reportingIds) return
            // Lower id first, always. Jolt reports a pair in its own internal order, which is not
            // stable between runs, so normalising here makes a pair's identity the same every time
            // and gives the sort below something total to work with.
            val low = minOf(id1, id2)
            val high = maxOf(id1, id2)
            pending += ContactEvent(BodyHandle(low.toLong()), BodyHandle(high.toLong()), phase)
        }
    }

    private companion object {
        /** Total and stable: no two events in one drain compare equal unless they are duplicates. */
        val contactOrder: Comparator<ContactEvent> = compareBy({ it.a.id }, { it.b.id }, { it.phase })
    }
}
