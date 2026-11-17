/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * [FollowerStageHeap] on the JVM (#1512): what each stage of a skipped follower frame allocates, the
 * line [FollowerStageHeapBrowserTest]'s figures are read against.
 */
class FollowerStageHeapProbeTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Double = threads.currentThreadAllocatedBytes.toDouble()

    @Test
    fun aSkippedFollowerFramesAllocationIsNamedStageByStage() {
        val moving = FollowerStageHeap(::allocated).measure(moving = true, WARM_UP, FRAMES) {}
        val still = FollowerStageHeap(::allocated).measure(moving = false, WARM_UP, FRAMES) {}
        println("FollowerStageHeap (JVM): ${FollowerStageHeap.PLATES} plates, KB allocated a skipped frame: ${FollowerStageHeap.line(moving, still)}")
    }

    private companion object {
        const val WARM_UP = 200
        const val FRAMES = 100
    }
}
