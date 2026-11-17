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
        val moving = FollowerStageHeap(::allocated).measure(moving = true, WARM_UP, FRAMES, {})
        val still = FollowerStageHeap(::allocated).measure(moving = false, WARM_UP, FRAMES, {})
        println("FollowerStageHeap (JVM): ${FollowerStageHeap.PLATES} plates, KB allocated a skipped frame: ${FollowerStageHeap.line(moving, still)}")
        val unfollowed = FollowerStageHeap(::allocated).measure(moving = false, WARM_UP, FRAMES, {}, FollowerParts.All.copy(follow = false))
        val boxes = PlainOffsetBoxes(PLAIN_BOXES)
        val plain = FollowerStageHeap(::allocated).measure(WARM_UP, FRAMES, {}) { boxes.frame(it) }
        val perLink = PlainOffsetBoxes.Links.entries.joinToString("; ") { links ->
            val kind = PlainOffsetBoxes(PLAIN_BOXES, links)
            val placement = FollowerStageHeap(::allocated).measure(WARM_UP, FRAMES, {}) { kind.frame(it) }[1]
            "$links ${((placement * 10).toInt() / 10.0)}"
        }
        println("FollowerStageHeap (JVM), still, $PLAIN_BOXES plain boxes, KB allocated in placement by link: $perLink")
        println(
            "FollowerStageHeap (JVM), still: plates not following ${FollowerStageHeap.line(unfollowed)}; " +
                "$PLAIN_BOXES plain offset boxes ${FollowerStageHeap.line(plain)}",
        )
    }

    private companion object {
        const val WARM_UP = 200
        const val FRAMES = 100
        const val PLAIN_BOXES = 600
    }
}
