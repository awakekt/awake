/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.testing.allocation.assertAllocatesAtMost
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bound elements (`bind`) in a host that skips unchanged frames (#1512). A bound field is polled for
 * its value, so a HUD whose game state holds still composes nothing, and one written in place
 * composes on the frames it was written, with no skipped frame drawing stale output. Before this, a
 * shown bound element counted as changing every frame and kept the canvas composing.
 */
class SceneCanvasBindSkipGateTest {
    private fun host(verify: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        verifySkippedCompositions = verify
    }

    private fun ComposeHost.frame(hud: BoundHud) = frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(hud.world, bindings = HUD_BINDINGS) }

    @Test
    fun aBoundHudWhoseStateHoldsStillComposesNothing() {
        val hud = BoundHud(units = 3)
        val host = host(verify = false)
        repeat(SETTLE_FRAMES) { host.frame(hud) }
        repeat(GATED_FRAMES) {
            hud.health[0].current = 80f // Equal writes, as a system rewriting its state each frame makes.
            host.frame(hud)
            assertFalse(host.frameCounts.composed, "frame $it composed: ${host.frameCounts}")
        }
    }

    @Test
    fun eachKindOfBoundFieldComposesWhenWrittenAndNoSkippedFrameIsStale() {
        val hud = BoundHud(units = 2)
        // A verifying host composes its skipped frames to compare them; the plain one shows what skips.
        val host = host(verify = true)
        val plain = host(verify = false)
        repeat(SETTLE_FRAMES) { host.frame(hud); plain.frame(hud) }
        val writes = listOf<Pair<String, () -> Unit>>(
            "a bar's value and a text's placeholder" to { hud.health[1].current -= 5f },
            "a bar's max and a formatted placeholder" to { hud.health[1].max += 10f },
            "a button's cooldown" to { hud.slots[1].left += 1f },
            "a button's cooldown max" to { hud.slots[1].total += 2f },
            "a button's charges" to { hud.slots[1].rounds -= 1 },
            "a button's enabled" to { hud.slots[1].ready = !hud.slots[1].ready },
        )
        for ((what, write) in writes) {
            write()
            host.frame(hud)
            plain.frame(hud)
            assertTrue(plain.frameCounts.composed, "$what composed the next frame")
            host.frame(hud)
            plain.frame(hud)
            assertFalse(plain.frameCounts.composed, "$what: settled again: ${plain.frameCounts}")
        }
        assertEquals(0, host.staleSkips, "a skipped frame would have drawn stale output")
    }

    @Test
    fun aHudWrittenOneFrameInTenComposesOnlyThoseFrames() {
        val hud = BoundHud(units = 100)
        val host = host(verify = false)
        repeat(SETTLE_FRAMES) { host.frame(hud) }
        var composed = 0
        repeat(PROBE_FRAMES) { frame ->
            if (frame % 10 == 0) hud.health[frame % 100].current -= 1f
            host.frame(hud)
            if (host.frameCounts.composed) composed++
        }
        println("SceneCanvasBindSkip: 300 elements, 300 of them bound, written 1 frame in 10: composed $composed of $PROBE_FRAMES frames")
        assertEquals(PROBE_FRAMES / 10, composed, "only the frames with a write compose")
    }

    /**
     * The poll's own cost at 100 bound elements, two fields each: the bound components exported
     * through their bindings and read without JSON. Measured at 126 KB and 186 µs a frame through JSON.
     */
    @Test
    fun pollingAHundredBoundElementsCostsAFewKilobytesAFrame() {
        val hud = BoundHud(units = 100)
        val named = CanvasNodeNames(hud.world)
        val bound = BoundFields(named, CanvasFieldReader(hud.world, HUD_BINDINGS))
        bound.begin()
        repeat(100) { bound.add(com.awakekt.awake.ui.canvas.CanvasBinding("Unit$it", value = "health.current", max = "health.max")) }
        bound.end()
        repeat(WARMUP) { named.nextFrame(); bound.changed() }
        val start = System.nanoTime()
        repeat(MEASURED) { named.nextFrame(); bound.changed() }
        val micros = (System.nanoTime() - start) / MEASURED / 1000
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { named.nextFrame(); bound.changed() } }
        println("SceneCanvasBindPoll: 100 bound elements, 2 fields each: $reading and $micros us a frame")
        assertAllocatesAtMost(reading, MAX_POLL_BYTES, "a poll of 100 bound elements")
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val SETTLE_FRAMES = 30
        const val GATED_FRAMES = 30
        const val PROBE_FRAMES = 300
        const val WARMUP = 300
        const val MEASURED = 500

        /** The exported components, 100 small objects: a few KB, against the JSON's 126 KB. */
        const val MAX_POLL_BYTES = 4096L
    }
}
