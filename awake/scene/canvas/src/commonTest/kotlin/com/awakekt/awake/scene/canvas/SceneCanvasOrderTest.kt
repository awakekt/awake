/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [canvasDrawOrder] and [canvasElementAt] answer from the order [SceneCanvas] draws in, a host's
 * sibling order included, so an editor that picks from the canvas finds what is drawn on top.
 */
class SceneCanvasOrderTest {
    private val world = World()

    private fun node(parent: Entity? = null): Entity = world.create().also { world.add(it, Transform(parent = parent)) }

    private fun panel(parent: Entity? = null, x: Float, width: Float = 50f, color: String, configure: CanvasElement.() -> Unit = {}): Entity =
        node(parent).also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel
                    offsetX = x
                    offsetY = 0f
                    this.width = width
                    height = 40f
                    background = color
                    configure()
                },
            )
        }

    @Test
    fun theDrawOrderIsHierarchyOrderWithAnElementsChildrenRightAfterIt() {
        val window = panel(x = 0f, color = RED)
        val group = node(window)
        val label = panel(group, x = 10f, width = 20f, color = BLUE)
        val hud = panel(x = 30f, color = GREEN)
        val icon = panel(window, x = 0f, width = 5f, color = BLUE)

        assertEquals(listOf(window, label, icon, hud), world.canvasDrawOrder())
    }

    @Test
    fun aHigherOrderDrawsLaterAndAHostsSiblingOrderIsFollowed() {
        val back = panel(x = 0f, color = RED) { order = 1 }
        val front = panel(x = 30f, color = GREEN)
        val third = panel(x = 60f, color = BLUE)

        assertEquals(listOf(front, third, back), world.canvasDrawOrder())
        assertEquals(listOf(third, front, back), world.canvasDrawOrder(compareByDescending { it.id }))
    }

    @Test
    fun theElementFoundAtEachPointIsTheOneDrawnThere() {
        val back = panel(x = 0f, color = RED)
        val inBack = panel(back, x = 10f, width = 20f, color = BLUE)
        val front = panel(x = 30f, color = GREEN)
        val reversed = compareByDescending<Entity> { it.id }

        assertEachPixelIsTheElementFoundThere(null)
        assertEachPixelIsTheElementFoundThere(reversed)
        draw(null)
        assertEquals(front, world.canvasElementAt(40f, 20f))
        assertEquals(inBack, world.canvasElementAt(15f, 20f))
        draw(reversed)
        assertEquals(back, world.canvasElementAt(40f, 20f, reversed), "the host lists the back panel last, so it draws on top")
        assertNull(world.canvasElementAt(110f, 70f, reversed))
    }

    /** Every pixel of a frame drawn with [siblingOrder] is the colour of the element found there, or black where none is. */
    private fun assertEachPixelIsTheElementFoundThere(siblingOrder: Comparator<Entity>?) {
        val pixels = draw(siblingOrder)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val found = world.canvasElementAt(x + 0.5f, y + 0.5f, siblingOrder)
                val color = found?.let { world.get<CanvasElement>(it)?.background?.removePrefix("#")?.toInt(16) } ?: 0
                assertEquals(color, pixelAt(pixels, x, y), "at ($x, $y) with ${if (siblingOrder == null) "the world's order" else "the host's"}")
            }
        }
    }

    private fun draw(siblingOrder: Comparator<Entity>?): ByteArray =
        ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, siblingOrder = siblingOrder) }
            .primitives.rasterize(WIDTH, HEIGHT, Color.Black)

    private fun pixelAt(pixels: ByteArray, x: Int, y: Int): Int {
        val offset = (y * WIDTH + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
        const val RED = "#FF0000"
        const val GREEN = "#00FF00"
        const val BLUE = "#0000FF"
    }
}
