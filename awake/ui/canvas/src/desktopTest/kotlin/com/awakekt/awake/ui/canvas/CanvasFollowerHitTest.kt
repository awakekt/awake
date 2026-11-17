/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A follower placed while placing takes the pointer where it is drawn (#1512): one whose node moved
 * without the canvas composing again is pressed at its new place and not its old one, and one the
 * plan culls takes no pointer at all.
 */
class CanvasFollowerHitTest {
    private var pointX = 100f

    private val resolver = object : CanvasResolver {
        override fun project(target: String, offset: Vec3f): CanvasProjection = CanvasProjection(pointX, 100f, distance = 5f)
    }

    private val button = CanvasElement().apply {
        kind = CanvasElementKind.Button
        follow = "node"
        width = 40f
        height = 20f
    }

    private val host = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = true
    }

    private fun frame(x: Int = -1, y: Int = -1, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            CanvasOverlay(listOf(CanvasItem(1, button)), resolver = resolver)
        }

    /** Clicks at ([x], [y]) and says whether the button took it. */
    private fun clicked(x: Int, y: Int): Boolean {
        button.consumePress()
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        frame(x, y, released = true)
        return button.consumePress()
    }

    private fun centreOf(out: FrameOutput): Pair<Int, Int> {
        val node = assertNotNull(out.semantics.findTag("canvas-element-1"), "the follower is laid out")
        return (node.x + node.width / 2) to (node.y + node.height / 2)
    }

    @Test
    fun aFollowerMovedWithoutComposingIsPressedWhereItNowIs() {
        repeat(SETTLE) { frame() }
        val (oldX, oldY) = centreOf(frame())
        pointX = 300f
        val moved = frame()
        assertFalse(host.frameCounts.composed, "the move composed: ${host.frameCounts}")
        val (newX, newY) = centreOf(moved)
        assertTrue(newX > oldX, "the follower moved: $oldX to $newX")
        assertTrue(clicked(newX, newY), "pressed where it now is")
        assertFalse(clicked(oldX, oldY), "and not where it was")
    }

    @Test
    fun aCulledFollowerTakesNoPointer() {
        repeat(SETTLE) { frame() }
        val (x, y) = centreOf(frame())
        assertTrue(clicked(x, y), "pressed while drawn")
        pointX = -1000f
        val culled = frame()
        assertNull(culled.semantics.findTag("canvas-element-1"), "culled off the canvas")
        assertFalse(clicked(x, y), "a culled follower took the pointer")
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val SETTLE = 10
    }
}
