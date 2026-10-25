/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * How [TokenAuthorizedFetch] answers a content server that asks for fewer requests: a request
 * answered 429 Too Many Requests is waited out and sent again, a bounded number of times.
 *
 * - **`Retry-After` first.** A 429 with a `Retry-After` header waits exactly as long as it says, in
 *   either of its forms: a number of seconds, or an HTTP date, measured against [clock]. A date
 *   already past sends again at once.
 * - **Otherwise exponential, with jitter.** With no `Retry-After`, or one that doesn't parse, the
 *   first retry waits up to [initialDelay], each later one up to twice the one before, never more
 *   than [maxDelay]. Each wait is drawn from between half and all of that, so clients refused
 *   together don't all come back together.
 * - **Bounded.** A request is sent at most [maxAttempts] times while it is answered 429, the first
 *   included, and one [TokenAuthorizedFetch.fetch] waits at most [maxTotalWait] in all. A wait that
 *   would go past that is not started: the 429 fails the fetch at once, as a
 *   [ContentFetchException] with status 429 that says why it stopped.
 * - **Only 429, only reads.** No other status is retried here, 503 included; network retries belong
 *   to the transport. Every content request is a `GET`, so sending one again changes nothing on the
 *   server.
 *
 * A wait suspends through [delay] and never blocks a thread, so it is safe on the frame thread, and
 * cancelling the fetch cancels the wait. After each wait the read token is checked again, and
 * refreshed if the wait brought it within [TokenAuthorizedFetch]'s `refreshAhead` of expiring.
 *
 * @property maxAttempts How many times one request is sent while it is answered 429, the first
 * included. At least 1; 1 sends it once and reports the 429, as [None] does.
 * @property maxTotalWait The longest one fetch waits in all, across its retries. Not negative.
 * @property initialDelay The longest wait before the first retry when the server says nothing. Not
 * negative.
 * @property maxDelay The longest any one wait grows to when the server says nothing. At least
 * [initialDelay].
 * @param clock The wall clock an HTTP-date `Retry-After` is measured against.
 * @param random The source of each wait's jitter.
 * @param delay Suspends for a wait; `kotlinx.coroutines.delay` by default. A test passes its own to
 * record the waits rather than take them.
 */
class RateLimitBackoff(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val maxTotalWait: Duration = DEFAULT_MAX_TOTAL_WAIT,
    val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
    val maxDelay: Duration = DEFAULT_MAX_DELAY,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
    private val delay: suspend (Duration) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1: $maxAttempts" }
        require(!maxTotalWait.isNegative()) { "maxTotalWait must not be negative: $maxTotalWait" }
        require(!initialDelay.isNegative()) { "initialDelay must not be negative: $initialDelay" }
        require(maxDelay >= initialDelay) { "maxDelay ($maxDelay) must be at least initialDelay ($initialDelay)" }
    }

    /**
     * What to do after the [sends]th send of one request was answered 429 with [retryAfter], when the
     * fetch has already waited [waited].
     */
    internal fun next(sends: Int, retryAfter: String?, waited: Duration): RateLimitStep {
        if (sends >= maxAttempts) {
            return RateLimitStep.Stop(if (sends == 1) "the backoff allows one attempt" else "it was sent $sends times, the backoff's limit")
        }
        val wait = retryAfter?.let { retryAfterDelay(it, clock.now()) } ?: exponentialDelay(retry = sends)
        val left = maxTotalWait - waited
        return if (wait <= left) {
            RateLimitStep.Wait(wait)
        } else {
            RateLimitStep.Stop("waiting another $wait would pass the $maxTotalWait this fetch may wait")
        }
    }

    internal suspend fun pause(wait: Duration) {
        if (wait.isPositive()) delay(wait)
    }

    /** Up to [initialDelay] doubled once per earlier retry and capped at [maxDelay], from half of that up. */
    private fun exponentialDelay(retry: Int): Duration {
        var ceiling = initialDelay
        repeat(retry - 1) { ceiling = minOf(ceiling * 2, maxDelay) }
        return ceiling * (HALF + random.nextDouble() * HALF)
    }

    /** The default policy, and [None]. */
    companion object {
        /** How many times a request is sent while it is answered 429, by default. */
        const val DEFAULT_MAX_ATTEMPTS: Int = 4

        /** The longest one fetch waits in all, by default. */
        val DEFAULT_MAX_TOTAL_WAIT: Duration = 30.seconds

        /** The longest wait before the first retry when the server sends no `Retry-After`, by default. */
        val DEFAULT_INITIAL_DELAY: Duration = 1.seconds

        /** The longest any one wait grows to when the server sends no `Retry-After`, by default. */
        val DEFAULT_MAX_DELAY: Duration = 8.seconds

        /** Never waits: a 429 fails the fetch at once. */
        val None: RateLimitBackoff = RateLimitBackoff(maxAttempts = 1)

        private const val HALF = 0.5
    }
}

/** What [RateLimitBackoff] decided after a 429. */
internal sealed interface RateLimitStep {
    /** Wait [duration], then send the request again. */
    class Wait(val duration: Duration) : RateLimitStep

    /** Report the 429; [reason] says why no retry follows. */
    class Stop(val reason: String) : RateLimitStep
}

/**
 * How long a `Retry-After` header [value] asks to wait from [now]: a number of seconds, or an
 * HTTP date (zero once it has passed). Null for a value in neither form.
 */
internal fun retryAfterDelay(value: String, now: Instant): Duration? {
    val text = value.trim()
    if (text.isNotEmpty() && text.all { it in '0'..'9' }) return text.toLongOrNull()?.seconds ?: Duration.INFINITE
    return parseHttpDate(text)?.let { (it - now).coerceAtLeast(Duration.ZERO) }
}
