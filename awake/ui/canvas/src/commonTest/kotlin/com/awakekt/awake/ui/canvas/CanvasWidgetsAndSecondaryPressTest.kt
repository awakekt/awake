/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CanvasWidgetsAndSecondaryPressTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()

    private fun element(
        kind: CanvasElementKind = CanvasElementKind.Button,
        configure: CanvasElement.() -> Unit = {},
    ): CanvasElement = CanvasElement().apply {
        this.kind = kind
        offsetX = 0f
        offsetY = 0f
        width = 80f
        height = 40f
    }.apply(configure).also { items += CanvasItem(items.size + 1, it) }

    private fun rightClick(x: Int = 20, y: Int = 20) {
        host.frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(items) }
        host.frame(
            FrameInput(
                viewportWidth = WIDTH,
                viewportHeight = HEIGHT,
                pointerX = x,
                pointerY = y,
                secondaryPointerPressed = true,
            ),
        ) { CanvasOverlay(items) }
    }

    @Test
    fun secondaryPressIsReportedAndConsumedOnceAndBlockedByDisabledButton() {
        val under = element { order = 0 }
        val top = element { order = 1 }

        rightClick()
        assertTrue(top.secondaryPressed)
        assertTrue(top.consumeSecondaryPress())
        assertFalse(top.secondaryPressed)
        assertFalse(top.consumeSecondaryPress(), "secondary press is consumed once")
        assertFalse(under.consumeSecondaryPress(), "top button occluded the element under it")

        top.enabled = false
        rightClick()
        assertFalse(top.consumeSecondaryPress(), "disabled button reports no secondary press")
        assertFalse(under.consumeSecondaryPress(), "disabled button still keeps secondary press from falling through")
    }

    @Test
    fun pressablePanelReportsSecondaryPress() {
        val panel = element(CanvasElementKind.Panel) { pressable = true }

        rightClick()
        assertTrue(panel.consumeSecondaryPress())
        assertFalse(panel.consumeSecondaryPress())
    }

    @Test
    fun scrollBarScrollsByArrowsPagesAndFractionalWheelAndPlacesThumb() {
        val nodes = mutableMapOf<String, CanvasElement>()
        fun add(name: String, configure: CanvasElement.() -> Unit = {}): CanvasElement =
            CanvasElement().apply(configure).also { nodes[name] = it }

        val up = add(CanvasScrollBar.up("inventory")) { kind = CanvasElementKind.Button }
        val down = add(CanvasScrollBar.down("inventory")) { kind = CanvasElementKind.Button }
        val track = add(CanvasScrollBar.track("inventory")) { kind = CanvasElementKind.Panel; height = 100f }
        val thumb = add(CanvasScrollBar.thumb("inventory")) { kind = CanvasElementKind.Panel }
        val pageUp = add(CanvasScrollBar.pageUp("inventory")) { kind = CanvasElementKind.Button }
        val pageDown = add(CanvasScrollBar.pageDown("inventory")) { kind = CanvasElementKind.Button }
        val grid = add("inventory items") { kind = CanvasElementKind.Panel; wheel = true }

        val scrollBar = CanvasScrollBar.of("inventory", wheel = "inventory items") { nodes[it] }

        // When all rows fit, thumb and page regions hide.
        assertEquals(0, scrollBar.scroll(first = 0, rows = 5, shown = 5))
        assertFalse(thumb.visible)
        assertFalse(pageUp.visible)
        assertFalse(pageDown.visible)

        // 20 rows, 5 shown -> thumb height = 25, starting at 0.
        assertEquals(0, scrollBar.scroll(first = 0, rows = 20, shown = 5))
        assertTrue(thumb.visible)
        assertEquals(25f, thumb.height, 0.01f)
        assertEquals(0f, thumb.offsetY, 0.01f)

        // Down button moves 1 row; pageDown moves 5 rows.
        down.press()
        assertEquals(1, scrollBar.scroll(first = 0, rows = 20, shown = 5))
        pageDown.press()
        assertEquals(6, scrollBar.scroll(first = 1, rows = 20, shown = 5))
        up.press()
        assertEquals(5, scrollBar.scroll(first = 6, rows = 20, shown = 5))
        pageUp.press()
        assertEquals(0, scrollBar.scroll(first = 5, rows = 20, shown = 5))

        // Fractional wheel accumulates across frames.
        grid.addWheel(-0.6f)
        assertEquals(0, scrollBar.scroll(first = 0, rows = 20, shown = 5))
        grid.addWheel(-0.6f)
        assertEquals(1, scrollBar.scroll(first = 0, rows = 20, shown = 5))
    }

    @Test
    fun spriteSheetSlicesCellsAndHorizontalStripsUpdateStatesWithoutExtraAllocations() {
        val sheet = CanvasSpriteSheet(
            path = "assets/ui/icons.png",
            columns = 4,
            cellWidth = 32f,
            cellHeight = 32f,
            pixelated = true,
        )
        val cell6 = sheet.image(6, Color.fromHex("#FF8800"))
        assertEquals("assets/ui/icons.png", cell6.path)
        assertEquals(64, cell6.regionX)
        assertEquals(32, cell6.regionY)
        assertEquals(32, cell6.regionWidth)
        assertEquals(32, cell6.regionHeight)
        assertEquals("#FF8800", cell6.tint)
        assertTrue(cell6.pixelated)

        val button = CanvasElement().apply {
            kind = CanvasElementKind.Button
            width = 72f
            height = 20f
            style = CanvasStyle(image = CanvasImage("assets/ui/button.png", regionWidth = 72, regionHeight = 20))
        }
        button.applyFourStateHorizontalStrip(enabled = true)
        val enabledStyle = button.style
        assertEquals(72, enabledStyle.image?.regionX)
        assertEquals(0, enabledStyle.hovered?.image?.regionX)
        assertEquals(144, enabledStyle.pressed?.image?.regionX)
        assertEquals(216, enabledStyle.disabled?.image?.regionX)

        button.applyFourStateHorizontalStrip(enabled = true)
        assertSame(enabledStyle, button.style, "Repeated call with same state must not reallocate style")

        button.applyFourStateHorizontalStrip(enabled = false)
        assertFalse(button.enabled)
        assertEquals(216, button.style.image?.regionX)
        assertEquals(216, button.style.hovered?.image?.regionX)

        val checkbox = CanvasElement().apply {
            kind = CanvasElementKind.Button
            width = 14f
            height = 14f
            style = CanvasStyle(image = CanvasImage("assets/ui/check.png", regionWidth = 14, regionHeight = 14))
        }
        checkbox.applySixStateCheckboxStrip(checked = true, enabled = true)
        assertEquals(42, checkbox.style.image?.regionX)
        assertEquals(28, checkbox.style.hovered?.image?.regionX)
        assertEquals(28, checkbox.style.pressed?.image?.regionX)
        assertEquals(70, checkbox.style.disabled?.image?.regionX)
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
    }
}
