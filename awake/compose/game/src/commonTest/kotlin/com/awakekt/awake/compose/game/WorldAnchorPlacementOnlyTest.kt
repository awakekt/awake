/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * World anchors on the placement-only path (#1512): with `skipUnchangedComposition` and
 * `skipUnchangedMeasure` on, an anchor that moves is placed again and nothing of it composes or
 * measures, and an anchor that hides or shows measures on the frame it changed. Each frame draws
 * what a host that composes and measures every frame draws.
 */
class WorldAnchorPlacementOnlyTest {
    private class Spot(val index: Int) : AnchorProvider {
        var tick = 0
        var visible = true

        override fun anchor(into: AnchorPoint) {
            into.x = 30f + index * 40 + tick * 2
            into.y = 40f + tick
            into.visible = visible
        }
    }

    /** Counts the skipping host's measures of the anchored content; the reference measures every frame. */
    private inner class Counting : MeasurePolicy {
        var measures = 0

        override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
            if (skippingNow) measures++
            return layout(constraints.constrainWidth(0), constraints.constrainHeight(0)) {}
        }
    }

    private object CountingNode

    private val spots = List(3) { Spot(it) }
    private val content = Counting()
    private var compositions = 0
    private var skippingNow = false
    private val reference = ComposeHost()
    private val skipping = ComposeHost().also {
        it.skipUnchangedComposition = true
        it.skipUnchangedMeasure = true
    }

    private fun frame(tick: Int) {
        for (spot in spots) spot.tick = tick
        val expected = reference.frame(FrameInput(320, 240)) { nameplates() }.primitives.toList()
        skippingNow = true
        val actual = try {
            skipping.frame(FrameInput(320, 240)) { nameplates() }.primitives
        } finally {
            skippingNow = false
        }
        assertEquals(expected, actual, "frame $tick drew something else from the measuring host")
    }

    context(_: Composer)
    private fun nameplates() {
        if (skippingNow) compositions++
        WorldAnchorLayout(Modifier.fillMaxSize()) {
            for (spot in spots) {
                Layout(CountingNode, Modifier.worldAnchor(spot).size(30.dp, 8.dp).background(Color(0.2f, 0.6f, 0.2f)), content)
            }
        }
    }

    @Test
    fun movingAnchorsArePlacedAgainWithoutComposingOrMeasuring() {
        frame(0)
        frame(0)
        val composed = compositions
        val measured = content.measures

        for (tick in 1..20) frame(tick)

        assertEquals(composed, compositions, "moving anchors recomposed")
        assertEquals(measured, content.measures, "moving anchors measured their content")
        val bars = skipping.frame(FrameInput(320, 240)) { nameplates() }.primitives.filterIsInstance<UiDrawPrimitive.Quad>()
        assertEquals(30f + 20 * 2 - 15f, bars.first().x, "placed where the provider says on the latest frame")
    }

    @Test
    fun anAnchorThatHidesOrShowsMeasuresOnThatFrame() {
        frame(0)
        frame(0)
        val measured = content.measures

        spots[1].visible = false
        frame(1)
        assertEquals(measured + spots.size, content.measures, "hiding a child sizes it to nothing on that frame")

        frame(2)
        assertEquals(measured + spots.size, content.measures, "nothing changed but position")

        spots[1].visible = true
        frame(3)
        assertEquals(measured + 2 * spots.size, content.measures, "showing it measures it again")
    }
}
