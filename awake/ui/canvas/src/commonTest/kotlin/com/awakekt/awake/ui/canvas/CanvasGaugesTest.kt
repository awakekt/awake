/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A Stepper's presses, holds, keys and binding, and a Bar's trailing fill. */
class CanvasGaugesTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()
    private var fields = emptyMap<String, String>()
    private val resolver = object : CanvasResolver {
        override fun field(node: String, path: String): String? = fields[path]
    }

    private fun element(kind: CanvasElementKind, configure: CanvasElement.() -> Unit = {}): CanvasElement =
        CanvasElement().apply {
            this.kind = kind; offsetX = 0f; offsetY = 0f; width = 120f; height = 30f
        }.apply(configure).also { items += CanvasItem(items.size + 1, it) }

    private fun frame(vararg touches: PointerFrame, delta: Float = 1f / 60f, keys: List<KeyEvent> = emptyList()): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList(), deltaSeconds = delta, keyEvents = keys)) {
            CanvasOverlay(items, resolver = resolver)
        }

    private fun down(x: Int) = PointerFrame(1, x, 15, down = true, pressed = true)

    private fun at(x: Int) = PointerFrame(1, x, 15, down = true)

    private fun up(x: Int) = PointerFrame(1, x, 15, down = false, released = true)

    @Test
    fun aPressStepsOnceTheMomentItLands() {
        val stepper = element(CanvasElementKind.Stepper) { stepper = CanvasStepper(value = 4f, step = 2f) }
        frame()

        frame(down(PLUS))
        frame(at(PLUS))
        frame(up(PLUS))

        assertEquals(6f, stepper.number)
        assertTrue(stepper.consumeChange())
        assertFalse(stepper.consumeChange(), "a change is reported once")

        frame(down(MINUS))
        frame(up(MINUS))
        assertEquals(4f, stepper.number)
    }

    @Test
    fun holdingAButtonRepeatsAfterTheDelayAndStopsAtTheBound() {
        val stepper = element(CanvasElementKind.Stepper) { stepper = CanvasStepper(value = 0f, max = 10f, repeatDelay = 0.4f, repeatInterval = 0.1f) }
        frame()

        frame(down(PLUS))
        repeat(3) { frame(at(PLUS), delta = 0.1f) }
        assertEquals(1f, stepper.number, "nothing repeats before the delay")

        repeat(3) { frame(at(PLUS), delta = 0.1f) }
        assertEquals(4f, stepper.number, "then one more every interval")

        repeat(20) { frame(at(PLUS), delta = 0.1f) }
        frame(up(PLUS))
        assertEquals(10f, stepper.number, "held at the bound")
    }

    @Test
    fun keysAndAControllerStepIt() {
        val stepper = element(CanvasElementKind.Stepper) { stepper = CanvasStepper(value = 50f) }
        frame()
        // A click on the number gives the stepper the keyboard.
        frame(down(MIDDLE))
        frame(up(MIDDLE))

        frame(keys = listOf(KeyEvent(Key.ArrowUp, KeyEventType.Down)))
        assertEquals(51f, stepper.number)
        frame(keys = listOf(KeyEvent(Key.PageDown, KeyEventType.Down)))
        assertEquals(41f, stepper.number)
        frame(keys = listOf(KeyEvent(Key.End, KeyEventType.Down)))
        assertEquals(100f, stepper.number)

        assertTrue(stepper.stepBy(GamepadButton.DpadDown))
        assertEquals(99f, stepper.number)
        assertFalse(stepper.stepBy(GamepadButton.South), "a face button does not step")
    }

    @Test
    fun aBoundStepperShowsTheFieldAndReportsThePlayersStep() {
        fields = mapOf("cart.count" to "3")
        val stepper = element(CanvasElementKind.Stepper) { bind = CanvasBinding("Shop", value = "cart.count") }

        frame()
        assertEquals(3f, stepper.number)

        frame(down(PLUS))
        assertTrue(stepper.consumeChange())
        assertEquals(4f, stepper.number, "the step lands on the field's value")

        fields = mapOf("cart.count" to "4")
        frame(up(PLUS))
        assertEquals(4f, stepper.number, "and stays once the game writes it back")
        assertFalse(stepper.consumeChange(), "the field changing is not the player's step")
    }

    @Test
    fun aTrailHoldsThenDrainsToTheValue() {
        val bar = element(CanvasElementKind.Bar) {
            value = 1f
            this.bar = CanvasBar(trailColor = "#FF0000", trailDelay = 0.5f, trailSpeed = 1f)
        }
        frame()

        // A host clock never steps more than 0.1 s a frame.
        bar.value = 0.4f
        repeat(5) { frame(delta = 0.1f) }
        assertEquals(1f, bar.trail.value, 0.001f, "held")
        repeat(2) { frame(delta = 0.1f) }
        assertEquals(0.8f, bar.trail.value, 0.001f, "the 0.2 s past the hold drained 0.2")
        repeat(10) { frame(delta = 0.1f) }
        assertEquals(0.4f, bar.trail.value, 0.001f, "it stops at the value")

        bar.value = 0.9f
        frame()
        assertEquals(0.9f, bar.trail.value, 0.001f, "a rise jumps the trail up with it")
    }

    @Test
    fun aBarDoesNotTrailInFromFullTheFirstTimeItDraws() {
        val bar = element(CanvasElementKind.Bar) {
            value = 0.3f
            this.bar = CanvasBar(trailColor = "#FF0000")
        }

        frame()

        assertEquals(0.3f, bar.trail.value, 0.001f)
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasBar().problems().isEmpty() && CanvasSweep().problems().isEmpty() && CanvasStepper().problems().isEmpty())
        assertEquals(listOf("trailColor \"red\" must be #RRGGBB or #RRGGBBAA", "trailSpeed must be above 0"), CanvasBar(trailColor = "red", trailSpeed = 0f).problems())
        assertEquals(listOf("thickness must not be negative"), CanvasSweep(thickness = -1f).problems())
        assertEquals(
            listOf("max must be at least min", "value must be between min and max", "step must be above 0"),
            CanvasStepper(value = 5f, min = 10f, max = 0f, step = 0f).problems(),
        )
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300

        /** The middle of the minus button, the 30-wide square at the stepper's left. */
        const val MINUS = 15

        /** The number between the buttons. */
        const val MIDDLE = 60

        /** The middle of the plus button, at its right. */
        const val PLUS = 105
    }
}
