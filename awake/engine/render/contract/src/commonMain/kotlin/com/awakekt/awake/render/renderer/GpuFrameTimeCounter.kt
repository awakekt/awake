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
 *
 * Frame records are pooled and active submissions are tracked in parallel primitive arrays so a
 * steady timed frame allocates nothing in bookkeeping (#1709).
 */
class GpuFrameTimeCounter {
    private class Frame {
        var sequence = 0L
        var outstanding = 0
        var milliseconds = 0.0
        var valid = true
        var closed = false

        fun reset(sequence: Long): Frame {
            this.sequence = sequence
            outstanding = 0
            milliseconds = 0.0
            valid = true
            closed = false
            return this
        }
    }

    private val freeFrames = ArrayList<Frame>(INITIAL_CAPACITY)
    private var frame = Frame().reset(0L)
    private var nextSubmission = 0L
    private var latestSequence = -1L
    private var submissionIds = LongArray(INITIAL_CAPACITY)
    private var submissionFrames = arrayOfNulls<Frame>(INITIAL_CAPACITY)
    private var submissionCount = 0

    /** Most recently completed frame's GPU time in milliseconds, or `Float.NaN` when unavailable. */
    var latestMsOrNaN: Float = Float.NaN
        private set

    /** Most recently completed frame's GPU time, or null when its measurement was unavailable. */
    val latestMs: Float?
        get() = if (latestMsOrNaN.isNaN()) null else latestMsOrNaN

    /** Starts a measurement belonging to the frame currently accumulating draws. */
    fun beginSubmission(): Long {
        val submission = nextSubmission++
        frame.outstanding++
        if (submissionCount == submissionIds.size) {
            val nextSize = submissionIds.size * 2
            submissionIds = submissionIds.copyOf(nextSize)
            submissionFrames = submissionFrames.copyOf(nextSize)
        }
        submissionIds[submissionCount] = submission
        submissionFrames[submissionCount] = frame
        submissionCount++
        return submission
    }

    /** Completes a measurement. A non-finite (`NaN`) or negative duration invalidates the whole frame. */
    fun completeSubmission(submission: Long, milliseconds: Double) {
        val measured = removeSubmission(submission)
            ?: error("Unknown or completed GPU submission: $submission")
        if (!milliseconds.isFinite() || milliseconds < 0.0) {
            measured.valid = false
        } else {
            measured.milliseconds += milliseconds
        }
        measured.outstanding--
        publishIfComplete(measured)
    }

    /** Completes a measurement. A missing or invalid duration invalidates the whole frame. */
    fun completeSubmission(submission: Long, milliseconds: Double?) {
        completeSubmission(submission, milliseconds ?: Double.NaN)
    }

    /** Closes the submitted frame, allowing its measurements to arrive later. */
    fun publish() {
        val closing = frame
        val nextSequence = closing.sequence + 1L
        closing.closed = true
        // Allocate the next frame before publishIfComplete may recycle `closing` into `freeFrames`.
        frame = obtainFrame(nextSequence)
        publishIfComplete(closing)
    }

    private fun obtainFrame(sequence: Long): Frame =
        if (freeFrames.isEmpty()) Frame().reset(sequence) else freeFrames.removeAt(freeFrames.lastIndex).reset(sequence)

    private fun removeSubmission(submission: Long): Frame? {
        for (i in 0 until submissionCount) {
            if (submissionIds[i] == submission) {
                val measured = submissionFrames[i]
                val last = submissionCount - 1
                if (i != last) {
                    submissionIds[i] = submissionIds[last]
                    submissionFrames[i] = submissionFrames[last]
                }
                submissionFrames[last] = null
                submissionCount = last
                return measured
            }
        }
        return null
    }

    private fun publishIfComplete(measured: Frame) {
        if (!measured.closed || measured.outstanding != 0) return
        if (measured.sequence > latestSequence) {
            latestSequence = measured.sequence
            val value = measured.milliseconds.toFloat()
            latestMsOrNaN = if (measured.valid && value.isFinite()) value else Float.NaN
        }
        if (freeFrames.size < MAX_POOLED_FRAMES) freeFrames.add(measured)
    }
}

private const val INITIAL_CAPACITY = 8
private const val MAX_POOLED_FRAMES = 8
