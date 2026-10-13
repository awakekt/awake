/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A canvas a host draws over part of the window, such as an editor's game view, lands its followers
 * on their nodes: a [CanvasProjector] reports the frame's pixels, and the canvas moves them into its
 * own box. Its elements' [CanvasElement.screenBounds], a drag's ghost and a drop's point are its own
 * box's too, not the window's.
 */
class CanvasHostBoxTest {
    private val world = World()

    private fun element(configure: CanvasElement.() -> Unit): Entity = world.create().also { world.add(it, CanvasElement().apply(configure)) }

    /** Ten frame pixels per world unit across and down, from the window's corner. */
    private class FlatProjector(private val box: Rectangle) : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x * 10f, y * 10f)

        override fun bounds(entity: Entity): Rectangle = box
    }

    private fun canvas(entity: Entity): CanvasElement = assertNotNull(world.get<CanvasElement>(entity))

    /**
     * A frame of a game view at (100, 40) in the window, whose canvas pads itself 20 in: its box
     * starts at (120, 60), [scale] canvas units a pixel.
     */
    private fun ComposeHost.inView(
        x: Int = FrameInput.UNKNOWN_POINTER,
        y: Int = FrameInput.UNKNOWN_POINTER,
        pressed: Boolean = false,
        down: Boolean = false,
        released: Boolean = false,
        scale: Float = 1f,
    ): FrameOutput = frame(FrameInput(800, 600, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
        Box(Modifier.padding(start = 100.dp, top = 40.dp).size(600.dp, 500.dp)) {
            SceneCanvas(world, Modifier.padding(start = 20.dp, top = 20.dp), scale = scale)
        }
    }

    private fun FrameOutput.box(entity: Entity): List<Int> =
        assertNotNull(semantics.find("canvas-element-${entity.id}")).let { listOf(it.x, it.y, it.width, it.height) }

    @Test
    fun aCanvasDrawnOverPartOfTheWindowPutsAFollowerOnItsNodesPixelInTheFrame() {
        world.create().also {
            world.add(it, Name("Hero"))
            world.add(it, Transform().apply { worldMatrix = Mat4().translate(20f, 10f, 0f) })
        }
        val plate = element { follow = "Hero"; anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }
        val marker = element { follow = "Hero"; followBounds = true }
        val corner = element { anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }
        val projector = FlatProjector(box = Rectangle(150f, 80f, 80f, 60f))
        val host = ComposeHost()

        // A game view at (100, 40) in the window, whose canvas pads itself 20 in: its box starts at (120, 60).
        fun draw() = host.frame(FrameInput(800, 600)) {
            Box(Modifier.padding(start = 100.dp, top = 40.dp).size(300.dp, 200.dp)) {
                SceneCanvas(world, Modifier.padding(start = 20.dp, top = 20.dp), projector = projector)
            }
        }

        draw()
        // The second frame knows where the first placed the canvas in the window.
        val out = draw()

        assertEquals(listOf(200, 100, 10, 10), out.box(plate), "on the node's pixel in the frame, not 120 by 60 past it")
        assertEquals(listOf(150, 80, 80, 60), out.box(marker), "over the node's box in the frame")
        assertEquals(listOf(120, 60, 10, 10), out.box(corner), "an anchored element still sits in the canvas's own box")
    }

    @Test
    fun anElementsScreenBoundsAreFromTheCanvasBoxsCornerNotTheWindows() {
        val panel = element { anchor = CanvasAnchor.TopLeft; offsetX = 10f; offsetY = 20f; width = 30f; height = 40f }
        val host = ComposeHost()

        // Drawn at 2x, so at (140, 100) in the window, 60 by 80 pixels; one frame, as a box is read off as it is placed.
        val out = host.inView(scale = 2f)

        assertEquals(listOf(140, 100, 60, 80), out.box(panel), "where the canvas drew it")
        assertEquals(Rectangle(10f, 20f, 30f, 40f), canvas(panel).screenBounds, "as its offsets and size say, not 120 by 60 past them")
    }

    @Test
    fun aDragsGhostDrawsUnderThePointerAndADropOutsideIsInTheCanvassUnits() {
        val source = element {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            offsetX = 10f
            offsetY = 10f
            width = GHOSTED
            height = GHOSTED
            background = "#FF0000"
            draggable = true
        }
        val host = ComposeHost()

        // The element's centre in the window: the box's corner (120, 60), its offset and half its size.
        host.inView(150, 90)
        host.inView(150, 90, pressed = true, down = true)
        host.inView(400, 300, down = true)
        val midDrag = host.inView(400, 300, down = true)

        assertTrue(canvas(source).dragging, "the move must have crossed the threshold for this to prove anything")
        val ghost = midDrag.primitives.filterIsInstance<UiDrawPrimitive.Quad>()
            .filter { it.w == GHOSTED && it.h == GHOSTED }
            .single { it.x != 130f || it.y != 70f }
        assertEquals(Vec2(400f, 300f), Vec2(ghost.x + ghost.w / 2, ghost.y + ghost.h / 2), "on the pointer, not 120 by 60 past it")

        host.inView(400, 300, released = true)
        assertEquals(Vec2(280f, 240f), canvas(source).droppedOutside, "from the canvas box's corner, as its screenBounds are")
    }

    @Test
    fun aDropInACanvasOverPartOfTheWindowLandsOnTheTargetUnderThePointer() {
        val source = element { anchor = CanvasAnchor.TopLeft; offsetX = 10f; offsetY = 10f; width = 40f; height = 40f; draggable = true }
        val target = element { anchor = CanvasAnchor.TopLeft; offsetX = 200f; offsetY = 10f; width = 40f; height = 40f; dropTarget = true }
        val host = ComposeHost()

        host.inView(150, 90)
        host.inView(150, 90, pressed = true, down = true)
        host.inView(340, 90, down = true)
        host.inView(340, 90, released = true)

        assertEquals(canvas(source), canvas(target).dropped, "the target is at (320, 70) in the window")
        assertNull(canvas(source).droppedOutside)
    }

    private companion object {
        const val GHOSTED = 40f
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
