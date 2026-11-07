/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * A browser frame loop whose frames run as ordinary tasks rather than inside `requestAnimationFrame`.
 *
 * A browser runs input events and animation-frame callbacks ahead of ordinary tasks, a worker's
 * messages among them. A frame that runs in the animation-frame callback and takes longer than the
 * key-repeat interval (about 33 ms) while a key is held always has a newer input event or frame
 * waiting, so a worker's messages wait behind them for as long as the key is held: a socket in a
 * worker then delivers nothing to the page, not a snapshot and not a pong. Here each animation frame
 * only asks for the next one and [post]s its frame as an ordinary task, which takes its turn with the
 * worker's messages. A frame that hasn't run yet when the next animation frame comes is not posted
 * twice: that animation frame is skipped, and the frame runs once, with the newest timestamp.
 *
 * [requestFrame] is `requestAnimationFrame`; [post] queues an ordinary task, a `MessageChannel`
 * message in a browser. [frame] runs one frame at the timestamp, in milliseconds, of the animation
 * frame it was asked for, and answers false to stop the loop. Single-threaded, as the page is.
 */
internal class PostedFrameLoop(
    private val requestFrame: (callback: (Double) -> Unit) -> Unit,
    private val post: (task: () -> Unit) -> Unit,
    private val frame: (timeMillis: Double) -> Boolean,
) {
    private var started = false
    private var stopped = false
    private var posted = false
    private var latestTime = 0.0
    private val afterFrame = ArrayList<() -> Unit>()
    private val running = ArrayList<() -> Unit>()

    // Made once, so a frame allocates neither callback.
    private val onAnimationFrame: (Double) -> Unit = ::animationFrame
    private val onTask: () -> Unit = ::runFrame

    /** Whether the loop has stopped: a frame answered false, or [stop] was called. */
    val isStopped: Boolean get() = stopped

    /** Starts the loop at the next animation frame; once. */
    fun start() {
        if (started || stopped) return
        started = true
        requestFrame(onAnimationFrame)
    }

    /** Stops the loop: no frame runs or is asked for after this. */
    fun stop() {
        stopped = true
    }

    /**
     * Runs [action] once the next frame has run, so what it undoes stays in place for that frame. A
     * browser's own animation-frame callbacks now run before the frame does, so they can't do this.
     */
    fun afterNextFrame(action: () -> Unit) {
        afterFrame += action
    }

    private fun animationFrame(time: Double) {
        if (stopped) return
        latestTime = time
        requestFrame(onAnimationFrame)
        if (posted) return
        posted = true
        post(onTask)
    }

    private fun runFrame() {
        posted = false
        if (stopped) return
        if (!frame(latestTime)) stopped = true
        if (afterFrame.isEmpty()) return
        running += afterFrame
        afterFrame.clear()
        running.forEach { it() }
        running.clear()
    }
}
