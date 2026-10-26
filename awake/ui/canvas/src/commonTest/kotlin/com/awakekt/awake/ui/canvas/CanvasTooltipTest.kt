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
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TIP_TABLES = StringTableSource.of(
    mapOf(
        LocaleId.English to mapOf(
            "tip.bag" to "Your bag",
            "tip.long" to "A tooltip with many more words than one short line can hold at a time",
            "tip.slot" to "Slot {n}",
        ),
    ),
)

/** An element's `tooltip`: shown after a hover delay near the pointer, kept on the canvas, hidden by a press or by leaving. */
class CanvasTooltipTest {
    private val host = ComposeHost()
    private lateinit var strings: Strings
    private val bag = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        offsetX = 20f
        offsetY = 20f
        width = 100f
        height = 60f
        tooltip = "tip.bag"
    }
    private var items = listOf(CanvasItem(0, bag))

    private fun frame(
        pointer: Pair<Int, Int>? = null,
        down: Boolean = false,
        pressed: Boolean = false,
        seconds: Float = 0.1f,
    ): FrameOutput = host.frame(
        FrameInput(
            WIDTH,
            HEIGHT,
            pointerX = pointer?.first ?: FrameInput.UNKNOWN_POINTER,
            pointerY = pointer?.second ?: FrameInput.UNKNOWN_POINTER,
            pointerDown = down,
            pointerPressed = pressed,
            deltaSeconds = seconds,
        ),
    ) { CanvasOverlay(items, strings = strings) }

    private fun FrameOutput.tooltip(): SemanticsNode? = semantics.flatMap { flatten(it) }.firstOrNull { it.testTag == "canvas-tooltip" }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    /** Frames with the pointer at [at] until [seconds] have passed since it came over the element. */
    private fun hover(at: Pair<Int, Int>, seconds: Float): FrameOutput {
        var out = frame(at)
        var elapsed = 0f
        while (elapsed + 0.05f < seconds) {
            out = frame(at)
            elapsed += 0.1f
        }
        return out
    }

    @Test
    fun noTooltipShowsBeforeThePointerHasBeenOverTheElementForTheDelay() = withStrings {
        frame()
        val early = hover(50 to 40, seconds = 0.3f)

        assertNull(early.tooltip(), "shown before the ${CanvasElement.TOOLTIP_DELAY_SECONDS} s delay")
    }

    @Test
    fun theTooltipAppearsAfterTheDelayNearThePointer() = withStrings {
        frame()
        val shown = hover(50 to 40, seconds = 0.7f)

        val bubble = assertNotNull(shown.tooltip(), "no tooltip after the delay")
        assertTrue(bubble.x >= 50, "the bubble starts right of the pointer, at ${bubble.x}")
        assertTrue(bubble.y >= 40, "the bubble starts below the pointer, at ${bubble.y}")
        assertTrue(bubble.x - 50 < 40 && bubble.y - 40 < 40, "the bubble is near the pointer, at ${bubble.x}, ${bubble.y}")
        assertTrue(shown.primitives.any { it is UiDrawPrimitive.Glyph }, "the bubble draws its words")
    }

    @Test
    fun theTooltipShowsTheKeysTextWithItsArguments() = withStrings {
        bag.tooltip = "tip.slot"
        bag.textArgs = mapOf("n" to "3")
        frame()
        val shown = hover(50 to 40, seconds = 0.7f)

        val glyphs = shown.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertEquals("Slot 3".count { it != ' ' }, glyphs.size)
    }

    @Test
    fun theTooltipFollowsThePointerWhileItStaysOverTheElement() = withStrings {
        frame()
        val first = assertNotNull(hover(40 to 30, seconds = 0.7f).tooltip())

        val moved = assertNotNull(frame(70 to 50).tooltip())

        assertEquals(30, moved.x - first.x)
        assertEquals(20, moved.y - first.y)
    }

    @Test
    fun theTooltipStaysOnScreenAtTheCanvasEdges() = withStrings {
        bag.tooltip = "tip.long"
        bag.offsetX = 0f
        bag.offsetY = 0f
        bag.width = WIDTH.toFloat()
        bag.height = HEIGHT.toFloat()
        frame()

        for (corner in listOf(2 to 2, WIDTH - 2 to 2, 2 to HEIGHT - 2, WIDTH - 2 to HEIGHT - 2)) {
            val bubble = assertNotNull(hover(corner, seconds = 0.7f).tooltip(), "no tooltip at $corner")
            assertTrue(bubble.x >= 0 && bubble.y >= 0, "$corner: the bubble is off the top or left, at ${bubble.x}, ${bubble.y}")
            assertTrue(bubble.x + bubble.width <= WIDTH, "$corner: the bubble reaches past the right edge: ${bubble.x}+${bubble.width}")
            assertTrue(bubble.y + bubble.height <= HEIGHT, "$corner: the bubble reaches past the bottom edge: ${bubble.y}+${bubble.height}")
        }
    }

    @Test
    fun theTooltipFlipsToTheLeftAndAboveOfAPointerAtTheBottomRightCorner() = withStrings {
        bag.offsetX = 0f
        bag.offsetY = 0f
        bag.width = WIDTH.toFloat()
        bag.height = HEIGHT.toFloat()
        frame()
        val pointer = WIDTH - 5 to HEIGHT - 5

        val bubble = assertNotNull(hover(pointer, seconds = 0.7f).tooltip())

        assertTrue(bubble.x + bubble.width <= pointer.first, "the bubble is to the pointer's left")
        assertTrue(bubble.y + bubble.height <= pointer.second, "the bubble is above the pointer")
    }

    @Test
    fun aPressHidesTheTooltipUntilThePointerLeavesAndComesBack() = withStrings {
        frame()
        val at = 50 to 40
        assertNotNull(hover(at, seconds = 0.7f).tooltip())

        val pressed = frame(at, down = true, pressed = true)
        assertNull(pressed.tooltip(), "still shown on the press")
        assertNull(frame(at, down = true).tooltip(), "shown while held")
        frame(at)
        assertNull(hover(at, seconds = 0.7f).tooltip(), "back after the press with the pointer still on the element")

        frame(300 to 150)
        assertNotNull(hover(at, seconds = 0.7f).tooltip(), "not shown again once the pointer returned")
    }

    @Test
    fun leavingTheElementHidesTheTooltip() = withStrings {
        frame()
        assertNotNull(hover(50 to 40, seconds = 0.7f).tooltip())

        assertNull(frame(300 to 150).tooltip())
    }

    @Test
    fun theDelayStartsAgainOnAnotherElement() = withStrings {
        val other = CanvasElement().apply {
            kind = CanvasElementKind.Panel; offsetX = 200f; offsetY = 20f; width = 100f; height = 60f; tooltip = "tip.slot"
        }
        items = listOf(CanvasItem(0, bag), CanvasItem(1, other))
        frame()
        assertNotNull(hover(50 to 40, seconds = 0.7f).tooltip())

        assertNull(frame(250 to 40).tooltip(), "the second element's tooltip waits its own delay")
        assertNotNull(hover(250 to 40, seconds = 0.7f).tooltip())
    }

    @Test
    fun anElementWithoutATooltipShowsNone() = withStrings {
        bag.tooltip = ""
        frame()

        assertNull(hover(50 to 40, seconds = 1f).tooltip())
    }

    @Test
    fun aHiddenElementShowsNoTooltip() = withStrings {
        frame()
        hover(50 to 40, seconds = 0.7f)
        bag.visible = false

        assertNull(frame(50 to 40).tooltip())
    }

    @Test
    fun theTooltipNeverTakesThePointerFromTheElementItNames() = withStrings {
        bag.kind = CanvasElementKind.Button
        frame()
        hover(50 to 40, seconds = 0.7f)
        frame(50 to 40, down = true, pressed = true)
        frame(50 to 40)

        assertTrue(bag.consumePress(), "a press on the button under the pointer still reached it")
    }

    /** Runs [body] with the strings loaded, which suspends. */
    private fun withStrings(body: suspend () -> Unit) = runTest {
        strings = Strings.load(TIP_TABLES, locale = LocaleId.English)
        body()
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 200
    }
}
