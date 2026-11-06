// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0

package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskFrameLoopTest {
    /** A page: frame callbacks the test fires, and posted tasks that run in order when the test drains them. */
    private class Page {
        val frameCallbacks = ArrayDeque<(Double) -> Unit>()
        val tasks = ArrayDeque<() -> Unit>()
        val ran = mutableListOf<Double>()

        fun frame(time: Double) {
            val waiting = frameCallbacks.toList()
            frameCallbacks.clear()
            waiting.forEach { it(time) }
        }

        fun drainTasks() {
            while (tasks.isNotEmpty()) tasks.removeFirst()()
        }

        fun loop(keepRunning: (Double) -> Boolean = { true }) = TaskFrameLoop(
            requestFrame = { frameCallbacks += it },
            postTask = { tasks += it },
            runFrame = { time -> ran += time; keepRunning(time) },
        )
    }

    @Test
    fun theWorkRunsAsAPostedTaskNotInsideTheFrameCallback() {
        val page = Page()
        page.loop().start()

        page.frame(16.0)

        assertTrue(page.ran.isEmpty(), "the frame callback only posts the work")
        assertEquals(1, page.tasks.size)
        page.drainTasks()
        assertEquals(listOf(16.0), page.ran)
    }

    @Test
    fun eachFrameCallbackAsksForTheNextBeforeTheWorkRuns() {
        val page = Page()
        page.loop().start()

        page.frame(16.0)

        assertEquals(1, page.frameCallbacks.size, "the next frame is already asked for")
    }

    @Test
    fun aFrameIsSkippedWhileTheOneBeforeItHasNotRun() {
        val page = Page()
        val loop = page.loop()
        loop.start()

        page.frame(16.0)
        page.frame(33.0)
        page.frame(50.0)
        page.drainTasks()

        assertEquals(listOf(16.0), page.ran, "only the first frame's work was posted")
        assertEquals(2, loop.skippedFrames)

        page.frame(66.0)
        page.drainTasks()
        assertEquals(listOf(16.0, 66.0), page.ran)
    }

    @Test
    fun workReturningFalseStopsTheLoop() {
        val page = Page()
        page.loop { time -> time < 30.0 }.start()

        page.frame(16.0)
        page.drainTasks()
        page.frame(33.0)
        page.drainTasks()
        page.frame(50.0)
        page.drainTasks()

        assertEquals(listOf(16.0, 33.0), page.ran)
        assertTrue(page.frameCallbacks.isEmpty(), "a stopped loop asks for no more frames")
    }

    @Test
    fun aFrameThatThrowsStillLetsTheNextOneRun() {
        val page = Page()
        var first = true
        TaskFrameLoop(
            requestFrame = { page.frameCallbacks += it },
            postTask = { page.tasks += it },
            runFrame = { time ->
                page.ran += time
                if (first) {
                    first = false
                    error("boom")
                }
                true
            },
        ).start()

        page.frame(16.0)
        runCatching { page.drainTasks() }
        page.frame(33.0)
        page.drainTasks()

        assertEquals(listOf(16.0, 33.0), page.ran)
    }
}
