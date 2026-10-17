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
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [CanvasAim]: dragging an action button to aim, firing with the aim, cancelling, and locking onto a target. */
class CanvasAimTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()

    /** An 80 dp button in the bottom-right corner of a 400 by 300 canvas: its centre is (360, 260). */
    private fun aimed(aim: CanvasAim): CanvasElement = CanvasElement().apply {
        kind = CanvasElementKind.Button; anchor = CanvasAnchor.BottomRight; offsetX = 0f; offsetY = 0f
        width = 80f; height = 80f; button = CanvasButton(aim = aim)
    }.also { items += CanvasItem(items.size + 1, it) }

    private fun frame(vararg touches: PointerFrame): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) { CanvasOverlay(items) }

    private fun down(x: Int = CX, y: Int = CY) = PointerFrame(1, x, y, down = true, pressed = true)

    private fun at(x: Int, y: Int) = PointerFrame(1, x, y, down = true)

    private fun up(x: Int, y: Int) = PointerFrame(1, x, y, down = false, released = true)

    @Test
    fun aDirectionIsFullLengthPastTheDeadZoneAndAGroundTargetIsAsFarAsTheDrag() {
        val direction = CanvasAim(radius = 100f, deadZone = 0.2f)
        assertEquals(Pair(0f, 0f), direction.aim(10f, 0f), "inside the dead zone is no aim")
        assertEquals(Pair(0f, -1f), direction.aim(0f, -30f))

        val (x, y) = CanvasAim(mode = CanvasAimMode.Ground, radius = 100f).aim(30f, -40f)
        assertEquals(0.5f, sqrt(x * x + y * y), TOLERANCE, "50 of a 100 reach is half way out")
        assertEquals(1f, CanvasAim(mode = CanvasAimMode.Ground, radius = 100f).aim(300f, 0f).first, TOLERANCE, "held at the reach")
    }

    @Test
    fun draggingAimsAndLettingGoAnywhereFiresWithThatAim() {
        val button = aimed(CanvasAim(radius = 100f))
        frame()

        val idle = frame()
        assertNull(idle.semantics.findTag("canvas-aim-range"), "nothing shows until it is pressed")
        frame(down())
        frame(at(CX - 200, CY))
        assertTrue(button.aiming)
        assertEquals(-1f, button.aimX, TOLERANCE)
        val shown = frame(at(CX - 200, CY))
        assertNotNull(shown.semantics.findTag("canvas-aim-range"))
        assertNotNull(shown.semantics.findTag("canvas-aim-cursor"))
        frame(up(CX - 200, CY))

        assertTrue(button.consumePress(), "let go far off the button, it still fires")
        assertFalse(button.aiming)
        assertEquals(-1f, button.aimX, TOLERANCE, "the aim it fired with stays until the next press")
    }

    @Test
    fun aQuickTapFiresWithNoAim() {
        val button = aimed(CanvasAim())
        frame()

        frame(down())
        frame(up(CX + 3, CY))

        assertTrue(button.consumePress())
        assertEquals(0f, button.aimX)
        assertEquals(0f, button.aimY)
    }

    @Test
    fun lettingGoOverTheCancelZoneCancelsTheShot() {
        // The zone is 72 dp square, 24 dp down from the top's centre: x 164 to 236, y 24 to 96.
        val button = aimed(CanvasAim(cancel = CanvasZone()))
        frame()

        frame(down())
        val placed = frame(at(CX - 100, CY - 100))
        assertNotNull(placed.semantics.findTag("canvas-aim-cancel"), "the cancel zone shows while aiming")
        assertFalse(button.aimCancelling)
        frame(at(200, 60))
        assertTrue(button.aimCancelling)
        frame(up(200, 60))

        assertFalse(button.consumePress(), "a cancelled shot does not fire")
        assertTrue(button.consumeAimCancel())
        assertTrue(button.consumeRelease(), "it is still a release")
    }

    @Test
    fun aLockOntoATargetTakesThePlaceOfTheRawAim() {
        val button = aimed(CanvasAim(lockTarget = true)).apply {
            aimLock = CanvasAimLock { _, x, _ -> if (x < 0f) CanvasAimTarget("left-target", -0.6f, -0.8f) else null }
        }
        frame()

        frame(down())
        frame(at(CX - 100, CY))
        assertEquals("left-target", button.aimTarget)
        assertEquals(-0.8f, button.aimY, TOLERANCE)
        frame(at(CX, CY - 100))
        assertNull(button.aimTarget, "nothing to lock onto keeps the raw aim")
        assertEquals(-1f, button.aimY, TOLERANCE)
        frame(up(CX, CY - 100))
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasAim().problems().isEmpty())
        assertEquals(listOf("aim.deadZone must be at least 0 and below 1"), CanvasButton(aim = CanvasAim(deadZone = 1f)).problems())
        assertEquals(listOf("cancel.size must not be negative"), CanvasAim(cancel = CanvasZone(width = -1f)).problems())
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val CX = 360
        const val CY = 260
        const val TOLERANCE = 0.01f
    }
}
