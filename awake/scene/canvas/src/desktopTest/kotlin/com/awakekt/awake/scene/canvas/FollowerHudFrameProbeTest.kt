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
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * What a frame of a world canvas costs when it is mostly followers (#1512): [PLATES] name plates, a
 * panel with a bar and a line of text each, over nodes that move every frame or hold still, and a
 * minimap view. Printed, not asserted: the figures compare a host that composes every frame with
 * one that skips unchanged frames, on this branch and on main.
 */
class FollowerHudFrameProbeTest {
    private val world = World()
    private val movers = ArrayList<Transform>()

    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null
    }

    init {
        repeat(PLATES) { index ->
            val mover = world.create()
            val transform = Transform()
            movers += transform
            world.add(mover, transform)
            world.add(mover, Name("Mover $index"))
            val plate = node(null, CanvasElement().apply { kind = CanvasElementKind.Panel; follow = "Mover $index"; width = 70f; height = 20f })
            node(plate, CanvasElement().apply { kind = CanvasElementKind.Bar; width = 70f; height = 4f; value = 0.8f })
            node(plate, CanvasElement().apply { kind = CanvasElementKind.Text; text = "Player $index"; offsetY = 6f })
        }
        node(null, CanvasElement().apply { kind = CanvasElementKind.View; camera = "minimap"; width = 160f; height = 160f })
        place(0)
    }

    private fun node(parent: Entity?, element: CanvasElement): Entity =
        world.create().also { world.add(it, Transform(parent = parent)); world.add(it, element) }

    private fun place(frame: Int) {
        for (index in movers.indices) {
            val x = (index % COLUMNS) * SPACING + (frame % SWAY).toFloat()
            movers[index].worldMatrix = Mat4().translate(x, (index / COLUMNS) * SPACING, 0f)
        }
    }

    @Test
    fun aFollowerHudsFrameIsMeasured() {
        val lines = listOf(false, true).flatMap { skip -> listOf(true, false).map { moving -> measure(skip, moving) } }
        println("FollowerHudFrame: $PLATES plates; ${lines.joinToString("; ")}")
    }

    private fun measure(skip: Boolean, moving: Boolean): String {
        val host = ComposeHost().apply {
            skipUnchangedComposition = skip
            skipUnchangedMeasure = skip
            retainUnchangedDraw = skip
        }
        var frame = 0
        fun frame() {
            if (moving) place(frame++)
            host.frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, projector = projector) }
        }
        repeat(WARM_UP) { frame() }
        var composed = 0
        val bytes = allocated()
        val mark = TimeSource.Monotonic.markNow()
        repeat(MEASURED) {
            frame()
            if (host.frameCounts.composed) composed++
        }
        val micros = mark.elapsedNow().inWholeMicroseconds / MEASURED
        val perFrame = (allocated() - bytes) / MEASURED
        return "skip=$skip moving=$moving: ${micros / MICROS_PER_MILLI} ms, ${perFrame / BYTES_PER_KB} KB a frame, composed $composed of $MEASURED"
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val PLATES = 200
        const val COLUMNS = 20
        const val SPACING = 60f
        const val SWAY = 30
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val WARM_UP = 200
        const val MEASURED = 300
        const val MICROS_PER_MILLI = 1000.0
        const val BYTES_PER_KB = 1024.0
    }
}
