/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Token-bucket rate limiter bounding how many application-level ping requests a server answers for
 * a single connection.
 *
 * Clients typically send one ping per second to measure round-trip latency, but frames or network
 * stalls can release several queued pings in a single burst. Holding [burst] tokens (defaulting to
 * [DEFAULT_BURST]) lets bunched pings all receive replies so client RTT meters recover immediately,
 * while pings sent faster than [perSecond] sustained are silently ignored without kicking the peer
 * or amplifying outbound traffic.
 *
 * Not thread-safe; intended to be owned per connection on a single reader or tick thread.
 *
 * @param clock Monotonic nanosecond clock consulted by the no-argument [allow] overload.
 * @property perSecond Sustained reply refill rate per second (must be positive and finite).
 * @property burst Maximum burst of replies answered at once (must be at least `1.0` and finite).
 */
class PingLimit(
    private val clock: () -> Long,
    val perSecond: Double = DEFAULT_PER_SECOND,
    val burst: Double = DEFAULT_BURST,
) {
    init {
        require(perSecond > 0.0 && perSecond.isFinite()) {
            "perSecond must be finite and > 0, was $perSecond"
        }
        require(burst >= 1.0 && burst.isFinite()) {
            "burst must be finite and >= 1.0, was $burst"
        }
    }

    private var tokens: Double = burst
    private var lastAtNanos: Long = clock()

    /**
     * Checks whether a ping arriving at the current clock reading should be answered, spending one
     * token when allowed.
     *
     * @return `true` if a reply should be sent, or `false` if the ping should be ignored.
     */
    fun allow(): Boolean = allow(clock())

    /**
     * Checks whether a ping arriving at explicit monotonic timestamp [nowNanos] should be answered,
     * spending one token when allowed.
     *
     * @param nowNanos Monotonic timestamp in nanoseconds.
     * @return `true` if a reply should be sent, or `false` if the ping should be ignored.
     */
    fun allow(nowNanos: Long): Boolean {
        val elapsed = (nowNanos - lastAtNanos).coerceAtLeast(0L)
        tokens = minOf(burst, tokens + elapsed / NANOS_PER_SECOND * perSecond)
        lastAtNanos = maxOf(lastAtNanos, nowNanos)
        val allowed = tokens >= 1.0
        if (allowed) {
            tokens -= 1.0
        }
        return allowed
    }

    /** Default rate-limit parameters for [PingLimit]. */
    companion object {
        /** Default sustained reply rate (2 replies/second, twice a typical 1 Hz client ping). */
        const val DEFAULT_PER_SECOND: Double = 2.0

        /** Default burst capacity (8 replies, absorbing several seconds of bunched 1 Hz pings). */
        const val DEFAULT_BURST: Double = 8.0

        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
