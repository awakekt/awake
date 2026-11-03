/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.core.math.Vec3f

/**
 * Where the client's own prediction left a locally controlled entity after each input it sent, by the
 * input's sequence number: what a server snapshot that acknowledges that input is compared with.
 *
 * A snapshot is a round trip old when it arrives, and the entity has moved on since on inputs the
 * server hasn't seen yet. Comparing the snapshot with the pose drawn now would count that walk as an
 * error; comparing it with the pose kept here for the acknowledged input counts only what the server
 * did differently ([reconcile]).
 *
 * A fixed-capacity ring: recording overwrites the entry [capacity] sequences back, and nothing
 * allocates after construction. Size it to hold more inputs than the slowest acknowledgement you
 * accept takes to come back; the default, at 30 inputs a second, holds just over four seconds.
 *
 * @property capacity How many inputs' poses are kept. At least 1.
 */
class PredictionHistory(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be at least 1, was $capacity" }
    }

    private val seqs = LongArray(capacity) { NONE }
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val zs = FloatArray(capacity)
    private val times = DoubleArray(capacity)

    /** The newest sequence recorded, or -1 when none is. */
    var newestSeq: Long = NONE
        private set

    /**
     * Keeps the pose input [seq]'s prediction left the entity at, [x], [y], [z], and [timeSeconds],
     * when the input was sent, on the clock the caller later passes to [secondsSinceOldestUnacked].
     * [seq] is zero or more, and sequences are expected to rise. Recording a sequence again replaces it.
     */
    fun record(seq: Long, x: Float, y: Float, z: Float, timeSeconds: Double = 0.0) {
        require(seq >= 0) { "seq must not be negative, was $seq" }
        val i = slot(seq)
        seqs[i] = seq
        xs[i] = x
        ys[i] = y
        zs[i] = z
        times[i] = timeSeconds
        if (seq > newestSeq) newestSeq = seq
    }

    /** [record] with the pose as a vector. */
    fun record(seq: Long, pose: Vec3f, timeSeconds: Double = 0.0) = record(seq, pose.x, pose.y, pose.z, timeSeconds)

    /** Whether input [seq]'s pose is still kept: recorded, and not overwritten since. */
    operator fun contains(seq: Long): Boolean = seq >= 0 && seqs[slot(seq)] == seq

    /**
     * Writes input [seq]'s pose into [out] and returns true, or leaves [out] as it is and returns false
     * when the pose isn't kept: never recorded, or overwritten.
     */
    fun poseAt(seq: Long, out: Vec3f): Boolean {
        if (seq !in this) return false
        val i = slot(seq)
        out.x = xs[i]
        out.y = ys[i]
        out.z = zs[i]
        return true
    }

    /**
     * Moves every kept pose by [dx], [dy], [dz]: after a correction moved the entity, its later
     * snapshots are compared with where its inputs now leave it, so the same drift isn't corrected twice.
     */
    fun shift(dx: Float, dy: Float, dz: Float) {
        for (i in 0 until capacity) {
            if (seqs[i] == NONE) continue
            xs[i] += dx
            ys[i] += dy
            zs[i] += dz
        }
    }

    /** The oldest kept input newer than [ackSeq], the newest the server acknowledged; -1 when none is kept. */
    fun oldestUnacked(ackSeq: Long): Long {
        var oldest = NONE
        for (i in 0 until capacity) {
            val seq = seqs[i]
            if (seq > ackSeq && (oldest == NONE || seq < oldest)) oldest = seq
        }
        return oldest
    }

    /** When the [oldestUnacked] input was sent, or NaN when no input past [ackSeq] is kept. */
    fun oldestUnackedTime(ackSeq: Long): Double {
        val seq = oldestUnacked(ackSeq)
        return if (seq == NONE) Double.NaN else times[slot(seq)]
    }

    /**
     * How long the oldest input past [ackSeq] has waited for the server, at [nowSeconds]: what the
     * prediction cap reads ([ReconcileInput.secondsSinceOldestUnheard] passes the newest input the server
     * heard). Zero when no input past [ackSeq] was recorded; infinite when the ring has overwritten it,
     * since it is then older than any cap should allow.
     */
    fun secondsSinceOldestUnacked(ackSeq: Long, nowSeconds: Double): Double {
        val sent = oldestUnackedTime(ackSeq)
        return when {
            newestSeq <= ackSeq -> 0.0
            // The ring holds the inputs after newestSeq - capacity; an older one past the ack is gone.
            ackSeq + 1 <= newestSeq - capacity || sent.isNaN() -> Double.POSITIVE_INFINITY
            else -> (nowSeconds - sent).coerceAtLeast(0.0)
        }
    }

    /** Forgets every pose, as after a respawn or a reconnect that restarts the sequence. */
    fun clear() {
        seqs.fill(NONE)
        newestSeq = NONE
    }

    private fun slot(seq: Long): Int = (seq % capacity).toInt()

    companion object {
        /** 128 inputs: over four seconds at 30 inputs a second, longer than any acknowledgement should take. */
        const val DEFAULT_CAPACITY: Int = 128

        private const val NONE = -1L
    }
}
