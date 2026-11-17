/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * What a frame of a world canvas that is mostly followers costs (#1512), on the JVM: [FollowerHud]'s
 * 200 plates, with the nodes moving or still, for a host that composes every frame and one that
 * skips unchanged frames. Followers are planned and placed while placing, so with skipping on a frame
 * whose nodes only move composes and measures nothing, which this asserts; the rest is printed.
 */
class FollowerHudFrameProbeTest {
    @Test
    fun aFollowerHudsFrameIsMeasured() {
        val lines = listOf(false, true).flatMap { skip -> listOf(true, false).map { moving -> measure(skip, moving) } }
        println("FollowerHudFrame: $PLATES plates; ${lines.joinToString("; ")}")
    }

    private fun measure(skip: Boolean, moving: Boolean): String {
        val hud = FollowerHud(PLATES)
        val host = ComposeHost().apply {
            skipUnchangedComposition = skip
            skipUnchangedMeasure = skip
            retainUnchangedDraw = skip
        }
        repeat(WARM_UP) { hud.frame(host, moving) }
        var composed = 0
        val bytes = allocated()
        val mark = TimeSource.Monotonic.markNow()
        repeat(MEASURED) {
            hud.frame(host, moving)
            if (host.frameCounts.composed || host.frameCounts.measured) composed++
        }
        val micros = mark.elapsedNow().inWholeMicroseconds / MEASURED
        val perFrame = (allocated() - bytes) / MEASURED
        if (skip) assertEquals(0, composed, "with skipping on, moving=$moving: frames that composed or measured")
        return "skip=$skip moving=$moving: ${micros / MICROS_PER_MILLI} ms, ${perFrame / BYTES_PER_KB} KB a frame, composed or measured $composed of $MEASURED"
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 200
        const val MEASURED = 300
        const val MICROS_PER_MILLI = 1000.0
        const val BYTES_PER_KB = 1024.0
    }
}
