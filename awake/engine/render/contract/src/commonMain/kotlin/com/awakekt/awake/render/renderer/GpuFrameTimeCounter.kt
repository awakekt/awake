/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * Groups asynchronous GPU measurements at the same frame boundary as [RenderStatsCounter].
 * Offscreen submissions belong to the next presented frame. Results can finish out of order;
 * only a complete frame is published, and an older result never replaces a newer one.
 * All calls belong on the render thread; backend callbacks must hand their results back there.
 */
class GpuFrameTimeCounter {
    private class Frame(val sequence: Long) {
        var outstanding = 0
        var milliseconds = 0.0
        var valid = true
        var closed = false
    }

    private var frame = Frame(0)
    private var nextSubmission = 0L
    private var latestSequence = -1L
    private val submissions = HashMap<Long, Frame>()

    /** Most recently completed frame's GPU time, or null when its measurement was unavailable. */
    var latestMs: Float? = null
        private set

    /** Starts a measurement belonging to the frame currently accumulating draws. */
    fun beginSubmission(): Long {
        val submission = nextSubmission++
        frame.outstanding++
        submissions[submission] = frame
        return submission
    }

    /** Completes a measurement. A missing or invalid duration invalidates the whole frame. */
    fun completeSubmission(submission: Long, milliseconds: Double?) {
        val measured = checkNotNull(submissions.remove(submission)) { "Unknown or completed GPU submission: $submission" }
        if (milliseconds == null || !milliseconds.isFinite() || milliseconds < 0.0) {
            measured.valid = false
        } else {
            measured.milliseconds += milliseconds
        }
        measured.outstanding--
        publishIfComplete(measured)
    }

    /** Closes the submitted frame, allowing its measurements to arrive later. */
    fun publish() {
        frame.closed = true
        publishIfComplete(frame)
        frame = Frame(frame.sequence + 1)
    }

    private fun publishIfComplete(measured: Frame) {
        if (!measured.closed || measured.outstanding != 0 || measured.sequence <= latestSequence) return
        latestSequence = measured.sequence
        latestMs = measured.milliseconds.toFloat().takeIf { measured.valid && it.isFinite() }
    }
}
