/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** [CanvasJoystick]: its modes, dead zone, response, directions, snap-back, sprint, idle fade, and one pointer per stick. */
class CanvasJoystickTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind; offsetX = 0f; offsetY = 0f }.apply(configure).also { items += CanvasItem(items.size + 1, it) }

    private fun frame(vararg touches: PointerFrame) {
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) { CanvasOverlay(items, showTouchControls = true) }
    }

    private fun down(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = true, pressed = true)

    private fun at(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = true)

    private fun up(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = false, released = true)

    private fun stick(settings: CanvasJoystick? = null, size: Float = 100f): CanvasElement =
        element(CanvasElementKind.Joystick) { width = size; height = size; joystick = settings }

    @Test
    fun aDeadZoneReportsNothingNearTheCentreAndStretchesTheRestBackToFull() {
        val settings = CanvasJoystick(deadZone = 0.2f)

        assertEquals(0f, settings.deflection(0f, -10f, 50f).second, "10 of 50 is inside a 0.2 dead zone")
        assertEquals(-0.5f, settings.deflection(0f, -30f, 50f).second, TOLERANCE, "0.6 out is half way from 0.2 to 1")
        assertEquals(-1f, settings.deflection(0f, -50f, 50f).second, TOLERANCE, "the edge is still full")
    }

    @Test
    fun aQuadraticResponseIsSlowNearTheCentreAndACurveTakesItsPlace() {
        assertEquals(0.25f, CanvasJoystick(response = CanvasStickResponse.Quadratic).deflection(25f, 0f, 50f).first, TOLERANCE)
        val curve = CanvasCurve(listOf(CanvasCurvePoint(0f, 0f), CanvasCurvePoint(0.5f, 0.8f), CanvasCurvePoint(1f, 1f)))
        assertEquals(0.8f, CanvasJoystick(curve = curve).deflection(25f, 0f, 50f).first, TOLERANCE)
    }

    @Test
    fun eightWaySnappingTurnsToTheNearestDiagonal() {
        val (x, y) = CanvasJoystick(directions = CanvasStickDirections.Eight).deflection(50f, -30f, 50f)
        // 50 right and 30 up is about 31 degrees, nearer the up-right diagonal at 45 than the right at 0.
        assertEquals(x, -y, TOLERANCE, "a diagonal has equal parts")
        assertEquals(1f, sqrt(x * x + y * y), TOLERANCE, "snapping keeps the deflection's length")
        assertEquals(1f, CanvasJoystick(directions = CanvasStickDirections.Four).deflection(50f, -20f, 50f).first, TOLERANCE)
    }

    @Test
    fun aFixedStickDeflectsFromItsCentreWhereverTheThumbLands() {
        val pad = stick()
        frame()

        frame(down(1, 75, 50))
        assertEquals(0.5f, pad.stickX, TOLERANCE, "a touch 25 right of the centre of a 50 reach is half right")
        frame(at(1, 75, -200))
        assertEquals(-1f, pad.stickY, 0.1f, "dragged far up, it stops at the pad's edge")
        frame(up(1, 75, -200))
        assertEquals(0f, pad.stickX)
        assertEquals(0f, pad.stickY, "letting go centres it")
    }

    @Test
    fun aFloatingStickAppearsWhereTheThumbLandsAndDeflectsFromThere() {
        val zone = stick(CanvasJoystick(mode = CanvasJoystickMode.Floating, radius = 40f), size = 300f)
        frame()

        frame(down(1, 200, 150))
        assertEquals(0f, zone.stickX, "landing is no deflection")
        assertEquals(200f, zone.stick.centerX, "the pad is centred under the thumb")
        frame(at(1, 220, 150))
        assertEquals(0.5f, zone.stickX, TOLERANCE, "20 right of where it landed, of a 40 reach")
    }

    @Test
    fun aDynamicStickIsDraggedAlongPastItsReach() {
        val zone = stick(CanvasJoystick(mode = CanvasJoystickMode.Dynamic, radius = 40f), size = 300f)
        frame()

        frame(down(1, 100, 150))
        frame(at(1, 200, 150))
        assertEquals(1f, zone.stickX, TOLERANCE)
        assertEquals(160f, zone.stick.centerX, TOLERANCE, "the pad follows the thumb to stay a reach behind it")
        frame(at(1, 150, 150))
        assertEquals(-0.25f, zone.stickX, TOLERANCE, "coming back, it deflects from where the pad was dragged to")
    }

    @Test
    fun withoutSnapBackLettingGoHoldsTheLastDeflection() {
        val pad = stick(CanvasJoystick(snapBack = false))
        frame()

        frame(down(1, 50, 25))
        frame(up(1, 50, 25))

        assertEquals(-0.5f, pad.stickY, TOLERANCE)
    }

    @Test
    fun pastTheSprintThresholdTheStickSprintsAndSaysWhenItStarted() {
        val pad = stick(CanvasJoystick(sprintThreshold = 0.8f, sprintAction = "run"))
        frame()

        frame(down(1, 50, 20))
        assertFalse(pad.sprinting, "30 of 50 is not yet 0.8 out")
        frame(at(1, 50, 5))
        assertTrue(pad.sprinting)
        assertTrue(pad.consumeSprintStart())
        assertFalse(pad.consumeSprintStart(), "the start is reported once")
        frame(up(1, 50, 5))
        assertFalse(pad.sprinting, "letting go stops sprinting")
    }

    @Test
    fun anIdleStickFadesAfterItsDelayAndATouchShowsItAtOnce() {
        val time = TestTimeSource()
        val pad = stick(CanvasJoystick(idleAlpha = 0.2f, idleDelay = 1f, fadeSeconds = 1f)).apply { clock = time }
        val settings = pad.joystick!!
        frame()

        assertEquals(1f, pad.stick.alpha(settings), "whole until its delay has passed")
        time += 1500.milliseconds
        assertEquals(0.6f, pad.stick.alpha(settings), TOLERANCE, "half way through the fade")
        time += 1.seconds()
        assertEquals(0.2f, pad.stick.alpha(settings), TOLERANCE)
        frame(down(1, 50, 50))
        assertEquals(1f, pad.stick.alpha(settings), "a touch shows it")
    }

    @Test
    fun aStickKeepsItsOwnPointerWhileAnotherPressesAButton() {
        val pad = stick()
        val button = element(CanvasElementKind.Button) { anchor = CanvasAnchor.TopRight; width = 80f; height = 80f }
        frame()

        frame(down(1, 50, 50))
        frame(at(1, 75, 50), down(2, WIDTH - 40, 40))
        frame(at(1, 75, 50), up(2, WIDTH - 40, 40))
        assertTrue(button.consumePress(), "the second thumb's tap reached the button")
        assertEquals(0.5f, pad.stickX, TOLERANCE, "and the stick kept following the first")

        frame(at(1, 75, 50), down(3, 30, 50))
        frame(at(1, 75, 50), at(3, 0, 50))
        assertEquals(0.5f, pad.stickX, TOLERANCE, "a second thumb on the stick does not take it")
        frame(up(1, 75, 50), up(3, 0, 50))
        assertEquals(0f, pad.stickX)
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasJoystick().problems().isEmpty())
        assertTrue(CanvasJoystick(deadZone = 1f).problems().any { "deadZone" in it })
        assertTrue(CanvasJoystick(sprintAction = "run").problems().any { "sprintThreshold" in it })
        assertTrue(CanvasJoystick(deadZone = 0.5f, sprintThreshold = 0.4f).problems().any { "past deadZone" in it })
    }

    private fun Int.seconds() = (this * 1000).milliseconds

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val TOLERANCE = 0.01f
    }
}
