/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import kotlin.math.floor

/**
 * A contiguous run of frame indices, independent of a texture, renderer or scene.
 *
 * @property firstFrame First index in the run.
 * @property frameCount Number of indices, at least one.
 * @property framesPerSecond Rate in frames per second; zero holds the first frame.
 * @property loop Whether playback repeats or holds the last frame when finished.
 */
data class FrameClip(
    val firstFrame: Int,
    val frameCount: Int,
    val framesPerSecond: Float,
    val loop: Boolean,
) {
    init {
        require(firstFrame >= 0 && frameCount > 0 && firstFrame.toLong() + frameCount <= Int.MAX_VALUE) { "A frame clip must have a valid range of indices." }
        require(framesPerSecond.isFinite() && framesPerSecond >= 0f) { "Clip rate must be finite and non-negative." }
    }
}

/**
 * A simulation-time clock shared by frame-based animations. Call [advance] once per simulation
 * update and read [frame]; applying it to an image or entity belongs to the caller.
 *
 * @param clips Named runs, copied on construction so external map mutations cannot change playback.
 * @param initialClipId Initial run; defaults to the first listed, or none for an empty library.
 */
class FrameClipPlayer(
    clips: Map<String, FrameClip>,
    initialClipId: String? = clips.keys.firstOrNull(),
) {
    private val runs = clips.toMap()

    init {
        require(runs.keys.none { it.isEmpty() }) { "Clip names must not be empty." }
        require(initialClipId == null || initialClipId in runs) { "Unknown initial clip: $initialClipId." }
    }

    /** Name of the active run, or null when none was selected. */
    var activeClipId: String? = initialClipId
        private set

    /** Playback multiplier: one runs normally and zero pauses. */
    var speed: Float = 1f
        set(value) {
            require(value.isFinite() && value >= 0f) { "Clip speed must be finite and non-negative." }
            field = value
        }

    private var clock = 0.0

    /** Seconds into the run; a looping clock stays within one loop. */
    val elapsedSeconds: Float get() = clock.toFloat()

    private val active: FrameClip? get() = activeClipId?.let { runs[it] }

    /** Whether a non-looping run reached its end. Zero-rate and looping runs never finish. */
    val isFinished: Boolean
        get() = active?.let { !it.loop && it.framesPerSecond > 0f && framesPlayed(it) >= it.frameCount } ?: false

    /** Current index, or zero when no run is active. */
    val frame: Int
        get() = active?.let { run ->
            val played = framesPlayed(run)
            run.firstFrame + if (run.loop) played % run.frameCount else minOf(played, run.frameCount - 1)
        } ?: 0

    /** Selects a run. Selecting the same run preserves time unless [restart] is true. */
    fun play(clipId: String, restart: Boolean = false) {
        require(clipId in runs) { "No clip \"$clipId\"; the library has ${runs.keys}." }
        if (activeClipId == clipId && !restart) return
        activeClipId = clipId
        clock = 0.0
    }

    /** Advances by finite [delta] seconds, scaled by [speed]. Negative deltas hold playback. */
    fun advance(delta: Float) {
        require(delta.isFinite()) { "Clip delta must be finite." }
        val run = active ?: return
        if (run.framesPerSecond <= 0f) return
        val duration = run.frameCount / run.framesPerSecond.toDouble()
        val next = clock + maxOf(delta, 0f).toDouble() * speed
        clock = if (run.loop) next.mod(duration) else minOf(next, duration)
    }

    private fun framesPlayed(run: FrameClip): Int = floor(clock * run.framesPerSecond + FRAME_EPSILON).toInt()
}

// Far below a meaningful frame time; credits an exact boundary for floating-point rounding.
private const val FRAME_EPSILON = 1e-4
