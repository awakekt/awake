/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Send-side backpressure outbox over a [DirectTransport] that separates one-shot discrete packets
 * ([sendNow]) from replaceable state updates ([submit], such as movement inputs).
 *
 * One-shot packets (pings, chat, discrete actions) pass straight to [DirectTransport.sendNow] in
 * FIFO order regardless of socket buffer occupancy so latency probes and reliable commands never
 * queue behind seconds of stale movement inputs. Replaceable state updates are sent immediately
 * while [DirectTransport.bufferedBytes] is at most [limit] and no prior state is waiting; above
 * [limit], at most one pending state is retained, folding newer updates into the waiting one via
 * `merge` (preserving edge-triggered flags such as jump or attack presses) and retrying every
 * [retryMillis] via [DirectTransport.schedule] until the socket drains.
 *
 * Call from the socket's thread only (for example the browser's event loop or worker thread).
 *
 * @param M Replaceable outbound state message type.
 * @param socket Underlying [DirectTransport] providing `bufferedBytes`, `sendNow`, and `schedule`.
 * @param encode Serializer converting a state [M] into wire packet bytes when transmitted.
 * @param merge Combiner folding an `older` unsent state and a `newer` state into a single replacement.
 * @property limit Maximum [DirectTransport.bufferedBytes] at which replaceable state sends immediately.
 * @property retryMillis Delay in milliseconds before rechecking a congested socket buffer.
 */
class DirectOutbox<M : Any>(
    private val socket: DirectTransport,
    private val encode: (M) -> ByteArray,
    private val merge: (older: M, newer: M) -> M,
    val limit: Long = BUFFERED_LIMIT,
    val retryMillis: Int = RETRY_MILLIS,
) {
    init {
        require(limit >= 0L) { "limit must be non-negative, was $limit" }
        require(retryMillis > 0) { "retryMillis must be positive, was $retryMillis" }
    }

    private var pending: M? = null
    private var retrying: Boolean = false
    private val retryCallback: () -> Unit = ::onRetry

    /** Number of replaceable states currently waiting for the socket buffer to drain (`0` or `1`). */
    val waiting: Int
        get() = if (pending == null) 0 else 1

    /** Cumulative replaceable states folded into a newer waiting state rather than transmitted separately. */
    var folded: Long = 0L
        private set

    /**
     * Hands a discrete one-shot [packet] directly to [DirectTransport.sendNow] without queuing or
     * dropping it behind waiting state updates.
     *
     * @param packet Encoded wire packet bytes to transmit immediately.
     */
    fun sendNow(packet: ByteArray) {
        socket.sendNow(packet)
    }

    /**
     * Submits replaceable [state] for transmission: sends immediately if nothing is waiting and
     * [DirectTransport.bufferedBytes] is at most [limit], or folds into the single waiting slot and
     * schedules a drain retry.
     *
     * @param state Latest replaceable state to send or fold.
     */
    fun submit(state: M) {
        val older = pending
        if (older == null && socket.bufferedBytes <= limit) {
            socket.sendNow(encode(state))
            return
        }
        pending = if (older == null) state else merge(older, state).also { folded++ }
        flush()
    }

    /**
     * Transmits the waiting state immediately if [DirectTransport.bufferedBytes] is at most
     * [limit]; otherwise ensures a single retry timer is scheduled on the socket.
     */
    fun flush() {
        val current = pending ?: return
        if (socket.bufferedBytes <= limit) {
            pending = null
            socket.sendNow(encode(current))
        } else if (!retrying) {
            retrying = true
            socket.schedule(retryMillis, retryCallback)
        }
    }

    /** Discards any currently waiting replaceable state (for example on disconnect). */
    fun clear() {
        pending = null
    }

    private fun onRetry() {
        retrying = false
        flush()
    }

    /** Default backpressure thresholds for [DirectOutbox]. */
    companion object {
        /** Default buffered-byte ceiling (`512 B`, a handful of compact input packets) before state folds. */
        const val BUFFERED_LIMIT: Long = 512L

        /** Default drain retry interval (`8 ms`, roughly half a 60 Hz frame). */
        const val RETRY_MILLIS: Int = 8
    }
}
