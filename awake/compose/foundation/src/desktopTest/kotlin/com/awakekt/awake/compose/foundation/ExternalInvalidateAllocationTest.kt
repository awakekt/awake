/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
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
 * #1544: asking [ComposeHost.externalChanges] every frame costs an idle, skipping host nothing.
 * An idle frame with the hook allocates what one without it does.
 */
class ExternalInvalidateAllocationTest {
    private val input = FrameInput(400, 300, deltaSeconds = 0f)

    private fun bytesPerIdleFrame(withHook: Boolean): Long {
        val host = ComposeHost().also { it.skipUnchangedComposition = true }
        var hp = 7
        if (withHook) host.externalChanges = { hp < 0 }
        fun frame() = host.frame(input) {
            Column {
                Text("hp $hp")
                Box(Modifier.size(40.dp, 20.dp).background(Color(0.1f, 0.8f, 0.1f)))
            }
        }
        repeat(WARMUP) { frame() }
        var quietest = Long.MAX_VALUE
        repeat(WINDOWS) {
            val start = allocatedBytes()
            repeat(WINDOW) { frame() }
            quietest = minOf(quietest, (allocatedBytes() - start) / WINDOW)
        }
        hp += 1
        return quietest
    }

    @Test
    fun theHookAddsNoAllocationToAnIdleFrame() {
        val without = bytesPerIdleFrame(withHook = false)
        val with = bytesPerIdleFrame(withHook = true)
        println("PERF externalChanges idle bytes/frame without=$without with=$with")
        assertTrue(with <= without + SLACK_BYTES, "the per-frame hook allocates: without=$without with=$with")
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val WARMUP = 300
        const val WINDOW = 100
        const val WINDOWS = 6

        // Measurement noise of the thread counter, far below one object per frame.
        const val SLACK_BYTES = 8L
    }
}
