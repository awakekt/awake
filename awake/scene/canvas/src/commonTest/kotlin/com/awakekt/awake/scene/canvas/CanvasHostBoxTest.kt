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

/**
 * A canvas a host draws over part of the window, such as an editor's game view, lands its followers
 * on their nodes: a [CanvasProjector] reports the frame's pixels, and the canvas moves them into its
 * own box.
 */
class CanvasHostBoxTest {
    private val world = World()

    private fun element(configure: CanvasElement.() -> Unit): Entity = world.create().also { world.add(it, CanvasElement().apply(configure)) }

    /** Ten frame pixels per world unit across and down, from the window's corner. */
    private class FlatProjector(private val box: Rectangle) : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x * 10f, y * 10f)

        override fun bounds(entity: Entity): Rectangle = box
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
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
