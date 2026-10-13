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

/** Where an element's anchor and offset place it on the screen, or in its parent. */
class SceneCanvasAnchorTest {

    private val world = World()

    private fun element(configure: CanvasElement.() -> Unit): Entity =
        world.create().also { world.add(it, CanvasElement().apply(configure)) }

    /** An element on a new entity below [parent] in the scene hierarchy. */
    private fun child(parent: Entity, configure: CanvasElement.() -> Unit): Entity =
        world.create().also {
            world.add(it, Transform(parent = parent))
            world.add(it, CanvasElement().apply(configure))
        }

    private fun frame(host: ComposeHost = ComposeHost(), input: FrameInput = FrameInput(800, 600)): FrameOutput =
        host.frame(input) { SceneCanvas(world) }

    private fun FrameOutput.node(entity: Entity): SemanticsNode? = semantics.find("canvas-element-${entity.id}")

    private fun FrameOutput.box(entity: Entity): List<Int> = assertNotNull(node(entity)).let { listOf(it.x, it.y, it.width, it.height) }

    @Test
    fun anchorsPlaceElementsInwardFromEachScreenEdge() {
        val topLeft = element { anchor = CanvasAnchor.TopLeft; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val bottomRight = element { anchor = CanvasAnchor.BottomRight; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val center = element { anchor = CanvasAnchor.Center; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }

        val out = frame()

        assertEquals(16 to 8, assertNotNull(out.node(topLeft)).let { it.x to it.y })
        assertEquals(800 - 16 - 200 to 600 - 8 - 40, assertNotNull(out.node(bottomRight)).let { it.x to it.y })
        assertEquals(350 to 275, assertNotNull(out.node(center)).let { it.x to it.y })
    }

    @Test
    fun aNegativeOffsetOnACentredAxisPlacesTheElementLeftOfOrAboveCentre() {
        val center = element { anchor = CanvasAnchor.Center; offsetX = -30f; offsetY = -20f; width = 100f; height = 50f }
        val top = element { anchor = CanvasAnchor.TopCenter; offsetX = -30f; offsetY = 8f; width = 100f; height = 50f }
        val right = element { anchor = CanvasAnchor.CenterRight; offsetX = 16f; offsetY = -20f; width = 100f; height = 50f }
        lateinit var pressable: CanvasElement
        val button = element {
            kind = CanvasElementKind.Button; anchor = CanvasAnchor.BottomCenter; offsetX = -120f; offsetY = 8f; width = 100f; height = 40f
            pressable = this
        }
        val host = ComposeHost()

        val out = frame(host)
        frame(host, FrameInput(800, 600, pointerX = 280, pointerY = 572, pointerDown = true, pointerPressed = true))
        frame(host, FrameInput(800, 600, pointerX = 280, pointerY = 572, pointerReleased = true))

        assertEquals(listOf(350 - 30, 275 - 20, 100, 50), out.box(center), "30 dp left of and 20 dp above centre, at its own size")
        assertEquals(listOf(350 - 30, 8, 100, 50), out.box(top), "left of centre, the edge axis unchanged")
        assertEquals(listOf(800 - 16 - 100, 275 - 20, 100, 50), out.box(right), "above centre, the edge axis unchanged")
        assertEquals(listOf(350 - 120, 600 - 8 - 40, 100, 40), out.box(button))
        assertTrue(pressable.consumePress(), "a tap where it is drawn, left of centre, presses it")
    }

    @Test
    fun aNegativeOffsetAtAnEdgeIsHeldAtTheEdge() {
        val topLeft = element { anchor = CanvasAnchor.TopLeft; offsetX = -10f; offsetY = -10f; width = 100f; height = 50f }
        val bottomRight = element { anchor = CanvasAnchor.BottomRight; offsetX = -10f; offsetY = -10f; width = 100f; height = 50f }
        val parent = element { kind = CanvasElementKind.Panel; offsetX = 40f; offsetY = 40f; width = 200f; height = 100f }
        val inside = child(parent) { anchor = CanvasAnchor.TopRight; offsetX = -10f; offsetY = -10f; width = 50f; height = 20f }

        val out = frame()

        assertEquals(listOf(0, 0, 100, 50), out.box(topLeft))
        assertEquals(listOf(800 - 100, 600 - 50, 100, 50), out.box(bottomRight))
        assertEquals(listOf(40 + 200 - 50, 40, 50, 20), out.box(inside), "against the parent's corner, not past it")
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
