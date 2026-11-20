/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.PhaseReadCause
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An idle canvas with followers composes nothing, after some of them are hidden or dropped (#1512).
 *
 * A private consumer pack's scene canvas composed and laid out twice on every frame, and never
 * skipped: placement bumped one `MutableState` by two each frame (`ComposeHost.explainPlacement`).
 * A follower that was drawn and then hidden kept its plan saying "drawn", while the composition no
 * longer composed it, so its plan differed from what was composed on every placement.
 */
class CanvasFollowerIdleTest {
    private val resolver = object : CanvasResolver {
        override fun project(target: String, offset: Vec3f): CanvasProjection {
            val index = target.removePrefix("node").toInt()
            return CanvasProjection(START + (index % COLUMNS) * SPACING, START + (index / COLUMNS) * SPACING, distance = 1f + index)
        }
    }

    private val all = List(FOLLOWERS) { index ->
        CanvasItem(
            index + 1,
            CanvasElement().apply {
                kind = CanvasElementKind.Bar
                follow = "node$index"
                width = 40f
                height = 6f
                value = 0.5f
            },
        )
    }
    private var items = all

    private fun host() = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        explainPlacement = true
    }

    private fun ComposeHost.frame() = frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(items, resolver = resolver) }

    /** Placement recompositions and skipped compositions over [FRAMES] idle frames, after [change] and a settle. */
    private fun idleAfter(change: () -> Unit): Pair<Int, Int> {
        val host = host()
        repeat(SETTLE) { host.frame() }
        change()
        host.invalidate()
        repeat(SETTLE) { host.frame() }
        val placed = host.placementRecompositions
        val skipped = host.skippedCompositions
        repeat(FRAMES) { host.frame() }
        val cause = host.frameCounts.placementCause
        host.dispose()
        val result = (host.placementRecompositions - placed) to (host.skippedCompositions - skipped)
        println("CanvasFollowerIdle: placement recompositions ${result.first}, skipped ${result.second} of $FRAMES, last cause $cause")
        assertEquals(PhaseReadCause.None, cause, "what placement changed on the last idle frame")
        return result
    }

    @Test
    fun anIdleCanvasComposesNothing() {
        assertEquals(0 to FRAMES, idleAfter {})
    }

    @Test
    fun hidingFollowersLeavesAnIdleCanvasComposingNothing() {
        assertEquals(0 to FRAMES, idleAfter { all.forEachIndexed { i, item -> if (i % 3 == 0) item.element.visible = false } })
    }

    @Test
    fun droppingFollowersLeavesAnIdleCanvasComposingNothing() {
        assertEquals(0 to FRAMES, idleAfter { items = all.filterIndexed { i, _ -> i % 3 != 0 } })
    }

    private companion object {
        const val FOLLOWERS = 60
        const val COLUMNS = 10
        const val START = 20f
        const val SPACING = 50f
        const val WIDTH = 640
        const val HEIGHT = 360
        const val SETTLE = 10
        const val FRAMES = 80
    }
}
