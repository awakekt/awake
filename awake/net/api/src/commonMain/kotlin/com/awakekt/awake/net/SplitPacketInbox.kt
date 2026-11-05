/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

/**
 * Two-lane inbound packet inbox separating discrete reliable messages ([M]) from replaceable world
 * snapshots ([S]).
 *
 * When a client's main thread pauses (for example in a background browser tab or during an OS
 * window drag), a single FIFO packet queue fills with hundreds of high-rate world snapshots that
 * either overflow and drop critical discrete events or head-of-line block them on resume. Splitting
 * inbound traffic into a bounded reliable message lane and a bounded `DROP_OLDEST` snapshot lane
 * preserves discrete events while keeping only the freshest snapshots.
 *
 * Every offer and poll operation is non-blocking, safe to call directly from a socket listener
 * callback such as [DirectTransport.listen] as well as from a frame loop.
 *
 * @param M Discrete reliable message type.
 * @param S Replaceable state snapshot type.
 * @property messageCapacity Maximum number of discrete messages buffered before [offerMessage] drops.
 * @property snapshotCapacity Maximum number of snapshots retained before [offerSnapshot] evicts the oldest.
 */
class SplitPacketInbox<M : Any, S : Any>(
    val messageCapacity: Int = DEFAULT_MESSAGE_CAPACITY,
    val snapshotCapacity: Int = DEFAULT_SNAPSHOT_CAPACITY,
) : AutoCloseable {
    init {
        require(messageCapacity > 0) { "messageCapacity must be positive, was $messageCapacity" }
        require(snapshotCapacity > 0) { "snapshotCapacity must be positive, was $snapshotCapacity" }
    }

    private val messages = Channel<M>(messageCapacity, BufferOverflow.SUSPEND)
    private val snapshots = Channel<S>(snapshotCapacity, BufferOverflow.DROP_OLDEST) {
        droppedSnapshots++
    }

    /** Cumulative discrete messages rejected because the message lane was full or closed. */
    @Volatile
    var droppedMessages: Long = 0L
        private set

    /** Cumulative snapshots evicted due to snapshot lane overflow or rejected after [close]. */
    @Volatile
    var droppedSnapshots: Long = 0L
        private set

    /** Whether [close] has been called and all buffered messages and snapshots have been drained. */
    @OptIn(DelicateCoroutinesApi::class)
    val isClosedForReceive: Boolean
        get() = messages.isClosedForReceive && snapshots.isClosedForReceive

    /**
     * Queues a discrete [message] without blocking.
     *
     * @param message Discrete reliable message to enqueue.
     * @return `true` if queued, or `false` (incrementing [droppedMessages]) if full or closed.
     */
    fun offerMessage(message: M): Boolean {
        val accepted = messages.trySend(message).isSuccess
        if (!accepted) droppedMessages++
        return accepted
    }

    /**
     * Queues a replaceable state [snapshot] without blocking.
     *
     * When [snapshotCapacity] snapshots are already waiting, the oldest queued snapshot is evicted
     * and counted in [droppedSnapshots] so the newest snapshot always wins.
     *
     * @param snapshot State snapshot to enqueue.
     */
    fun offerSnapshot(snapshot: S) {
        if (!snapshots.trySend(snapshot).isSuccess) {
            droppedSnapshots++
        }
    }

    /**
     * Removes and returns the next queued discrete message in arrival order, or `null` if empty.
     *
     * @return Next buffered discrete message, or `null`.
     */
    fun pollMessage(): M? = messages.tryReceive().getOrNull()

    /**
     * Removes and returns the oldest retained snapshot in arrival order, or `null` if empty.
     *
     * @return Next buffered snapshot, or `null`.
     */
    fun pollSnapshot(): S? = snapshots.tryReceive().getOrNull()

    /**
     * Polls all currently queued discrete messages in arrival order and passes each to [consumer].
     *
     * @param consumer Callback invoked for each drained message.
     * @return Number of messages drained.
     */
    fun drainMessages(consumer: (M) -> Unit): Int {
        var drained = 0
        while (true) {
            val message = messages.tryReceive().getOrNull() ?: break
            consumer(message)
            drained++
        }
        return drained
    }

    /**
     * Polls all currently retained snapshots in arrival order and passes each to [consumer].
     *
     * @param consumer Callback invoked for each drained snapshot.
     * @return Number of snapshots drained.
     */
    fun drainSnapshots(consumer: (S) -> Unit): Int {
        var drained = 0
        while (true) {
            val snapshot = snapshots.tryReceive().getOrNull() ?: break
            consumer(snapshot)
            drained++
        }
        return drained
    }

    /**
     * Drains all currently retained snapshots and returns only the newest one, or `null` if empty.
     *
     * @return Most recently offered snapshot currently in the inbox, or `null`.
     */
    fun latestSnapshot(): S? {
        var latest: S? = null
        while (true) {
            val snapshot = snapshots.tryReceive().getOrNull() ?: break
            latest = snapshot
        }
        return latest
    }

    /** Closes both lanes to new offers while allowing already buffered items to be polled. */
    override fun close() {
        messages.close()
        snapshots.close()
    }

    /** Default lane capacities for [SplitPacketInbox]. */
    companion object {
        /** Default capacity of the discrete reliable message lane. */
        const val DEFAULT_MESSAGE_CAPACITY: Int = 4096

        /** Default capacity of the conflated snapshot lane. */
        const val DEFAULT_SNAPSHOT_CAPACITY: Int = 32
    }
}
