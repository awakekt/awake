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
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A HUD of [COUNT] world-anchored nameplates, three in four of them hidden, as players behind the
 * camera are: a hidden nameplate is left unplaced, so it costs nothing after composition (#1512).
 *
 * The default host, every switch off, escape analysis off (this module's test JVM). Before, a hidden
 * child was measured at 0x0, placed off screen, and still positioned, painted, hit-tested and
 * announced: 549,818 B and 1.556 ms a frame, all 200 announced. Now 335,203 B and 1.115 ms, 50
 * announced; [CEILING_BYTES] is that plus 2.7%, the headroom [NameplateAllocationGateTest] uses.
 */
class HiddenNameplateCostTest {
    private class Spot(private val index: Int) : AnchorProvider {
        var tick = 0

        override fun anchor(into: AnchorPoint) {
            into.visible = index % HIDDEN_EVERY == 0
            into.x = 40f + (index * 37 + tick * 3) % 1200
            into.y = 40f + (index * 53 + tick * 2) % 640
        }
    }

    private val spots = List(COUNT) { Spot(it) }

    @Test
    fun hiddenNameplatesAreNotDrawnOrAnnouncedAndCostNothingAfterComposition() {
        val host = ComposeHost()
        var tick = 0
        fun frame() = host.frame(FrameInput(WIDTH, HEIGHT)) { nameplates() }.also { tick++; spots.forEach { it.tick = tick } }

        val first = frame()
        val tagged = first.semantics.flatten().mapNotNull { it.testTag }.toSet()
        assertEquals((0 until COUNT step HIDDEN_EVERY).map { "plate$it" }.toSet(), tagged, "only the shown nameplates are announced")

        repeat(WARMUP) { frame() }
        val before = allocatedBytes()
        val start = System.nanoTime()
        repeat(FRAMES) { frame() }
        val perFrame = (allocatedBytes() - before) / FRAMES
        val millis = (System.nanoTime() - start) / 1e6 / FRAMES
        println("HiddenNameplateCost: $COUNT nameplates, ${COUNT / HIDDEN_EVERY} shown: $perFrame B/frame, ${"%.3f".format(millis)} ms/frame")
        assertTrue(perFrame <= CEILING_BYTES, "a frame allocated $perFrame B, over the $CEILING_BYTES B ceiling")
    }

    private fun List<com.awakekt.awake.compose.ui.semantics.SemanticsNode>.flatten(): List<com.awakekt.awake.compose.ui.semantics.SemanticsNode> =
        flatMap { listOf(it) + it.children.flatten() }

    context(_: Composer)
    private fun nameplates() {
        WorldAnchorLayout(Modifier.fillMaxSize()) {
            for (i in 0 until COUNT) {
                Box(Modifier.worldAnchor(spots[i]).size(70.dp, 20.dp).background(Color(0.1f, 0.1f, 0.1f)).testTag("plate$i")) {
                    Box(Modifier.size(36.dp, 4.dp).background(Color(0.2f, 0.8f, 0.2f)))
                    Text("Player $i")
                }
            }
        }
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 200
        const val HIDDEN_EVERY = 4
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val WARMUP = 300
        const val FRAMES = 400
        const val CEILING_BYTES = 344_500
    }
}
