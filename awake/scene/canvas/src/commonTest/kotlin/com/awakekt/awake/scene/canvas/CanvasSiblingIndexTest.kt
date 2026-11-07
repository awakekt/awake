/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order canvas content declares its siblings in is the order they draw in and the order the
 * pointer finds them in, and it is never saved: a scene saved after a move loads and saves the same.
 */
class CanvasSiblingIndexTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val applier = CanvasEntityApplier(world, hud)
    private val composition = Composition(applier, applier.root)
    private var order = listOf("a", "b")

    private val content: context(Composer)
    () -> Unit = {
        order.forEach { id -> key(id) { button(id) } }
    }

    context(_: Composer)
    private fun button(id: String) {
        val parts = CanvasElementScope(id, CanvasElementKind.Button, id).apply {
            anchor = CanvasAnchor.TopLeft
            offset(Dp(0f), Dp(0f))
            size(Dp(60f), Dp(40f))
        }.canvasParts()
        node(CanvasElementKind.Button, update = { applier.write(it as CanvasContentNode, parts) })
    }

    private fun pass() {
        composition.reconcile(content)
        applier.finishPass()
    }

    private fun entity(id: String): Entity = checkNotNull(world.findCanvasElement(hud, id))

    /** Taps (20, 20), where both buttons are, the way a mouse does, and returns the ids that took it. */
    private fun tap(): List<String> {
        val host = ComposeHost()
        fun frame(pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
            host.frame(FrameInput(WIDTH, HEIGHT, pointerX = 20, pointerY = 20, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world)
            }
        frame()
        frame(pressed = true, down = true)
        frame(released = true)
        return listOf("a", "b").filter { checkNotNull(world.get<CanvasElement>(entity(it))).consumePress() }
    }

    @Test
    fun afterAMoveTheElementNowOnTopTakesTheTap() {
        pass()
        assertEquals(listOf("b"), tap(), "the later declared draws on top")

        order = listOf("b", "a")
        pass()

        assertEquals(listOf("a"), tap())
        assertEquals(entity("a"), world.canvasElementAt(20f, 20f), "what an editor's click picks")
    }

    @Test
    fun aSceneSavedAfterAMoveRoundTrips() {
        pass()
        order = listOf("b", "a")
        pass()

        val saved = world.saveCanvas()

        assertEquals(saved, loadCanvas(saved).saveCanvas())
        assertTrue(saved.flatMap { it.children }.flatMap { it.components }.all { it.isCanvasElementPart() }, "no sibling index is saved")
    }

    @Test
    fun aSiblingWithNoIndexComesBeforeIndexedOnes() {
        pass()
        // Made after the content, so stored after it: without the rule it would draw on top.
        val scenery = world.create()
        world.add(scenery, Name("scenery"))
        world.add(scenery, Transform(parent = hud))
        world.addCanvasElement(scenery, CanvasElement())

        val drawn = world.canvasDrawOrder()

        assertEquals(listOf(scenery, entity("a"), entity("b")), drawn)
        assertFalse(world.has<CanvasSiblingIndex>(scenery))
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
    }
}
