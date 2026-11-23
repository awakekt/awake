/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import kotlinx.serialization.Serializable

/**
 * Musical pacing clock translating beats and bars to exact timeline seconds so shot cuts, lighting
 * flashes, slow-motion drops, and SFX stingers lock cleanly onto a soundtrack's tempo.
 *
 * @property bpm Beats per minute (must be positive, default `120f`).
 * @property beatsPerBar Beats per musical measure (must be positive, default `4`).
 * @property offsetSeconds Timeline offset in seconds where beat `0` lands (default `0f`).
 */
@Serializable
data class BeatClock(
    val bpm: Float = DEFAULT_BPM,
    val beatsPerBar: Int = DEFAULT_BEATS_PER_BAR,
    val offsetSeconds: Float = 0f,
) {
    /** Seconds elapsed per single musical beat. */
    val secondsPerBeat: Float
        get() = if (bpm > 0f && bpm.isFinite()) SECONDS_PER_MINUTE / bpm else SECONDS_PER_MINUTE / DEFAULT_BPM

    /** Seconds elapsed per full musical bar. */
    val secondsPerBar: Float
        get() = secondsPerBeat * beatsPerBar.coerceAtLeast(1)

    /**
     * Converts a musical [beat] index (starting at `0f`) into timeline seconds.
     *
     * @param beat Beat position (`0f` = first downbeat at [offsetSeconds]).
     * @return Absolute timeline timestamp in seconds.
     */
    fun secondsAtBeat(beat: Float): Float = offsetSeconds + beat * secondsPerBeat

    /**
     * Converts a duration in [beats] into seconds.
     *
     * @param beats Duration measured in musical beats.
     * @return Duration in seconds.
     */
    fun durationForBeats(beats: Float): Float = beats * secondsPerBeat

    /**
     * Returns the continuous beat number at [timeSeconds].
     *
     * @param timeSeconds Elapsed timeline time in seconds.
     * @return Continuous beat coordinate (may be negative before [offsetSeconds]).
     */
    fun beatAt(timeSeconds: Float): Float = (timeSeconds - offsetSeconds) / secondsPerBeat

    /**
     * Returns the fractional phase `0..1` within the current beat at [timeSeconds].
     *
     * @param timeSeconds Elapsed timeline time in seconds.
     * @return Phase in `[0f, 1f)`, where `0f` is exactly on the beat hit.
     */
    fun beatPhase(timeSeconds: Float): Float {
        val beat = beatAt(timeSeconds)
        return beat.mod(1f)
    }

    /**
     * Resolves a [TimeRef] (specified in either seconds or beats) to seconds.
     *
     * @param ref Timeline timestamp reference.
     * @return Resolved timestamp in seconds.
     */
    fun resolveTime(ref: TimeRef): Float = when {
        ref.beat != null -> secondsAtBeat(ref.beat)
        ref.seconds != null -> ref.seconds
        else -> 0f
    }

    /**
     * Resolves a [DurationRef] (specified in either seconds or beats) to seconds.
     *
     * @param ref Timeline duration reference.
     * @return Resolved duration in seconds (non-negative).
     */
    fun resolveDuration(ref: DurationRef): Float = when {
        ref.beats != null -> durationForBeats(ref.beats).coerceAtLeast(0f)
        ref.seconds != null -> ref.seconds.coerceAtLeast(0f)
        else -> 0f
    }

    /** Constants for [BeatClock]. */
    companion object {
        /** Default trailer tempo (120 BPM). */
        const val DEFAULT_BPM: Float = 120f

        /** Default time signature numerator (4/4). */
        const val DEFAULT_BEATS_PER_BAR: Int = 4

        private const val SECONDS_PER_MINUTE = 60f
    }
}

/**
 * A timestamp on a director timeline expressed in either [seconds] or musical [beat].
 *
 * @property seconds Absolute time in seconds (`>= 0f`).
 * @property beat Musical beat position resolved through the script's [BeatClock].
 */
@Serializable
data class TimeRef(
    val seconds: Float? = null,
    val beat: Float? = null,
) {
    /** Factory helpers for [TimeRef]. */
    companion object {
        /** Creates a timestamp in seconds. */
        fun seconds(value: Float): TimeRef = TimeRef(seconds = value)

        /** Creates a timestamp on a musical beat. */
        fun beat(value: Float): TimeRef = TimeRef(beat = value)
    }
}

/**
 * A duration on a director timeline expressed in either [seconds] or musical [beats].
 *
 * @property seconds Duration in seconds (`> 0f`).
 * @property beats Duration in musical beats resolved through the script's [BeatClock].
 */
@Serializable
data class DurationRef(
    val seconds: Float? = null,
    val beats: Float? = null,
) {
    /** Factory helpers for [DurationRef]. */
    companion object {
        /** Creates a duration in seconds. */
        fun seconds(value: Float): DurationRef = DurationRef(seconds = value)

        /** Creates a duration in musical beats. */
        fun beats(value: Float): DurationRef = DurationRef(beats = value)
    }
}
