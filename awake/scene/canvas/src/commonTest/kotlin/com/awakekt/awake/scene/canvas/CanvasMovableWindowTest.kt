/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A window with a title bar marked `moveHandle`, a close button on the bar and a draggable slot in
 * its body, drawn by [SceneCanvas]: dragging the bar moves the window, the button still clicks, and
 * the slot still drags and drops, all on the same window.
 */
class CanvasMovableWindowTest {
    private val world = World()
    private val host = ComposeHost()
    private val entities = HashMap<CanvasElement, Entity>()

    private fun element(kind: CanvasElementKind, parent: CanvasElement? = null, configure: CanvasElement.() -> Unit): CanvasElement {
        val element = CanvasElement().apply { this.kind = kind; anchor = CanvasAnchor.TopLeft }.apply(configure)
        val entity = world.create()
        world.add(entity, element)
        if (parent != null) world.add(entity, Transform(parent = entities.getValue(parent)))
        entities[element] = entity
        return element
    }

    private fun frame(x: Int, y: Int, pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }

    private fun drag(x: Int, y: Int, toX: Int, toY: Int) {
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        frame(toX, toY, down = true)
        frame(toX, toY, released = true)
        frame(toX, toY)
    }

    private val window = element(CanvasElementKind.Panel) { offsetX = 100f; offsetY = 100f; width = 240f; height = 180f }
    private val bar = element(CanvasElementKind.Panel, window) { offsetX = 0f; offsetY = 0f; width = 240f; height = 24f; moveHandle = true }
    private val close = element(CanvasElementKind.Button, bar) { offsetX = 216f; offsetY = 0f; width = 24f; height = 24f }
    private val slot = element(CanvasElementKind.Panel, window) { offsetX = 20f; offsetY = 60f; width = 40f; height = 40f; draggable = true }
    private val target = element(CanvasElementKind.Panel) { offsetX = 600f; offsetY = 400f; width = 60f; height = 60f; dropTarget = true }

    @Test
    fun theBarMovesTheWindowTheButtonClicksAndTheSlotDrags() {
        drag(150, 110, 250, 160)
        assertEquals(200f, window.offsetX, "the bar moved the window")
        assertEquals(150f, window.offsetY)
        assertEquals(200f, world.get(entities.getValue(window), CanvasRect::class)?.offsetX, "into the saved component")
        assertFalse(close.consumePress(), "a move is not a click on the bar's button")

        // The close button now sits at (200 + 216, 150), 24 square.
        drag(428, 162, 428, 162)
        assertTrue(close.consumePress(), "the button on the bar still clicks")
        assertEquals(200f, window.offsetX, "and the click moved nothing")

        // The slot now sits at (220, 210), 40 square.
        drag(240, 230, 630, 430)
        assertEquals(slot, target.dropped, "the slot inside the window still drags and drops")
        assertEquals(200f, window.offsetX, "and dragging it never moves the window")
        assertEquals(150f, window.offsetY)
    }

    @Test
    fun theMovedWindowReportsItsOffsetOnce() {
        drag(150, 110, 170, 140)

        val moved = window.moved
        assertEquals(120f, moved?.x)
        assertEquals(130f, moved?.y)
        assertNull(window.moved)
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}

/** `moveHandle` and `moveStrip` as a prefab saves them and a scene loads them back. */
class CanvasMoveHandleSchemaTest {
    @Test
    fun aPrefabSavesTheHandleOnItsCanvasInput() {
        val prefab = canvasPrefab(guid = "0b3f6a52-6a3e-4b3c-9d1e-5b2d7a9c1e44", name = "Window") {
            size(240.dp, 180.dp)
            panel("Title Bar") {
                size(240.dp, 24.dp)
                moveHandle = true
                moveStrip = 32.dp
            }
        }

        val input = prefab.root.children.single().components.filterIsInstance<SceneCanvasInput>().single()
        assertEquals(SceneCanvasInput(moveHandle = true, moveStrip = 32f), input)
        val read = CanvasPrefabs.fromJson(CanvasPrefabs.toJson(prefab))
        assertEquals(input, read.root.children.single().components.filterIsInstance<SceneCanvasInput>().single(), "and reads it back")
    }

    @Test
    fun theBindingCarriesTheHandleToTheLiveElement() {
        val live = CanvasInputBinding.run { SceneCanvasInput(moveHandle = true, moveStrip = 12f).toComponent() }

        assertTrue(live.moveHandle)
        assertEquals(12f, live.moveStrip)
    }

    @Test
    fun aHandleThatAlsoDragsIsAProblem() {
        val issues = SceneCanvasInput(draggable = true, moveHandle = true).validate("node", emptyList())

        assertEquals(listOf("canvas_input.moveHandle and draggable both drag the element; keep one"), issues.map { it.message })
    }
}
