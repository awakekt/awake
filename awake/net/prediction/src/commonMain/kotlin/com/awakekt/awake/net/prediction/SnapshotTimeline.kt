/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.abs

/**
 * Client playback clock that maps frame time onto the server's snapshot timeline for remote entity
 * interpolation while recovering immediately from client frame pauses.
 *
 * Remote entities are drawn slightly behind the newest server snapshot ([bufferSeconds]) so packet
 * jitter can be absorbed by interpolating between buffered snapshots rather than stalling or
 * extrapolating. During steady play, each observed snapshot nudges the estimated clock [offset]
 * (`arrivalTime - serverTime`) by [gain] so clock skew and gradual latency shifts track smoothly
 * without stepping [playbackTime] backwards.
 *
 * When the client thread stalls (for example in a background browser tab, during Alt-Tab, or while
 * dragging an OS window), a clamped frame clock freezes while server time advances by seconds or
 * minutes. Smoothing that gap at [gain] would leave remote entities frozen or rubber-banding far in
 * the past; instead, whenever a sample or target diverges by more than [maxBehindSeconds], both
 * [offset] and [playbackTime] rebase at once to `serverTime - bufferSeconds`.
 *
 * @property bufferSeconds Interpolation delay held behind live server time (non-negative).
 * @property gain Smoothing factor in `0.0..1.0` applied to normal clock-offset samples.
 * @property maxBehindSeconds Maximum tolerated drift in seconds before rebasing immediately (positive).
 */
class SnapshotTimeline(
    val bufferSeconds: Double = DEFAULT_BUFFER_SECONDS,
    val gain: Double = DEFAULT_GAIN,
    val maxBehindSeconds: Double = DEFAULT_MAX_BEHIND_SECONDS,
) {
    init {
        require(bufferSeconds >= 0.0 && bufferSeconds.isFinite()) {
            "bufferSeconds must be finite and non-negative, was $bufferSeconds"
        }
        require(gain in 0.0..1.0) { "gain must be in 0.0..1.0, was $gain" }
        require(maxBehindSeconds > 0.0 && maxBehindSeconds.isFinite()) {
            "maxBehindSeconds must be finite and positive, was $maxBehindSeconds"
        }
    }

    /** Current render playback time on the server's clock, or `0.0` before [ready]. */
    var playbackTime: Double = 0.0
        private set

    /** Accumulated client time in seconds from [step] or [advance]. */
    var clientTime: Double = 0.0
        private set

    /** Estimated `clientTime - serverTime` offset in seconds once [ready]. */
    var offset: Double = 0.0
        private set

    /** Whether at least one server snapshot timestamp has been observed since construction or [reset]. */
    var ready: Boolean = false
        private set

    /** Number of discontinuities past [maxBehindSeconds] that triggered an immediate rebase. */
    var rebaseCount: Long = 0L
        private set

    private var lockedAtArrival: Double = Double.NaN

    /**
     * Observes a snapshot stamped with [serverTimeSeconds] on the internal [clientTime] clock.
     *
     * Pass `DirectTransport.arrivalLag` as [arrivalLagSeconds] when packets may have waited on a
     * worker queue before reaching the frame thread, so queue delay is not mistaken for wire
     * latency.
     *
     * @param serverTimeSeconds Authoritative server timestamp carried by the snapshot, in seconds.
     * @param arrivalLagSeconds Time in seconds the packet waited locally before being handed on.
     */
    fun observe(serverTimeSeconds: Double, arrivalLagSeconds: Double = 0.0) {
        require(serverTimeSeconds.isFinite()) {
            "serverTimeSeconds must be finite, was $serverTimeSeconds"
        }
        require(arrivalLagSeconds >= 0.0 && arrivalLagSeconds.isFinite()) {
            "arrivalLagSeconds must be finite and non-negative, was $arrivalLagSeconds"
        }
        observeSample(
            serverTimeSeconds = serverTimeSeconds,
            arrivalTimeSeconds = clientTime - arrivalLagSeconds,
            nowSeconds = clientTime,
        )
    }

    /**
     * Observes a snapshot stamped with [serverTimeSeconds] that arrived at an explicit
     * [arrivalTimeSeconds] on the caller's client clock.
     *
     * @param serverTimeSeconds Authoritative server timestamp carried by the snapshot, in seconds.
     * @param arrivalTimeSeconds Client clock timestamp when the snapshot arrived, in seconds.
     */
    fun observeAt(serverTimeSeconds: Double, arrivalTimeSeconds: Double) {
        require(serverTimeSeconds.isFinite()) {
            "serverTimeSeconds must be finite, was $serverTimeSeconds"
        }
        require(arrivalTimeSeconds.isFinite()) {
            "arrivalTimeSeconds must be finite, was $arrivalTimeSeconds"
        }
        clientTime = arrivalTimeSeconds
        observeSample(
            serverTimeSeconds = serverTimeSeconds,
            arrivalTimeSeconds = arrivalTimeSeconds,
            nowSeconds = arrivalTimeSeconds,
        )
    }

    /**
     * Advances [clientTime] by [deltaSeconds] and returns the updated [playbackTime].
     *
     * @param deltaSeconds Non-negative frame step in seconds (clamped or raw).
     * @return Updated [playbackTime], or `0.0` if not yet [ready].
     */
    fun step(deltaSeconds: Double): Double {
        require(deltaSeconds >= 0.0 && deltaSeconds.isFinite()) {
            "deltaSeconds must be finite and non-negative, was $deltaSeconds"
        }
        return advance(clientTime + deltaSeconds)
    }

    /**
     * Advances [clientTime] to an explicit [clientTimeSeconds], capping normal forward catch-up to
     * [maxStepSeconds] while rebasing immediately if [playbackTime] drifts past [maxBehindSeconds].
     *
     * @param clientTimeSeconds Current client clock timestamp in seconds.
     * @param maxStepSeconds Maximum forward step applied when within [maxBehindSeconds].
     * @return Updated [playbackTime], or `0.0` if not yet [ready].
     */
    fun advance(
        clientTimeSeconds: Double,
        maxStepSeconds: Double = Double.POSITIVE_INFINITY,
    ): Double {
        require(clientTimeSeconds.isFinite()) {
            "clientTimeSeconds must be finite, was $clientTimeSeconds"
        }
        require(maxStepSeconds >= 0.0 && !maxStepSeconds.isNaN()) {
            "maxStepSeconds must be non-negative, was $maxStepSeconds"
        }
        clientTime = clientTimeSeconds
        if (!ready) return playbackTime
        val target = clientTime - offset - bufferSeconds
        val delta = target - playbackTime
        if (abs(delta) > maxBehindSeconds) {
            playbackTime = target
            rebaseCount++
        } else if (delta > 0.0) {
            playbackTime += delta.coerceAtMost(maxStepSeconds)
        }
        return playbackTime
    }

    /** Clears all clock state so the next [observe] or [observeAt] locks afresh as on first connect. */
    fun reset() {
        playbackTime = 0.0
        clientTime = 0.0
        offset = 0.0
        ready = false
        rebaseCount = 0L
        lockedAtArrival = Double.NaN
    }

    private fun observeSample(
        serverTimeSeconds: Double,
        arrivalTimeSeconds: Double,
        nowSeconds: Double,
    ) {
        val sample = arrivalTimeSeconds - serverTimeSeconds
        when {
            !ready -> {
                offset = sample
                ready = true
                lockedAtArrival = arrivalTimeSeconds
                playbackTime = nowSeconds - offset - bufferSeconds
            }
            abs(sample - offset) > maxBehindSeconds -> {
                offset = sample
                lockedAtArrival = arrivalTimeSeconds
                playbackTime = nowSeconds - offset - bufferSeconds
                rebaseCount++
            }
            arrivalTimeSeconds == lockedAtArrival && sample < offset -> {
                offset = sample
                playbackTime = nowSeconds - offset - bufferSeconds
            }
            else -> {
                offset += (sample - offset) * gain
                val target = nowSeconds - offset - bufferSeconds
                if (abs(target - playbackTime) > maxBehindSeconds) {
                    playbackTime = target
                    rebaseCount++
                }
            }
        }
    }

    /** Default tuning parameters for [SnapshotTimeline]. */
    companion object {
        /** Default interpolation buffer behind live server time (100 ms, two 20 Hz snapshots). */
        const val DEFAULT_BUFFER_SECONDS: Double = 0.1

        /** Default exponential smoothing factor applied to each clock-offset sample. */
        const val DEFAULT_GAIN: Double = 0.1

        /** Default drift threshold (2 seconds) beyond which the timeline rebases immediately. */
        const val DEFAULT_MAX_BEHIND_SECONDS: Double = 2.0
    }
}
