/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A LookArea's swipe and pinch, beside a stick on another thumb, and a [CanvasCluster]'s fan of buttons. */
class CanvasLookAndClusterTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, parent: CanvasElement? = null, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind; offsetX = 0f; offsetY = 0f }.apply(configure)
            .also { items += CanvasItem(items.size + 1, it, parent) }

    /** The right half of a 400 by 300 canvas. */
    private fun lookArea(settings: CanvasLook? = null) = element(CanvasElementKind.LookArea) {
        anchor = CanvasAnchor.TopRight; width = 200f; height = 300f; look = settings
    }

    private fun frame(vararg touches: PointerFrame): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) { CanvasOverlay(items) }

    private fun down(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = true, pressed = true)

    private fun at(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = true)

    private fun up(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = false, released = true)

    @Test
    fun aSwipeLooksBySensitivityAndInversionAndIsReadOnce() {
        val area = lookArea(CanvasLook(sensitivity = 2f, invertY = true))
        frame()

        frame(down(1, 300, 150))
        frame(at(1, 320, 140))
        frame(at(1, 330, 130))
        val look = area.consumeLook()

        assertEquals(60f, look.x, TOLERANCE, "30 dp right, twice over")
        assertEquals(40f, look.y, TOLERANCE, "20 dp up, inverted to down")
        assertEquals(0f, area.consumeLook().x, "read once")
        frame(up(1, 330, 130))
    }

    @Test
    fun twoThumbsPinchToZoomAndDoNotLook() {
        val area = lookArea()
        frame()

        frame(down(1, 250, 150), down(2, 350, 150))
        frame(at(1, 225, 150), at(2, 375, 150))

        assertEquals(1.5f, area.consumeZoom(), TOLERANCE, "spread from 100 to 150 dp apart")
        assertEquals(0f, area.consumeLook().x, "a pinch does not look")
        assertEquals(1f, area.consumeZoom(), "read once")
        frame(up(1, 225, 150), up(2, 375, 150))
    }

    @Test
    fun aStickAndALookAreaEachKeepTheirOwnThumb() {
        val area = lookArea()
        val stick = element(CanvasElementKind.Joystick) { anchor = CanvasAnchor.BottomLeft; width = 100f; height = 100f }
        frame()

        frame(down(1, 50, 250), down(2, 300, 150))
        frame(at(1, 75, 250), at(2, 340, 150))

        assertEquals(0.5f, stick.stickX, TOLERANCE)
        assertEquals(40f, area.consumeLook().x, TOLERANCE)
        assertEquals(1f, area.consumeZoom(), "two thumbs on different controls are no pinch")
        frame(up(1, 75, 250), up(2, 340, 150))
    }

    @Test
    fun aClusterPutsThePrimaryOnItsPivotAndFansTheRestOut() {
        val cluster = CanvasCluster(pivotX = 1f, pivotY = 1f, radius = 100f, startAngle = 180f, spacing = 90f)
        assertEquals(200f, cluster.centerX(0, 200f), "the primary is on the pivot")
        assertEquals(100f, cluster.centerX(1, 200f), TOLERANCE, "the first secondary is 100 left of it")
        assertEquals(200f, cluster.centerX(2, 200f), TOLERANCE)
        assertEquals(100f, cluster.centerY(2, 200f), TOLERANCE, "the next is 100 above it")

        val box = element(CanvasElementKind.Panel) { anchor = CanvasAnchor.BottomRight; width = 200f; height = 200f; this.cluster = cluster }
        element(CanvasElementKind.Button, box) { width = 60f; height = 60f }
        element(CanvasElementKind.Button, box) { width = 40f; height = 40f }
        val out = frame()

        // The box spans x 200 to 400 and y 100 to 300, its pivot at its bottom-right corner (400, 300).
        val primary = assertNotNull(out.semantics.findTag("canvas-element-2"))
        assertEquals(370, primary.x, "centred on the pivot")
        assertEquals(270, primary.y)
        val secondary = assertNotNull(out.semantics.findTag("canvas-element-3"))
        assertEquals(280, secondary.x, "centred 100 left of the pivot")
        assertEquals(280, secondary.y)
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasLook().problems().isEmpty())
        assertEquals(listOf("sensitivity must not be negative"), CanvasLook(sensitivity = -1f).problems())
        assertEquals(listOf("radius must not be negative"), CanvasCluster(radius = -1f).problems())
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val TOLERANCE = 0.01f
    }
}
