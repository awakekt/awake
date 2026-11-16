/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * Dynamic resolution's fallback where the GPU cannot be timed (#1688): it reads only how long frames
 * take, so it has to find out by trying whether resolution is what holds them back.
 *
 * Frames are judged in windows of [WINDOW_FRAMES]. A frame misses when it runs [MISS_TOLERANCE] past
 * the budget.
 * - **Down.** A window with [MISSES_TO_STEP_DOWN] misses steps the edge down by [STEP_DOWN].
 * - **No relief.** When [UNRESPONSIVE_STEPS] steps in a row leave the window's mean frame time where
 *   it was, frames are held by something else, such as a display capped below the target, a
 *   present-time pacer or the CPU. The edge goes back up by the steps it took, and frames as long as
 *   that mean stop counting as misses until a window runs under budget again.
 * - **Probing up.** After [PROBE_AFTER_WINDOWS] clean windows the edge probes up by [STEP_UP]. A probe
 *   whose next window misses steps back, and the wait before the next probe doubles, up to
 *   [MAX_PROBE_WAIT_WINDOWS], so a scene that sits at its limit does not see-saw. A probe that holds
 *   resets the wait.
 */
internal class MissedFrameScaler(private val budgetMs: Float) {
    private var frames = 0
    private var misses = 0
    private var sumMs = 0f
    private var cleanWindows = 0
    private var probeWait = PROBE_AFTER_WINDOWS
    private var probing = false
    private var stepsDown = 0
    private var stepsWithoutRelief = 0
    private var meanBeforeStepMs = 0f
    private var boundMs = 0f

    /** Whether the last windows showed a limit resolution does not move, and misses are judged against it. */
    val isBoundElsewhere: Boolean get() = boundMs > 0f

    /**
     * Takes a frame of [frameMs], and whether the edge already sits at its floor ([atFloor]) or
     * ceiling ([atCeiling]); returns the factor to move the edge by, 1 when it stays.
     */
    fun update(frameMs: Float, atFloor: Boolean, atCeiling: Boolean): Float {
        frames += 1
        sumMs += frameMs
        if (frameMs > missThresholdMs()) misses += 1
        if (frames < WINDOW_FRAMES) return 1f
        val mean = sumMs / frames
        val windowMisses = misses
        frames = 0
        misses = 0
        sumMs = 0f
        return closeWindow(mean, windowMisses, atFloor, atCeiling)
    }

    private fun missThresholdMs(): Float = maxOf(budgetMs, boundMs) * (1f + MISS_TOLERANCE)

    private fun closeWindow(mean: Float, windowMisses: Int, atFloor: Boolean, atCeiling: Boolean): Float {
        if (boundMs > 0f && mean < budgetMs) boundMs = 0f
        val missed = windowMisses >= MISSES_TO_STEP_DOWN
        return when {
            probing -> settleProbe(missed)
            !missed -> cleanWindow(atCeiling)
            else -> missedWindow(mean, atFloor)
        }
    }

    private fun cleanWindow(atCeiling: Boolean): Float {
        stepsDown = 0
        stepsWithoutRelief = 0
        cleanWindows += 1
        val probe = cleanWindows >= probeWait && !atCeiling
        if (probe) {
            cleanWindows = 0
            probing = true
        }
        return if (probe) STEP_UP else 1f
    }

    private fun missedWindow(mean: Float, atFloor: Boolean): Float {
        cleanWindows = 0
        if (stepsDown > 0) {
            val relieved = mean < meanBeforeStepMs * (1f - RELIEF)
            stepsWithoutRelief = if (relieved) 0 else stepsWithoutRelief + 1
        }
        return when {
            stepsWithoutRelief >= UNRESPONSIVE_STEPS -> giveBackSteps(mean)
            atFloor -> 1f
            else -> {
                stepsDown += 1
                meanBeforeStepMs = mean
                STEP_DOWN
            }
        }
    }

    private fun settleProbe(missed: Boolean): Float {
        probing = false
        if (!missed) {
            probeWait = PROBE_AFTER_WINDOWS
            return 1f
        }
        probeWait = minOf(probeWait * 2, MAX_PROBE_WAIT_WINDOWS)
        return 1f / STEP_UP
    }

    private fun giveBackSteps(mean: Float): Float {
        var factor = 1f
        repeat(stepsDown) { factor /= STEP_DOWN }
        boundMs = mean
        stepsDown = 0
        stepsWithoutRelief = 0
        return factor
    }

    /** The scaler's tuning. */
    companion object {
        /** Frames judged together: half a second at 60 fps. */
        const val WINDOW_FRAMES = 30

        /** How far past the budget a frame runs before it misses: a tenth, room for timer jitter. */
        const val MISS_TOLERANCE = 0.1f

        /** Misses in a window that step the edge down: one frame in ten. */
        const val MISSES_TO_STEP_DOWN = 3

        /** One step down: 10% off the edge, about 19% of the pixels. */
        const val STEP_DOWN = 0.9f

        /** One probe up: 5% onto the edge. */
        const val STEP_UP = 1.05f

        /** How much a step must cut the mean frame time to count as relief: 3%. */
        const val RELIEF = 0.03f

        /** Steps in a row without relief before the limit is taken to be elsewhere. */
        const val UNRESPONSIVE_STEPS = 2

        /** Clean windows before the first probe up: two seconds at 60 fps. */
        const val PROBE_AFTER_WINDOWS = 4

        /** The longest wait between probes, after repeated failures: about sixteen seconds. */
        const val MAX_PROBE_WAIT_WINDOWS = 32
    }
}
