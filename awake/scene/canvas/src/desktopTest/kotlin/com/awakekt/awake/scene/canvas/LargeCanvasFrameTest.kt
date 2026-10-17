/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * A game's HUD as it plays: [WINDOWS] windows of [PER_WINDOW] elements, all but [OPEN] closed, over a
 * world of [NAMED] named nodes, with [FOLLOWERS] name plates over some of them. Most of the canvas is
 * hidden, so a frame should cost about what the open windows and the plates draw, not what sorting
 * every element and indexing every name costs.
 *
 * The time bound is loose, for a shared CI machine. It prints the mean frame so a change's cost
 * shows in the log.
 */
class LargeCanvasFrameTest {

    private val world = World()

    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null
    }

    private fun node(parent: Entity? = null): Entity = world.create().also { world.add(it, Transform(parent = parent)) }

    private fun hud() {
        // The world's nodes first, as a loaded scene stores them, each named.
        repeat(NAMED) { index ->
            node().also {
                world.add(it, Name("Node $index"))
                world.get<Transform>(it)!!.worldMatrix = Mat4().translate((index % 40) * 30f, (index / 40 % 20) * 30f, 0f)
            }
        }
        val root = node()
        repeat(WINDOWS) { window ->
            val panel = node(root).also {
                world.add(it, CanvasElement().apply { kind = CanvasElementKind.Panel; width = 200f; height = 300f; visible = window < OPEN })
            }
            repeat(PER_WINDOW) { index ->
                val group = if (index % 4 == 0) node(panel) else panel
                node(group).also {
                    world.add(it, CanvasElement().apply { kind = CanvasElementKind.Text; text = "Label $index"; offsetY = index * 3f; width = 80f; height = 12f })
                }
            }
        }
        repeat(FOLLOWERS) { index ->
            node(root).also {
                world.add(it, CanvasElement().apply { kind = CanvasElementKind.Panel; follow = "Node ${index * 7}"; width = 60f; height = 10f })
            }
        }
    }

    @Test
    fun aMostlyClosedHudCostsWhatItShows() {
        hud()
        val host = ComposeHost()
        fun frame() = host.frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, projector = projector) }
        repeat(WARM_UP) { frame() }
        val time = measureTime { repeat(MEASURED) { frame() } }
        val mean = time / MEASURED
        println("LargeCanvasFrameTest: ${WINDOWS * PER_WINDOW + WINDOWS + FOLLOWERS} elements, $NAMED named nodes, mean frame $mean")
        assertTrue(mean < FRAME_BUDGET, "mean frame $mean over $FRAME_BUDGET")
    }

    private companion object {
        const val NAMED = 15_000
        const val WINDOWS = 40
        const val PER_WINDOW = 85
        const val OPEN = 3
        const val FOLLOWERS = 20
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val WARM_UP = 60
        const val MEASURED = 200
        val FRAME_BUDGET = 100.milliseconds
    }
}
