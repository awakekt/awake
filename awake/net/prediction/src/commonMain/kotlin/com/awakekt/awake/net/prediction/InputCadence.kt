/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.roundToInt

/**
 * Tracks the fixed-step window for each numbered client movement input sent to a fixed-tick
 * server, allocating zero bytes after construction.
 *
 * When a client steps at a higher fixed rate than its server (for example, 60 Hz client steps
 * against a 30 Hz server tick), each sent input runs for [stepsPerInput] client steps so both
 * sides integrate the same input for the same duration.
 *
 * To eliminate waiting an extra fixed step when starting to move from rest mid-window,
 * [tryRealignFromRest] may close a partially elapsed stationary window early by at most
 * `stepsPerInput - 1` steps. Those borrowed steps are recorded as [debtSteps] and repaid on a
 * later stationary window via [onLatched], keeping the long-term send rate locked at exactly one
 * input per server tick without inflating server input queues or overflowing rate budgets.
 */
class InputCadence {
    /** Fixed simulation steps per server input tick (`0` until [ensureStepsPerInput] runs). */
    var stepsPerInput: Int = 0
        private set

    /** Fixed steps already simulated inside the currently open input window. */
    var stepsInInput: Int = 0
        private set

    /** Total fixed steps the currently open input window will run before closing. */
    var targetSteps: Int = 0
        private set

    /** Borrowed fixed steps owed back on the next eligible stationary window. */
    var debtSteps: Int = 0
        private set

    /** Whether an input has been latched and still has fixed steps remaining in its window. */
    var open: Boolean = false
        private set

    private var unstepped: Double = 0.0

    /**
     * Computes [stepsPerInput] once from the fixed simulation step duration [fixedDelta] and the
     * authoritative [serverTicksPerSecond].
     */
    fun ensureStepsPerInput(fixedDelta: Float, serverTicksPerSecond: Int) {
        require(fixedDelta.isFinite() && fixedDelta > 0f) {
            "fixedDelta must be finite and positive, was $fixedDelta."
        }
        require(serverTicksPerSecond > 0) {
            "serverTicksPerSecond must be positive, was $serverTicksPerSecond."
        }
        if (stepsPerInput == 0) {
            stepsPerInput = (1f / (fixedDelta * serverTicksPerSecond)).roundToInt().coerceAtLeast(1)
        }
    }

    /**
     * Closes a partially elapsed stationary input window early when [canRealign] holds and no
     * prior [debtSteps] are outstanding. Returns `true` when closed so the caller can record the
     * finished idle input pose and immediately latch the new moving input on this step.
     */
    fun tryRealignFromRest(canRealign: Boolean): Boolean {
        val midRegularWindow = open && stepsInInput > 0 && targetSteps == stepsPerInput
        if (!midRegularWindow || debtSteps != 0 || !canRealign) return false
        debtSteps = (targetSteps - stepsInInput).coerceAtLeast(0)
        stepsInInput = 0
        open = false
        return true
    }

    /**
     * Opens a new input window after latching and sending an input. When [repayAllowed] is `true`,
     * any outstanding [debtSteps] are added to this stationary window and cleared.
     */
    fun onLatched(repayAllowed: Boolean) {
        open = true
        stepsInInput = 0
        val repay = if (repayAllowed) debtSteps.also { debtSteps = 0 } else 0
        targetSteps = stepsPerInput + repay
    }

    /**
     * Advances the active window by one completed fixed step. Returns `true` when the window
     * completes and the caller should record the entity's post-input pose in [PredictionHistory].
     */
    fun onStepCompleted(): Boolean {
        if (!open || ++stepsInInput < targetSteps) return false
        stepsInInput = 0
        targetSteps = stepsPerInput
        open = false
        return true
    }

    /** Fixed steps remaining in the currently open window, or `0` when no window is open. */
    fun remainingSteps(): Int = if (open) (targetSteps - stepsInInput).coerceAtLeast(0) else 0

    /** Newest input sequence whose fixed steps have all completed given the latest sent [inputSeq]. */
    fun lastCompletedSeq(inputSeq: Long): Long = if (open) inputSeq - 1L else inputSeq

    /**
     * Whether [ackSeq] already acknowledges an input whose fixed steps have not finished running on
     * the client yet (so comparing its server pose now would compare against an unfinished step).
     */
    fun isAckAhead(ackSeq: Long, inputSeq: Long): Boolean =
        inputSeq > 0L && (ackSeq > inputSeq || (open && ackSeq == inputSeq))

    /**
     * Returns whole fixed steps dropped past [maxStepsPerFrame] on a long frame. Uses [reported]
     * directly when the host's fixed-step loop provides one; otherwise mirrors the loop's
     * accumulator with [STEP_ROUNDING] so floating-point subtraction does not truncate one step short.
     */
    fun droppedSteps(
        frameDelta: Float,
        stepsRun: Int,
        stepSeconds: Float,
        maxStepsPerFrame: Int,
        reported: Int? = null,
    ): Int = when {
        reported != null -> {
            unstepped = 0.0
            reported.coerceAtLeast(0)
        }
        stepSeconds <= 0f || !frameDelta.isFinite() -> 0
        else -> {
            unstepped += frameDelta - stepsRun * stepSeconds.toDouble()
            if (stepsRun < maxStepsPerFrame) {
                unstepped = unstepped.coerceAtLeast(0.0)
                0
            } else {
                (unstepped / stepSeconds + STEP_ROUNDING).toInt().coerceAtLeast(0).also { unstepped = 0.0 }
            }
        }
    }

    /** Discards accumulated fallback time when a frame is skipped or paused past server guesses. */
    fun clearUnstepped() {
        unstepped = 0.0
    }

    /** Clears all runtime window, debt, and fallback accumulator state for reuse or reconnect. */
    fun reset() {
        stepsPerInput = 0
        stepsInInput = 0
        targetSteps = 0
        debtSteps = 0
        open = false
        unstepped = 0.0
    }

    private companion object {
        const val STEP_ROUNDING: Double = 1e-3
    }
}
