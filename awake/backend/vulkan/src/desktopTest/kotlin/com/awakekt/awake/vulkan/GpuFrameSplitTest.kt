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
 * `gpuTimeMs`. The split costs the frame no heap: a presented frame allocates within [FRAME_BUDGET],
 * what it did with two timestamps a frame (51,774 B on the JVM). Reading each part through a
 * `Double?` boxed seven times a frame, 51,935 B. JVM heap only: lavapipe's own memory is native.
 */
class GpuFrameSplitTest {
    private val fixture = HeadlessContentAttachFixture.create(framesInFlight = FRAMES_IN_FLIGHT, presentable = true)
    private val lens = Lens(eye = Vec3f(0f, 2f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

    @AfterTest
    fun release() = fixture.release()

    @Test
    fun aPresentedFrameReportsEachPartAndTheWaitsAndAllocatesNoMoreForIt() {
        val renderer = fixture.renderer
        repeat(WARM_UP) { fixture.draw(lens) }
        val perFrame = readAllocation(FRAMES) { repeat(FRAMES) { fixture.draw(lens) } }.perUnit

        val timing = renderer.frameGpuTiming
        val total = assertNotNull(renderer.frameStats?.gpuTimeMs, "lavapipe writes timestamps")
        val parts = GpuFramePart.entries.associateWith { assertNotNull(timing.partMs(it), "$it is reported") }
        println("GpuFrameSplit: $timing, gpuTimeMs $total, $perFrame B a frame")
        assertTrue(parts.values.all { it >= 0f }, "no part runs backwards: $parts")
        assertTrue(abs(parts.values.sum() - total) <= total * SUM_TOLERANCE + 1e-3f, "the parts ${parts.values.sum()} add up to $total")
        assertTrue(timing.frameResourceWaitMs >= 0f && timing.acquireWaitMs >= 0f, "waits are times: $timing")
        assertTrue(perFrame < FRAME_BUDGET, "a frame allocated $perFrame B")
    }

    private companion object {
        const val FRAMES_IN_FLIGHT = 2
        const val WARM_UP = 60
        const val FRAMES = 120
        const val SUM_TOLERANCE = 1e-4f
        const val FRAME_BUDGET = 51_800L
    }
}
