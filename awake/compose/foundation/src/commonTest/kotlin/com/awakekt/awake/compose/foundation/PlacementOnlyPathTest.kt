/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The placement-only path (#1512): a value read while placing or drawing moves its node, and a frame
 * that changes nothing else composes nothing and measures nothing, while drawing what a host that
 * composes and measures every frame draws, on the same frame.
 */
class PlacementOnlyPathTest {
    /**
     * A Box-like policy that counts how often the skipping host measured it, and reads [width] while
     * measuring if given. The reference host measures every frame by design, so it is not counted.
     */
    private inner class Counting(private val width: (() -> Int)? = null) : MeasurePolicy {
        var measures = 0

        override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
            if (skippingNow) measures++
            val inner = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
            val placeables = measurables.map { it.measure(inner) }
            val w = width?.invoke() ?: placeables.maxOfOrNull { it.width } ?: 0
            return layout(w, placeables.maxOfOrNull { it.height } ?: 0) { placeables.forEach { it.placeAt(0, 0) } }
        }
    }

    private object CountingNode

    private val moving = Counting()
    private val inner = Counting()
    private var compositions = 0

    // Set while the skipping host runs a frame, so counts are that host's alone.
    private var skippingNow = false
    private val skipping = ComposeHost().also {
        it.skipUnchangedComposition = true
        it.skipUnchangedMeasure = true
    }
    private val reference = ComposeHost()

    /** Runs [content] on both hosts and checks they drew the same thing. */
    private fun frame(input: FrameInput = FrameInput(320, 240), content: context(Composer) () -> Unit): FrameOutput {
        val expected = reference.frame(input, content)
        skippingNow = true
        val actual = try {
            skipping.frame(input, content)
        } finally {
            skippingNow = false
        }
        assertEquals(expected.primitives, actual.primitives, "the skipping host drew something else")
        assertEquals(expected.graphicsLayers, actual.graphicsLayers, "the skipping host layered something else")
        return actual
    }

    context(_: Composer)
    private fun movingNode(modifier: Modifier) {
        if (skippingNow) compositions++
        Box(Modifier.size(200.dp)) {
            Layout(CountingNode, modifier, moving) {
                Layout(CountingNode, Modifier.size(10.dp).background(Color(1f, 0f, 0f)), inner)
            }
        }
    }

    /** Two warm frames, then [frames] in which [step] moves the node; [check] sees each frame's output. */
    private fun movingFrames(
        frames: Int,
        step: (Int) -> Unit,
        check: (Int, FrameOutput) -> Unit = { _, _ -> },
        content: context(Composer) () -> Unit,
    ) {
        frame(content = content)
        frame(content = content)
        val before = listOf(compositions, moving.measures, inner.measures, skipping.skippedMeasures)
        for (i in 1..frames) {
            step(i)
            // Checked at once: a host reuses its output lists from frame to frame.
            check(i, frame(content = content))
        }
        assertEquals(before[0], compositions, "moving the node recomposed it")
        assertEquals(before[1], moving.measures, "moving the node measured it")
        assertEquals(before[2], inner.measures, "moving the node measured its subtree")
        assertEquals(before[3] + frames, skipping.skippedMeasures, "every moving frame should have skipped measuring")
    }

    @Test
    fun anOffsetLambdaMovesItsNodeEachFrameWithoutComposingOrMeasuringIt() {
        var x = 0
        movingFrames(
            10,
            step = { x = it * 3 },
            check = { i, output ->
                val quad = output.primitives.filterIsInstance<UiDrawPrimitive.Quad>().single()
                assertEquals(i * 3f, quad.x, "frame $i placed the node a frame late or not at all")
            },
            content = { movingNode(Modifier.offset { IntOffset(x, 0) }) },
        )
    }

    @Test
    fun aStateReadOnlyWhilePlacingDoesNotMakeTheFrameComposeOrMeasure() {
        val x = mutableStateOf(0)
        movingFrames(10, step = { x.value = it * 2 }, content = { movingNode(Modifier.offset { IntOffset(x.value, 0) }) })
    }

    @Test
    fun aLayerTranslationReadWhileDrawingMovesWithoutComposingOrMeasuring() {
        var dx = 0f
        movingFrames(10, step = { dx = it * 1.5f }, content = { movingNode(Modifier.graphicsLayer { translationX = dx }) })
    }

    @Test
    fun aStateReadWhileMeasuringMakesTheNextFrameMeasure() {
        val width = mutableStateOf(20)
        val sized = Counting { width.value }
        val content: context(Composer)
        () -> Unit = {
            Layout(CountingNode, Modifier.background(Color(0f, 1f, 0f)), sized) { Box(Modifier.size(5.dp)) }
        }
        frame(content = content)
        frame(content = content)
        val measured = sized.measures

        width.value = 40
        val output = frame(content = content)

        assertEquals(measured + 1, sized.measures, "a write the last measure read must measure again")
        assertEquals(40f, output.primitives.filterIsInstance<UiDrawPrimitive.Quad>().first().w)
        frame(content = content)
        assertEquals(measured + 1, sized.measures, "measured again with nothing changed")
    }

    @Test
    fun aSubcompositionThatWouldComposeAgainMakesTheFrameMeasure() {
        val label = mutableStateOf("one")
        val content: context(Composer)
        () -> Unit = {
            BoxWithConstraints { Text(label.value, Modifier.background(Color(0f, 0f, 1f))) }
        }
        frame(content = content)
        frame(content = content)

        label.value = "a longer label"
        frame(content = content)

        assertTrue(skipping.skippedCompositions >= 2, "the outer composition should have skipped")
    }

    @Test
    fun aResizeMeasures() {
        val content: context(Composer)
        () -> Unit = { movingNode(Modifier) }
        frame(content = content)
        val measured = moving.measures

        frame(FrameInput(400, 300), content)

        assertEquals(measured + 1, moving.measures)
    }

    @Test
    fun measuringIsNotSkippedUnlessAskedFor() {
        skipping.skipUnchangedMeasure = false
        val content: context(Composer)
        () -> Unit = { movingNode(Modifier) }
        repeat(4) { frame(content = content) }

        assertEquals(0, skipping.skippedMeasures)
        assertEquals(4, inner.measures, "each frame measures")
    }
}
