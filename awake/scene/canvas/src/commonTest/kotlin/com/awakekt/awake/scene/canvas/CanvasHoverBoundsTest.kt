/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CanvasElement.isHovered] and [CanvasElement.screenBounds]: what a system reads to place a
 * tooltip beside whatever item is under the pointer.
 */
class CanvasHoverBoundsTest {

    private val world = World()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, parent: Entity? = null, configure: CanvasElement.() -> Unit): Pair<Entity, CanvasElement> {
        val entity = world.create()
        if (parent != null) world.add(entity, Transform(parent = parent))
        val element = CanvasElement().apply { this.kind = kind }.apply(configure)
        world.add(entity, element)
        return entity to element
    }

    private fun frame(x: Int = AWAY, y: Int = AWAY, scale: Float = 1f): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y)) { SceneCanvas(world, scale = scale) }

    @Test
    fun thePointerMovingOverAnImageMarksItHoveredAndLeavingClearsIt() {
        val (_, image) = element(CanvasElementKind.Image) { offsetX = 0f; offsetY = 0f; width = 40f; height = 40f }
        frame() // places the tree once: input is hit-tested against the frame before it.

        frame(20, 20)
        val over = image.isHovered
        frame()

        assertTrue(over, "the pointer sat inside the image's box")
        assertFalse(image.isHovered, "it then left")
    }

    @Test
    fun thePointerMovingOverAButtonMarksItHoveredAndLeavingClearsIt() {
        val (_, button) = element(CanvasElementKind.Button) { offsetX = 0f; offsetY = 0f; width = 100f; height = 40f }
        frame()

        frame(50, 20)
        val over = button.isHovered
        frame()

        assertTrue(over)
        assertFalse(button.isHovered, "it then left")
    }

    @Test
    fun aPanelDrawnOverAButtonTracksItsOwnHoverWithoutSwallowingTheButtonsTap() {
        // Same shape as CanvasClickRoutingTest's regression for a plain panel: the panel here is
        // also hoverable, and must still let the tap -- and the hover -- through to the button.
        val (_, under) = element(CanvasElementKind.Button) { offsetX = 10f; offsetY = 10f; width = 100f; height = 40f }
        val (_, over) = element(CanvasElementKind.Panel) { order = 1; offsetX = 10f; offsetY = 10f; width = 100f; height = 40f }

        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = 50, pointerY = 30)) { SceneCanvas(world) }
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = 50, pointerY = 30, pointerDown = true, pointerPressed = true)) { SceneCanvas(world) }
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = 50, pointerY = 30, pointerReleased = true)) { SceneCanvas(world) }

        assertTrue(under.consumePress(), "the panel drew over it but took nothing, so the tap reached the button")
        assertTrue(under.isHovered, "the button is what the pointer actually lands on")
    }

    @Test
    fun aPanelDrawnBehindAButtonDoesNotStopItsOwnHoverElsewhere() {
        // The "under" half of the same guarantee: a non-button behind a button is still its own
        // hoverable element wherever the button does not cover it.
        val (_, behind) = element(CanvasElementKind.Panel) { offsetX = 0f; offsetY = 0f; width = 200f; height = 40f }
        val (_, button) = element(CanvasElementKind.Button) { order = 1; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f }
        frame()

        frame(150, 20)

        assertTrue(behind.isHovered, "outside the button's box, nothing covers the panel")
        assertFalse(button.isHovered)
    }

    @Test
    fun aLaterSiblingsAnchorWrapperOverAnElementLeavesTheHoverToTheElement() {
        val (_, image) = element(CanvasElementKind.Image) { offsetX = 0f; offsetY = 0f; width = 40f; height = 40f }
        // Inset 100 from the top-left: its padded wrapper spans the image, its own box doesn't.
        val (_, later) = element(CanvasElementKind.Panel) { order = 1; offsetX = 100f; offsetY = 100f; width = 40f; height = 40f }
        frame()

        frame(20, 20)

        assertTrue(image.isHovered, "the pointer is over the image's own box, under only the later panel's padding")
        assertFalse(later.isHovered)
    }

    @Test
    fun screenBoundsMatchesWhereAnElementAndItsChildWerePlaced() {
        val (window, parent) = element(CanvasElementKind.Panel) { offsetX = 16f; offsetY = 8f; width = 200f; height = 100f }
        val (_, corner) = element(CanvasElementKind.Button, window) {
            anchor = CanvasAnchor.BottomRight
            offsetX = 4f
            offsetY = 6f
            width = 50f
            height = 20f
        }

        frame()

        assertEquals(Rectangle(16f, 8f, 200f, 100f), parent.screenBounds)
        assertEquals(Rectangle(162f, 82f, 50f, 20f), corner.screenBounds, "in canvas coordinates, not relative to its parent")
    }

    @Test
    fun screenBoundsIsInCanvasUnitsNotTheScaledPixelsItWasPlacedAt() {
        val (_, bar) = element(CanvasElementKind.Bar) { offsetX = 4f; offsetY = 6f; width = 50f; height = 20f }

        frame(scale = 2f)

        assertEquals(Rectangle(4f, 6f, 50f, 20f), bar.screenBounds)
    }

    @Test
    fun aTooltipPlacedFromAHoveredItemsScreenBoundsLandsExactlyBesideIt() {
        // The whole point: a system reads a hovered item's screenBounds and offsets a root,
        // top-left-anchored tooltip from it, in the same canvas units, to sit beside it.
        val (_, item) = element(CanvasElementKind.Image) { offsetX = 30f; offsetY = 40f; width = 32f; height = 32f }
        val (_, tooltip) = element(CanvasElementKind.Text) { visible = false; anchor = CanvasAnchor.TopLeft; width = 80f; height = 20f }
        frame()

        frame(40, 50)
        val bounds = assertNotNull(item.screenBounds)
        tooltip.visible = true
        tooltip.offsetX = bounds.x + bounds.width
        tooltip.offsetY = bounds.y
        frame(40, 50)

        assertEquals(Rectangle(30f + 32f, 40f, 80f, 20f), tooltip.screenBounds, "beside the item, at its own top-right corner")
    }

    @Test
    fun anElementThatStopsBeingDrawnLosesItsBounds() {
        val (_, panel) = element(CanvasElementKind.Panel) { offsetX = 0f; offsetY = 0f; width = 40f; height = 40f }

        frame()
        val shown = panel.screenBounds
        panel.visible = false
        frame()

        assertNotNull(shown, "drawn, so it had a box")
        assertNull(panel.screenBounds, "hidden now, so no longer drawn")
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600

        // Off whatever this test placed, so a bare frame() always reads as the pointer having left.
        const val AWAY = -1000
    }
}
