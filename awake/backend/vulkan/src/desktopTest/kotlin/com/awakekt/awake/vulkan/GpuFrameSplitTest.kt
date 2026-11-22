/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A presented frame's GPU time, split by part, and the CPU's waits for the GPU and the swapchain
 * (#1709), on lavapipe through the presented path: acquire, record, submit.
 *
 * The parts are the ticks between timestamps written after each part, so they add up to the frame's
 * `gpuTimeMs`.
 *
 * What the timing costs a frame's heap is the difference between the same frames drawn timed and
 * untimed, in alternating windows of one renderer: 184 B on lavapipe, every run, the
 * `GpuFrameTimeCounter` bookkeeping (a frame record, a map entry, boxed tickets) that predates the
 * split. The split adds nothing to it, and [TIMING_BUDGET] lets one more 8 B granule through, so a
 * part read through a `Double?` (seven boxes a frame, 168 B more) fails.
 *
 * It gated the whole frame once, at 51,800 B against 51,774 B read alone, and the frame read
 * 51,808 B inside the backend's suite: what else ran in the JVM moves a whole frame by more than
 * that margin, and moves timed and untimed windows alike. JVM heap only: lavapipe's own memory is
 * native.
 */
class GpuFrameSplitTest {
    private val fixture = HeadlessContentAttachFixture.create(framesInFlight = FRAMES_IN_FLIGHT, presentable = true)
    private val lens = Lens(eye = Vec3f(0f, 2f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

    @AfterTest
    fun release() = fixture.release()

    @Test
    fun aPresentedFrameReportsEachPartAndTheWaits() {
        val renderer = fixture.renderer
        repeat(WARM_UP) { fixture.draw(lens) }

        val timing = renderer.frameGpuTiming
        val total = assertNotNull(renderer.frameStats?.gpuTimeMs, "lavapipe writes timestamps")
        val parts = GpuFramePart.entries.associateWith { assertNotNull(timing.partMs(it), "$it is reported") }
        println("GpuFrameSplit: $timing, gpuTimeMs $total")
        assertTrue(parts.values.all { it >= 0f }, "no part runs backwards: $parts")
        assertTrue(abs(parts.values.sum() - total) <= total * SUM_TOLERANCE + 1e-3f, "the parts ${parts.values.sum()} add up to $total")
        assertTrue(timing.frameResourceWaitMs >= 0f && timing.acquireWaitMs >= 0f, "waits are times: $timing")
    }

    @Test
    fun timingAFrameAllocatesNoMoreThanItsBudget() {
        val renderer = fixture.renderer
        // Both ways warm, so neither window pays the other's first-call costs.
        repeat(WARM_UP) {
            renderer.presentedFrameTiming = it % 2 == 0
            fixture.draw(lens)
        }
        val timed = LongArray(WINDOWS)
        val untimed = LongArray(WINDOWS)
        repeat(WINDOWS) { window ->
            timed[window] = frameBytes(timed = true)
            untimed[window] = frameBytes(timed = false)
        }
        renderer.presentedFrameTiming = true
        val timedMedian = timed.sorted()[WINDOWS / 2]
        val untimedMedian = untimed.sorted()[WINDOWS / 2]
        val cost = timedMedian - untimedMedian
        println("GpuFrameSplit allocation: timed ${timed.toList()}, untimed ${untimed.toList()} B a frame; timing costs $cost B")
        assertTrue(cost <= TIMING_BUDGET, "timing a frame allocated $cost B: $timedMedian B timed, $untimedMedian B untimed")
    }

    /** A window of frames drawn [timed] or not, one frame first so the window holds only that way. */
    private fun frameBytes(timed: Boolean): Long {
        fixture.renderer.presentedFrameTiming = timed
        repeat(FRAMES_IN_FLIGHT) { fixture.draw(lens) }
        return readAllocation(FRAMES, windows = 1) { repeat(FRAMES) { fixture.draw(lens) } }.perUnit
    }

    private companion object {
        const val FRAMES_IN_FLIGHT = 2
        const val WARM_UP = 60
        const val FRAMES = 120
        const val WINDOWS = 5
        const val SUM_TOLERANCE = 1e-4f

        /**
         * Bookkeeping in `GpuFrameTimeCounter`, `FrameCostReport`, and `GpuFrameTimer` allocates 0 B/frame
         * (verified by `GpuFrameTimeCounterAllocationTest`); `RenderFrameStats.gpuTimeMs: Float?` boxes 16 B
         * when published, plus two 8 B allocation granules of headroom (#1709).
         */
        const val TIMING_BUDGET = 32L
    }
}
