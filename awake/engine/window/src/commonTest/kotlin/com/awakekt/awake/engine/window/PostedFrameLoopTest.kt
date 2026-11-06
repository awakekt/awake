/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostedFrameLoopTest {
    /** A page's two queues: animation-frame callbacks and ordinary tasks, each run when the test says. */
    private class Page {
        val animationFrames = ArrayDeque<(Double) -> Unit>()
        val tasks = ArrayDeque<() -> Unit>()
        val frames = ArrayList<Double>()
        var keepRunning = true

        val loop = PostedFrameLoop(
            requestFrame = { animationFrames.addLast(it) },
            post = { tasks.addLast(it) },
            frame = { time -> frames += time; keepRunning },
        )

        /** The browser's next animation frame at [time]: the callbacks asked for before it. */
        fun vsync(time: Double) {
            repeat(animationFrames.size) { animationFrames.removeFirst()(time) }
        }

        fun runTasks() {
            while (tasks.isNotEmpty()) tasks.removeFirst()()
        }
    }

    @Test
    fun anAnimationFrameOnlyPostsTheFrameAndAsksForTheNext() {
        val page = Page()
        page.loop.start()
        page.vsync(16.0)
        // The frame hasn't run in the animation frame: it waits its turn as a task, the next frame already asked for.
        assertEquals(emptyList(), page.frames)
        assertEquals(1, page.tasks.size)
        assertEquals(1, page.animationFrames.size)
        page.runTasks()
        assertEquals(listOf(16.0), page.frames)
    }

    @Test
    fun aWorkerMessageQueuedBeforeTheFrameRunsFirst() {
        val page = Page()
        val order = ArrayList<String>()
        page.loop.start()
        page.tasks.addLast { order += "message" }
        page.vsync(16.0)
        page.tasks.addLast { order += "message after" }
        page.loop.afterNextFrame { order += "frame" }
        page.runTasks()
        assertEquals(listOf("message", "frame", "message after"), order)
    }

    @Test
    fun aFrameStillWaitingIsNotPostedAgainAndRunsWithTheNewestTime() {
        val page = Page()
        page.loop.start()
        page.vsync(16.0)
        page.vsync(33.0)
        page.vsync(50.0)
        assertEquals(1, page.tasks.size)
        page.runTasks()
        assertEquals(listOf(50.0), page.frames)
        page.vsync(66.0)
        page.runTasks()
        assertEquals(listOf(50.0, 66.0), page.frames)
    }

    @Test
    fun afterNextFrameRunsOnceTheNextFrameHasRunAndOnlyOnce() {
        val page = Page()
        var released = 0
        page.loop.start()
        page.loop.afterNextFrame { released++ }
        page.vsync(16.0)
        // Animation-frame callbacks ran, but the frame hasn't: what the action undoes is still in place.
        assertEquals(0, released)
        page.runTasks()
        assertEquals(1, released)
        page.vsync(33.0)
        page.runTasks()
        assertEquals(1, released)
    }

    @Test
    fun aFrameThatAnswersFalseStopsTheLoop() {
        val page = Page()
        page.loop.start()
        page.keepRunning = false
        page.vsync(16.0)
        page.runTasks()
        assertTrue(page.loop.isStopped)
        page.vsync(33.0)
        assertEquals(0, page.animationFrames.size)
        assertEquals(0, page.tasks.size)
        assertEquals(listOf(16.0), page.frames)
    }

    @Test
    fun stopBeforeAPostedFrameRunsDropsIt() {
        val page = Page()
        page.loop.start()
        page.vsync(16.0)
        page.loop.stop()
        page.runTasks()
        assertEquals(emptyList(), page.frames)
    }
}
