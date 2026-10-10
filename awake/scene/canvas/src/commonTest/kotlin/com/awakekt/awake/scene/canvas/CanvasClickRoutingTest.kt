/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A tap reaches the topmost element under the pointer that handles it.
 *
 * Every element sits in a box inset by its offset, and that box starts at the parent's anchor corner,
 * so a later sibling's box covers the earlier siblings' area. A window of buttons anchored top-left
 * with different offsets could then be tapped only on its last-drawn button: the tap stopped at the
 * later button's empty inset box and went no further.
 */
class CanvasClickRoutingTest {

    private val world = World()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, parent: Entity?, configure: CanvasElement.() -> Unit): Entity =
        world.create().also {
            if (parent != null) world.add(it, Transform(parent = parent))
            world.add(it, CanvasElement().apply { this.kind = kind }.apply(configure))
        }

    private fun button(parent: Entity? = null, configure: CanvasElement.() -> Unit) = element(CanvasElementKind.Button, parent, configure)

    private fun panel(parent: Entity? = null, configure: CanvasElement.() -> Unit) = element(CanvasElementKind.Panel, parent, configure)

    private fun frame(x: Int = 0, y: Int = 0, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }

    /** Taps the pixel ([x], [y]) the way a mouse does: the pointer arrives, goes down and comes up. */
    private fun tap(x: Int, y: Int) {
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        frame(x, y, released = true)
    }

    /** Taps the middle of [entity]'s placed box. */
    private fun tapMiddleOf(entity: Entity) {
        val box = assertNotNull(frame().semantics.find("canvas-element-${entity.id}"), "$entity is not on screen")
        tap(box.x + box.width / 2, box.y + box.height / 2)
    }

    private fun CanvasElement.at(x: Float, y: Float, w: Float = 40f, h: Float = 20f, anchor: CanvasAnchor = CanvasAnchor.TopLeft) {
        this.anchor = anchor
        offsetX = x
        offsetY = y
        width = w
        height = h
    }

    /** Every button in [buttons] that was tapped since it was last asked. */
    private fun tapped(buttons: List<Entity>): List<Entity> = buttons.filter { world.get<CanvasElement>(it)!!.consumePress() }

    @Test
    fun anEarlierButtonIsTappableBeneathALaterSiblingsInsetBox() {
        val early = button { at(10f, 10f) }
        val late = button { at(100f, 10f) }

        tap(30, 20)

        assertEquals(listOf(early), tapped(listOf(early, late)), "the tap was on the earlier button, not the later one whose box starts at the same corner")
    }

    @Test
    fun theLaterButtonIsStillTappable() {
        val early = button { at(10f, 10f) }
        val late = button { at(100f, 10f) }

        tap(120, 20)

        assertEquals(listOf(late), tapped(listOf(early, late)))
    }

    @Test
    fun everyButtonOfAWindowTakesItsOwnTap() {
        val window = panel { at(20f, 20f, 300f, 200f) }
        val buttons = buildList {
            add(button(window) { at(270f, 4f, 24f, 16f) })
            add(button(window) { at(4f, 4f, 16f, 16f) })
            add(button(window) { at(4f, 150f, 16f, 16f) })
            for (slot in 0 until 6) add(button(window) { at(30f + slot % 3 * 50f, 30f + slot / 3 * 50f, 40f, 40f) })
        }
        frame()

        for (target in buttons) {
            tapMiddleOf(target)

            assertEquals(listOf(target), tapped(buttons), "a tap in the middle of $target belongs to it and to no other button")
        }
    }

    @Test
    fun anElementAnchoredAtAnyCornerOrEdgeIsTappableBeneathALaterOne() {
        for (anchor in CanvasAnchor.entries) {
            val early = button { at(4f, 4f, 40f, 20f, anchor) }
            // Its box reaches furthest, in towards the screen's middle from any anchor.
            val late = button { at(150f, 120f, 40f, 20f, anchor) }
            frame()

            tapMiddleOf(early)

            assertEquals(listOf(early), tapped(listOf(early, late)), "$anchor: the earlier button took the tap")
            tapMiddleOf(late)
            assertEquals(listOf(late), tapped(listOf(early, late)), "$anchor: the later button took its own")
            world.destroy(early)
            world.destroy(late)
        }
    }

    @Test
    fun theInsetAroundAnElementTakesNoTap() {
        val early = button { at(10f, 10f) }
        val late = button { at(100f, 40f) }

        // Between the screen corner and the late button: inside its box, outside the element, and
        // outside the earlier button as well.
        tap(70, 30)

        assertEquals(emptyList(), tapped(listOf(early, late)))
    }

    @Test
    fun whenTwoButtonsOverlapTheLaterOneTakesTheTap() {
        val under = button { at(10f, 10f, 100f, 40f) }
        val over = button { at(50f, 20f, 100f, 40f) }

        tap(80, 30)

        assertEquals(listOf(over), tapped(listOf(under, over)), "drawn last, so on top")
    }

    @Test
    fun aPanelDrawnOverAButtonDoesNotSwallowItsTap() {
        // The panel handles nothing, so the tap goes through to the button beneath, as in Compose.
        val under = button { at(10f, 10f, 100f, 40f) }
        panel {
            order = 1
            at(10f, 10f, 100f, 40f)
        }

        tap(50, 30)

        assertEquals(listOf(under), tapped(listOf(under)))
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
