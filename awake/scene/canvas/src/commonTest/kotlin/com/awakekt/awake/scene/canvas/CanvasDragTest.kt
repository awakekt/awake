/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `draggable` and `dropTarget`: while dragged, `CanvasElement.dragging` is true; a release over a
 * drop target reports the source through its `dropped`, and a release elsewhere reports the point
 * on the dragged element's own `droppedOutside`. A press and release that never moved is a click,
 * not a drag.
 */
class CanvasDragTest {

    private val world = World()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, parent: CanvasElement? = null, configure: CanvasElement.() -> Unit): CanvasElement {
        val element = CanvasElement().apply { this.kind = kind }.apply(configure)
        val entity = world.create()
        world.add(entity, element)
        if (parent != null) world.add(entity, Transform(parent = entities.getValue(parent)))
        entities[element] = entity
        return element
    }

    private val entities = HashMap<CanvasElement, Entity>()

    private fun CanvasElement.at(x: Float, y: Float, w: Float, h: Float) {
        anchor = CanvasAnchor.TopLeft
        offsetX = x
        offsetY = y
        width = w
        height = h
    }

    private fun frame(x: Int = 0, y: Int = 0, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }

    @Test
    fun aDragFromADraggableElementOntoADropTargetReportsTheSourceOnce() {
        val source = element(CanvasElementKind.Panel) { at(10f, 10f, 40f, 40f); draggable = true }
        val target = element(CanvasElementKind.Panel) { at(200f, 10f, 40f, 40f); dropTarget = true }

        frame(30, 30)
        frame(30, 30, pressed = true, down = true)
        frame(220, 30, down = true) // past the threshold, and already over the target
        frame(220, 30, released = true)

        assertEquals(source, target.dropped, "the drop target names the dragged element")
        assertNull(target.dropped, "dropped reads once, then null")
    }

    @Test
    fun aReleaseOverNoDropTargetReportsDroppedOutsideWithItsPoint() {
        val source = element(CanvasElementKind.Panel) { at(10f, 10f, 40f, 40f); draggable = true }

        frame(30, 30)
        frame(30, 30, pressed = true, down = true)
        frame(500, 400, down = true)
        frame(500, 400, released = true)

        assertEquals(Vec2(500f, 400f), source.droppedOutside, "the release point, in canvas units")
        assertNull(source.droppedOutside, "droppedOutside reads once, then null")
    }

    @Test
    fun aPressAndReleaseWithoutMovementIsAClickNotADrag() {
        val button = element(CanvasElementKind.Button) { at(10f, 10f, 100f, 40f); draggable = true }

        frame(60, 30)
        frame(60, 30, pressed = true, down = true)
        frame(60, 30, released = true) // released where it was pressed: no move ever crossed the threshold

        assertTrue(button.consumePress(), "a draggable Button still clicks")
        assertFalse(button.dragging)
        assertNull(button.droppedOutside)
    }

    @Test
    fun aLiveDragClaimsThePointerSoGameplayDoesNotSeeIt() {
        val source = element(CanvasElementKind.Panel) { at(10f, 10f, 40f, 40f); draggable = true }

        frame(30, 30)
        frame(30, 30, pressed = true, down = true)
        val midDrag = frame(220, 30, down = true)

        assertTrue(source.dragging, "the move must actually have crossed the threshold for this to prove anything")
        assertTrue(midDrag.ownership.isCaptured, "a live drag must not leave its events for gameplay")

        val afterRelease = frame(220, 30, released = true)
        assertFalse(afterRelease.ownership.isCaptured, "released, so gameplay has the pointer back")
    }

    @Test
    fun aDropLandsOnTheTargetDrawnOnTop() {
        val source = element(CanvasElementKind.Panel) { at(10f, 10f, 40f, 40f); draggable = true }
        val window = element(CanvasElementKind.Panel) { at(200f, 10f, 200f, 200f); dropTarget = true }
        val slot = element(CanvasElementKind.Panel, parent = window) { at(10f, 10f, 40f, 40f); dropTarget = true }

        frame(30, 30)
        frame(30, 30, pressed = true, down = true)
        frame(230, 40, down = true)
        frame(230, 40, released = true)

        assertEquals(source, slot.dropped, "the slot is drawn over its window")
        assertNull(window.dropped, "and the window under it gets nothing")
    }

    @Test
    fun theGhostDrawsTheChildrenWithoutMovingTheirBoxes() {
        val source = element(CanvasElementKind.Panel) { at(10f, 10f, 40f, 40f); draggable = true }
        val icon = element(CanvasElementKind.Image, parent = source) { at(4f, 4f, 32f, 32f) }

        frame(30, 30)
        val before = icon.screenBounds
        frame(30, 30, pressed = true, down = true)
        val midDrag = frame(400, 300, down = true)

        assertTrue(source.dragging)
        assertEquals(before, icon.screenBounds, "the icon's box is where the slot is, not where its ghost is")
        assertTrue(midDrag.ownership.isCaptured)
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}
