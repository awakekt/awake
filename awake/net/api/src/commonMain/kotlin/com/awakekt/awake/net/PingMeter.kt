/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Zero-allocation client-side round-trip ping meter pairing outbound ping nonces with inbound pong
 * replies on a caller-supplied monotonic clock.
 *
 * Up to [kept] in-flight pings are remembered in a fixed ring so a pong that arrives after the next
 * ping was sent still resolves accurately, while stray, duplicate, or evicted nonces are ignored.
 * [smoothedMs] maintains an exponentially weighted moving average (`smoothing` weight on each new
 * sample), and [readingMs] escalates immediately to the live wait duration whenever an unanswered
 * ping exceeds [staleSeconds], ensuring stalled connections display rising latency instead of a
 * frozen historical average.
 *
 * Not thread-safe; call from the client frame or network loop thread.
 *
 * @property smoothing EWMA weight in `(0.0, 1.0]` applied to each newly measured RTT sample.
 * @property kept Number of in-flight ping nonces retained in the ring buffer (at least `1`).
 * @property staleSeconds Unanswered ping age in seconds beyond which [readingMs] reports the live wait.
 */
class PingMeter(
    val smoothing: Double = DEFAULT_SMOOTHING,
    val kept: Int = DEFAULT_KEPT,
    val staleSeconds: Double = DEFAULT_STALE_SECONDS,
) {
    init {
        require(smoothing > 0.0 && smoothing <= 1.0 && !smoothing.isNaN()) {
            "smoothing must be in (0.0, 1.0], was $smoothing"
        }
        require(kept >= 1) { "kept must be >= 1, was $kept" }
        require(staleSeconds > 0.0 && staleSeconds.isFinite()) {
            "staleSeconds must be finite and > 0, was $staleSeconds"
        }
    }

    private val nonces = LongArray(kept) { NONE }
    private val sentAt = DoubleArray(kept)
    private var nextNonce: Long = 0L

    /** Exponentially smoothed round-trip latency in milliseconds, or `Double.NaN` before the first pong. */
    var smoothedMs: Double = Double.NaN
        private set

    /** Most recently measured round-trip latency in milliseconds, or `Double.NaN` before the first pong. */
    var lastMs: Double = Double.NaN
        private set

    /** Total number of matched pong replies timed since construction or [reset]. */
    var samples: Int = 0
        private set

    /**
     * Records an outbound ping sent at monotonic timestamp [nowSeconds] and returns its assigned nonce.
     *
     * @param nowSeconds Monotonic client timestamp in seconds when the ping was transmitted.
     * @return Non-negative monotonic nonce identifying this ping.
     */
    fun send(nowSeconds: Double): Long {
        val nonce = nextNonce++
        val index = slot(nonce)
        nonces[index] = nonce
        sentAt[index] = nowSeconds
        return nonce
    }

    /**
     * Matches an inbound pong carrying [nonce] received at monotonic timestamp [nowSeconds].
     *
     * @param nonce Nonce echoed by the server's pong packet.
     * @param nowSeconds Monotonic client timestamp in seconds when the pong arrived.
     * @return `true` if [nonce] matched a remembered in-flight ping and updated [lastMs]/[smoothedMs],
     *   or `false` if [nonce] was negative, unknown, already consumed, or evicted.
     */
    fun receive(nonce: Long, nowSeconds: Double): Boolean {
        val index = if (nonce >= 0L) slot(nonce) else -1
        val matched = index >= 0 && nonces[index] == nonce
        if (matched) {
            nonces[index] = NONE
            val ms = ((nowSeconds - sentAt[index]) * MILLIS_PER_SECOND).coerceAtLeast(0.0)
            lastMs = ms
            smoothedMs = if (smoothedMs.isNaN()) ms else smoothedMs + smoothing * (ms - smoothedMs)
            samples++
        }
        return matched
    }

    /**
     * Returns the latency reading to display at monotonic timestamp [nowSeconds], in milliseconds.
     *
     * Returns [smoothedMs] while all remembered pings are either answered or younger than
     * [staleSeconds]; if any remembered ping has waited longer than [staleSeconds], returns the
     * larger of [smoothedMs] and that ping's elapsed wait in milliseconds.
     *
     * @param nowSeconds Current monotonic client timestamp in seconds.
     * @return Effective latency reading in milliseconds, or `Double.NaN` when nothing has resolved or stalled.
     */
    fun readingMs(nowSeconds: Double): Double {
        var waitedMs = Double.NaN
        for (i in nonces.indices) {
            if (nonces[i] == NONE) continue
            val ageSeconds = nowSeconds - sentAt[i]
            if (ageSeconds > staleSeconds) {
                val candidateMs = ageSeconds * MILLIS_PER_SECOND
                if (waitedMs.isNaN() || candidateMs > waitedMs) {
                    waitedMs = candidateMs
                }
            }
        }
        return when {
            waitedMs.isNaN() -> smoothedMs
            smoothedMs.isNaN() -> waitedMs
            else -> maxOf(smoothedMs, waitedMs)
        }
    }

    /** Clears all in-flight nonces and resets [smoothedMs], [lastMs], and [samples]. */
    fun reset() {
        nonces.fill(NONE)
        sentAt.fill(0.0)
        nextNonce = 0L
        smoothedMs = Double.NaN
        lastMs = Double.NaN
        samples = 0
    }

    private fun slot(nonce: Long): Int = (nonce % kept).toInt()

    /** Default configuration constants for [PingMeter]. */
    companion object {
        /** Default number of in-flight pings retained (`4`, covering 4 s at 1 Hz). */
        const val DEFAULT_KEPT: Int = 4

        /** Default EWMA smoothing factor (`0.3`, settling a step change in ~5 pongs). */
        const val DEFAULT_SMOOTHING: Double = 0.3

        /** Default unanswered ping threshold (`1.0 s`) before [readingMs] surfaces live wait time. */
        const val DEFAULT_STALE_SECONDS: Double = 1.0

        private const val NONE = -1L
        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
