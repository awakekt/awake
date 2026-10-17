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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** [CanvasButton]: an action button's press, hold, release and long press, its cooldown and charges, and its disabled state. */
class CanvasActionButtonTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()
    private val time = TestTimeSource()
    private var fields = emptyMap<String, String>()
    private val resolver = object : CanvasResolver {
        override fun field(node: String, path: String): String? = fields[path]
    }

    private fun element(kind: CanvasElementKind = CanvasElementKind.Button, configure: CanvasElement.() -> Unit = {}): CanvasElement =
        CanvasElement().apply {
            this.kind = kind; offsetX = 0f; offsetY = 0f; width = 80f; height = 80f; clock = time
        }.apply(configure).also { items += CanvasItem(items.size + 1, it) }

    private fun action(settings: CanvasButton = CanvasButton(), configure: CanvasElement.() -> Unit = {}) =
        element { button = settings; configure() }

    private fun frame(vararg touches: PointerFrame): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) { CanvasOverlay(items, resolver = resolver) }

    private fun down(id: Long = 1, x: Int = 40, y: Int = 40) = PointerFrame(id, x, y, down = true, pressed = true)

    private fun at(id: Long = 1, x: Int = 40, y: Int = 40) = PointerFrame(id, x, y, down = true)

    private fun up(id: Long = 1, x: Int = 40, y: Int = 40) = PointerFrame(id, x, y, down = false, released = true)

    @Test
    fun aTapFiresOnReleaseOverTheButtonAndSlidingOffCancelsItButNotTheRelease() {
        val button = action()
        frame()

        frame(down())
        assertTrue(button.consumeDown(), "going down is reported at once")
        assertFalse(button.consumePress(), "a tap waits for the release")
        assertTrue(button.isHeld)
        frame(up())
        assertTrue(button.consumePress())
        assertTrue(button.consumeRelease())

        frame(down())
        frame(at(x = 300))
        frame(up(x = 300))
        assertFalse(button.consumePress(), "let go off the button is no tap")
        assertTrue(button.consumeRelease(), "but it is still a release")
    }

    @Test
    fun aDownTriggerFiresTheMomentItIsPressed() {
        val button = action(CanvasButton(trigger = CanvasButtonTrigger.Down))
        frame()

        frame(down())
        assertTrue(button.consumePress())
        frame(up())
        assertFalse(button.consumePress(), "and not again on release")
    }

    @Test
    fun aLongPressIsReportedOnceAndWithALongPressActionIsNotAlsoATap() {
        val plain = action()
        val held = action(CanvasButton(longPressSeconds = 0.4f, longPressAction = "inspect")) { anchor = CanvasAnchor.TopRight }
        frame()

        frame(down(1), down(2, x = WIDTH - 40))
        time += 300.milliseconds
        frame(at(1), at(2, x = WIDTH - 40))
        assertFalse(held.consumeLongPress(), "0.3 s is not yet long")
        assertEquals(0.3f, held.heldSeconds, 0.01f)
        time += 200.milliseconds
        frame(at(1), at(2, x = WIDTH - 40))
        assertTrue(held.consumeLongPress())
        assertTrue(plain.consumeLongPress(), "a button with no long-press action still reports one")
        frame(up(1), up(2, x = WIDTH - 40))

        assertFalse(held.consumePress(), "the long press took the tap")
        assertTrue(plain.consumePress(), "with nothing to do on a long press, letting go still taps")
        assertFalse(held.consumeLongPress(), "reported once")
    }

    @Test
    fun aDisabledButtonReportsNothingAndKeepsThePressFromWhatIsUnder() {
        val under = element { order = 0 }
        val button = action { order = 1; enabled = false }
        frame()

        frame(down())
        frame(up())

        assertFalse(button.consumeDown())
        assertFalse(button.consumePress())
        assertFalse(button.consumeRelease())
        assertFalse(under.consumePress(), "the press did not fall through")
    }

    @Test
    fun aCoolingButtonTakesNoPressUntilItIsReadyOrHasACharge() {
        val button = action { cooldown = 0.5f }
        frame()
        assertTrue(button.coolingDown)
        assertFalse(button.usable)

        frame(down())
        frame(up())
        assertFalse(button.consumePress(), "held back while cooling")

        button.charges = 2
        assertTrue(button.usable, "a charge left can be spent while the next recharges")
        assertTrue(button.consumeReady(), "having a charge again is ready")
        button.charges = 0
        button.cooldown = 0f
        assertTrue(button.consumeReady(), "and so is the cooldown running out")
        assertFalse(button.consumeReady(), "once")
    }

    @Test
    fun aButtonThatDoesNotBlockWhileCoolingTakesThePressAnyway() {
        val button = action(CanvasButton(blockWhileCooling = false)) { cooldown = 0.5f }
        frame()

        frame(down())
        frame(up())

        assertTrue(button.consumePress())
    }

    @Test
    fun cooldownChargesAndEnabledAreReadFromBoundFieldsAndDrawn() {
        fields = mapOf("dash.left" to "1.5", "dash.seconds" to "3", "dash.charges" to "2", "dash.ready" to "true")
        val button = action {
            bind = CanvasBinding("Player", cooldown = "dash.left", cooldownMax = "dash.seconds", charges = "dash.charges", enabled = "dash.ready")
        }

        val out = frame()

        assertEquals(0.5f, button.cooldown, 0.001f)
        assertEquals(2, button.charges)
        assertTrue(button.enabled)
        assertNotNull(out.semantics.findTag("canvas-charges"), "charges show in a badge")

        fields = fields + ("dash.ready" to "false") + ("dash.charges" to "-1")
        assertNull(frame().semantics.findTag("canvas-charges"), "no badge below 0")
        assertFalse(button.enabled)
    }

    @Test
    fun twoThumbsHoldTwoButtonsAtOnce() {
        val left = action()
        val right = action { anchor = CanvasAnchor.TopRight }
        frame()

        frame(down(1), down(2, x = WIDTH - 40))
        frame(at(1), up(2, x = WIDTH - 40))

        assertTrue(left.isHeld, "letting go of one button does not let go of the other")
        assertTrue(right.consumePress())
        assertFalse(left.consumePress())
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasButton().problems().isEmpty())
        assertEquals(listOf("longPressSeconds must be above 0"), CanvasButton(longPressSeconds = 0f).problems())
        assertTrue(CanvasBinding("Player", cooldown = "seconds").problems().any { "cooldown" in it })
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
    }
}
