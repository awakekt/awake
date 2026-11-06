// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0

package com.awakekt.awake.engine.window

/**
 * A frame loop that times frames with the display's frame callback but runs each frame's work as an ordinary task.
 *
 * A browser runs `requestAnimationFrame` callbacks and input events ahead of other tasks. Once a frame's work takes
 * longer than the gap between two key repeats (about 33 ms), a held key always leaves one of them waiting, and messages
 * from a worker (a socket's pongs and snapshots) don't run until the key is released. Run as a posted task, the work
 * takes its turn with those messages instead.
 *
 * Each frame callback asks for the next one first, so the rate is still the display's, then posts the work. While a
 * frame's work is posted but hasn't run, later callbacks skip, so slow frames never pile up.
 *
 * @param requestFrame asks for the next frame callback, which receives its timestamp in milliseconds
 * (`window.requestAnimationFrame`).
 * @param postTask runs the work it is given as a task of its own, after the tasks already waiting (a `MessageChannel`).
 * @param runFrame does one frame's work for the callback's timestamp, and returns false to stop the loop.
 */
internal class TaskFrameLoop(
    private val requestFrame: (callback: (time: Double) -> Unit) -> Unit,
    private val postTask: (work: () -> Unit) -> Unit,
    private val runFrame: (time: Double) -> Boolean,
) {
    private var stopped = false
    private var posted = false
    private var postedTime = 0.0

    // Made once, so a frame allocates neither callback.
    private val onFrame: (Double) -> Unit = ::frameCallback
    private val work: () -> Unit = ::runPosted

    /** How many frame callbacks were skipped because the frame before them hadn't run yet. */
    var skippedFrames: Long = 0
        private set

    /** Asks for the first frame callback. */
    fun start() {
        requestFrame(onFrame)
    }

    private fun frameCallback(time: Double) {
        if (stopped) return
        requestFrame(onFrame)
        if (posted) {
            skippedFrames++
            return
        }
        posted = true
        postedTime = time
        postTask(work)
    }

    private fun runPosted() {
        try {
            if (!stopped && !runFrame(postedTime)) stopped = true
        } finally {
            posted = false
        }
    }
}
