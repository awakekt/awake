/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A canvas sized from its placement (#1512) lays its content out at the box's size on every frame:
 * the first, inside a box smaller than the viewport, and the frame a window is resized. Element
 * bounds here are the layout pointer input is hit-tested against.
 */
class CanvasSizingTest {
    // Scaled with the box's width: 1x at 200 wide, 2x at 400.
    private val scaler = CanvasScaler(referenceWidth = 200f, referenceHeight = 100f, match = 0f)

    private val corner = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        anchor = CanvasAnchor.BottomRight
        offsetX = 0f
        offsetY = 0f
        width = 20f
        height = 20f
    }

    private val follower = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        width = 10f
        height = 10f
        follow = "beacon"
    }

    /** A world point projected at x 300: off a 200-wide box, on a 400-wide one. */
    private val beacon = object : CanvasResolver {
        override fun project(target: String, offset: Vec3f): CanvasProjection? =
            if (target == "beacon") CanvasProjection(300f, 50f, distance = 5f) else null
    }

    private val items = listOf(CanvasItem(CORNER, corner), CanvasItem(FOLLOWER, follower))

    private fun ComposeHost.canvasFrame(width: Int, height: Int): FrameOutput =
        frame(FrameInput(width, height)) { CanvasOverlay(items, resolver = beacon, scaler = scaler) }

    @Test
    fun theFrameOfAResizeAndTheNextAreLaidOutAtTheNewSize() {
        listOf(false, true).forEach { skips ->
            val host = ComposeHost().apply {
                skipUnchangedComposition = skips
                skipUnchangedMeasure = skips
                retainUnchangedDraw = skips
            }
            repeat(3) { host.canvasFrame(200, 100) }
            val still = host.canvasFrame(200, 100)
            assertEquals(Bounds(180, 80, 20, 20), still.bounds(CORNER), "1x at 200x100, skips=$skips")
            assertNull(still.node(FOLLOWER), "the beacon is off a 200-wide box, skips=$skips")

            val resized = host.canvasFrame(400, 200)
            assertEquals(Bounds(360, 160, 40, 40), resized.bounds(CORNER), "2x on the resize frame, skips=$skips")
            assertNotNull(resized.node(FOLLOWER), "the beacon shows on the resize frame, skips=$skips")
            val after = host.canvasFrame(400, 200)
            assertEquals(Bounds(360, 160, 40, 40), after.bounds(CORNER), "2x the frame after, skips=$skips")
            assertEquals(resized.bounds(FOLLOWER), after.bounds(FOLLOWER), "the beacon holds, skips=$skips")
        }
    }

    @Test
    fun aCanvasSmallerThanTheViewportDrawsItsFirstFrameAtItsOwnSize() {
        val out = ComposeHost().frame(FrameInput(640, 360)) {
            Box(Modifier.size(200.dp, 100.dp)) { CanvasOverlay(items, resolver = beacon, scaler = scaler) }
        }
        assertEquals(Bounds(180, 80, 20, 20), out.bounds(CORNER), "first frame in a 200x100 box, not the 640x360 viewport")
        assertNull(out.node(FOLLOWER), "the beacon is off the 200-wide box on the first frame")
    }

    @Test
    fun twoCanvasesSideBySideEachDrawTheirFirstFrameAtTheirOwnSize() {
        val left = listOf(CanvasItem(CORNER, corner))
        val right = listOf(
            CanvasItem(
                OTHER_CORNER,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel
                    anchor = CanvasAnchor.BottomRight
                    offsetX = 0f
                    offsetY = 0f
                    width = 20f
                    height = 20f
                },
            ),
        )
        val out = ComposeHost().frame(FrameInput(400, 100)) {
            Row {
                Box(Modifier.size(200.dp, 100.dp)) { CanvasOverlay(left, scaler = scaler) }
                Box(Modifier.size(200.dp, 100.dp)) { CanvasOverlay(right, scaler = scaler) }
            }
        }
        assertEquals(Bounds(180, 80, 20, 20), out.bounds(CORNER), "the left canvas at 1x in its own box")
        assertEquals(Bounds(380, 80, 20, 20), out.bounds(OTHER_CORNER), "the right canvas at 1x in its own box")
    }

    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun FrameOutput.node(id: Int): SemanticsNode? = semantics.findTag("canvas-element-$id")

    private fun FrameOutput.bounds(id: Int): Bounds = checkNotNull(node(id)) { "canvas-element-$id was not laid out" }
        .let { Bounds(it.x, it.y, it.width, it.height) }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val CORNER = 1
        const val FOLLOWER = 2
        const val OTHER_CORNER = 3
    }
}
