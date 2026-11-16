/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A canvas in a host that skips unchanged composition and measure (#1512): still, it composes and
 * measures nothing; with a trail draining or a particle running, it repaints that element alone.
 */
class CanvasSkipGateTest {
    private fun host() = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = true
    }

    private val label = CanvasItem(
        1,
        CanvasElement().apply {
            kind = CanvasElementKind.Text
            text = "Harbor Town"
            width = 120f
            height = 20f
        },
    )

    private val bar = CanvasElement().apply {
        kind = CanvasElementKind.Bar
        width = 200f
        height = 20f
        offsetY = 40f
        value = 1f
        bar = CanvasBar(trailColor = "#ff0000", trailDelay = 0.1f, trailSpeed = 0.5f)
    }

    private val sparks = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        width = 100f
        height = 100f
        offsetY = 80f
        particles = CanvasParticles(maxParticles = 64, random = Random(3)).apply {
            spawnRate = 60f
            lifetime = 0.5f
            size = 4f
        }
    }

    private fun ComposeHost.run(items: List<CanvasItem>, frames: Int, check: (Int) -> Unit = {}) = repeat(frames) {
        frame(FrameInput(640, 360)) { CanvasOverlay(items) }
        check(it)
    }

    @Test
    fun aStillCanvasComposesAndMeasuresNothing() {
        val host = host()
        val items = listOf(label, CanvasItem(2, bar))
        host.run(items, SETTLE_FRAMES)
        host.run(items, GATED_FRAMES) {
            assertFalse(host.frameCounts.composed, "frame $it composed: ${host.frameCounts}")
            assertFalse(host.frameCounts.measured, "frame $it measured: ${host.frameCounts}")
            assertEquals(0, host.frameCounts.nodesDrawn, "frame $it painted: ${host.frameCounts}")
        }
    }

    @Test
    fun aDrainingTrailRepaintsItsBarAlone() {
        val host = host()
        val items = listOf(label, CanvasItem(2, bar))
        host.run(items, SETTLE_FRAMES)
        bar.value = 0.2f
        host.invalidate()
        host.run(items, 1)
        val start = bar.trail.value
        host.run(items, GATED_FRAMES) {
            assertFalse(host.frameCounts.composed, "frame $it composed: ${host.frameCounts}")
            assertFalse(host.frameCounts.measured, "frame $it measured: ${host.frameCounts}")
            // Only the path from the root to the moving element paints; the label beside it replays.
            assertTrue(host.frameCounts.nodesDrawn in 1..MAX_REPAINTED, "frame $it painted: ${host.frameCounts}")
            assertTrue(host.frameCounts.subtreesReplayed >= 1, "frame $it replayed nothing: ${host.frameCounts}")
        }
        assertTrue(bar.trail.value < start, "the trail drained: ${bar.trail.value} from $start")
        host.run(items, SETTLE_FRAMES)
        assertEquals(0.2f, bar.trail.value, "the trail met the value")
        host.run(items, GATED_FRAMES) { assertEquals(0, host.frameCounts.nodesDrawn, "frame $it painted, settled") }
    }

    @Test
    fun aRunningParticleRepaintsItsEmitterAlone() {
        val host = host()
        val items = listOf(label, CanvasItem(3, sparks))
        host.run(items, SETTLE_FRAMES)
        host.run(items, GATED_FRAMES) {
            assertFalse(host.frameCounts.composed, "frame $it composed: ${host.frameCounts}")
            assertFalse(host.frameCounts.measured, "frame $it measured: ${host.frameCounts}")
            // Only the path from the root to the moving element paints; the label beside it replays.
            assertTrue(host.frameCounts.nodesDrawn in 1..MAX_REPAINTED, "frame $it painted: ${host.frameCounts}")
            assertTrue(host.frameCounts.subtreesReplayed >= 1, "frame $it replayed nothing: ${host.frameCounts}")
        }
        assertTrue(checkNotNull(sparks.particles).simulation.spawnedTotal > 0, "the emitter ran")
    }

    private companion object {
        const val SETTLE_FRAMES = 120
        const val GATED_FRAMES = 30
        const val MAX_REPAINTED = 8
    }
}
