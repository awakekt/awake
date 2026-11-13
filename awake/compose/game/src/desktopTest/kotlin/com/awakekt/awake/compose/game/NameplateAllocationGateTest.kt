/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fails when a frame of world-anchored nameplates allocates more than [CEILING_BYTES] (#1512).
 *
 * The content is built the way a caller builds it, through [ComposeHost.frame]: 200 anchors on a
 * 1280x720 surface, each a 70x20 frame, a bar and one line of text, with modifier chains, colours
 * and the label built inside the composition each frame, all anchors moving every frame. It runs
 * with escape analysis off (`awake/compose/game/build.gradle.kts`, the `desktopTest` task's
 * `-XX:-DoEscapeAnalysis`), which matches Android's runtime, so it counts what a phone allocates.
 *
 * Measured on a 4-core 2.1 GHz Xeon, OpenJDK 17, with the `NodeChildren.indices` fix (#1515): 423,578 B
 * per frame over 400 frames after 300 warm-up frames, identical in five runs (spread 0, allocation
 * is deterministic here). The ceiling is 435,000 B: 11,422 B (2.7%) of headroom for JDK and
 * platform differences. Without the fix a frame is about 687,000 B and this fails.
 *
 * `ComposeHost.skipUnchangedComposition` (stage a) is off by default, so [CEILING_BYTES] still gates
 * the default path. With it on, a frame of this content is 221,482 B whether the anchors move or
 * not, and [STILL_SKIP_CEILING_BYTES] is that plus the same 2.7% headroom.
 *
 * **Each later step of #1512 lowers [CEILING_BYTES]** to the new measurement plus the same headroom;
 * a step that does not lower it did not save allocation.
 *
 * Only the whole frame is asserted. Per-phase bytes (recompose, measure, draw) cannot be read
 * through [ComposeHost.frame], and splitting them needs engine hooks that do not exist. The gate
 * catches a rise in bytes per frame of more than the headroom, wherever it comes from. It cannot say
 * which phase grew, and it does not see time or objects the JIT removes on a desktop JVM. Time is
 * printed for reference and never asserted.
 */
class NameplateAllocationGateTest {
    private class Spot(val index: Int) : AnchorProvider {
        var tick = 0

        override fun anchor(into: AnchorPoint) {
            into.x = 40f + (index * 37 + tick * 3) % 1200
            into.y = 40f + (index * 53 + tick * 2) % 640
        }
    }

    private val spots = List(COUNT) { Spot(it) }

    @Test
    fun framesOfMovingNameplatesStayUnderTheAllocationCeiling() {
        val perFrame = measure(skip = false, moving = true)
        assertTrue(perFrame <= CEILING_BYTES, "a frame of $COUNT nameplates allocated $perFrame B, over the $CEILING_BYTES B ceiling")
    }

    /**
     * Skipping the composition pass (#1512 stage a), anchors still. The content reads only what
     * the anchors read at placement, so nothing recomposes and a frame is measure, place and paint.
     */
    @Test
    fun stillFramesWithSkippedCompositionStayUnderTheirCeiling() {
        val perFrame = measure(skip = true, moving = false)
        assertTrue(
            perFrame <= STILL_SKIP_CEILING_BYTES,
            "a still frame of $COUNT nameplates allocated $perFrame B, over the $STILL_SKIP_CEILING_BYTES B ceiling",
        )
    }

    /**
     * Anchors moving with skipping on. They move at placement, which no composition read, so these
     * frames skip too: 221,483 B against 423,578 B. Content that read a moving value while composing
     * would recompose every frame and cost what [CEILING_BYTES] allows.
     */
    @Test
    fun movingFramesWithSkippedCompositionDoNotCostMore() {
        val perFrame = measure(skip = true, moving = true)
        assertTrue(
            perFrame <= STILL_SKIP_CEILING_BYTES,
            "a moving frame with skipping allocated $perFrame B, over the $STILL_SKIP_CEILING_BYTES B ceiling",
        )
    }

    private fun measure(skip: Boolean, moving: Boolean): Long {
        val host = ComposeHost()
        host.skipUnchangedComposition = skip
        var tick = 0

        fun frame() {
            if (moving) tick++
            for (spot in spots) spot.tick = tick
            host.frame(FrameInput(WIDTH, HEIGHT)) { nameplates() }
        }
        repeat(WARMUP) { frame() }
        val before = allocatedBytes()
        val start = System.nanoTime()
        repeat(FRAMES) { frame() }
        val perFrame = (allocatedBytes() - before) / FRAMES
        val millis = (System.nanoTime() - start) / 1e6 / FRAMES
        println(
            "NameplateAllocationGate: skip=$skip moving=$moving $perFrame B/frame, ${"%.3f".format(millis)} ms/frame, " +
                "skipped ${host.skippedCompositions} of ${WARMUP + FRAMES}",
        )
        return perFrame
    }

    context(_: Composer)
    private fun nameplates() {
        WorldAnchorLayout(Modifier.fillMaxSize()) {
            for (i in 0 until COUNT) {
                Box(Modifier.worldAnchor(spots[i]).size(70.dp, 20.dp).background(Color(0.1f, 0.1f, 0.1f))) {
                    Box(Modifier.size(36.dp, 4.dp).background(Color(0.2f, 0.8f, 0.2f)))
                    Text("Player $i")
                }
            }
        }
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 200
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val WARMUP = 300
        const val FRAMES = 400
        const val CEILING_BYTES = 435_000
        const val STILL_SKIP_CEILING_BYTES = 227_500
    }
}
