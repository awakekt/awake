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

/**
 * A world canvas that is mostly followers, for measuring its frame (#1512): [plates] name plates, a
 * panel with a bar and a line of text each, over nodes [frame] moves when asked, and a minimap view.
 * [parts] leaves parts out, so a breakdown can name what each one costs.
 */
internal class FollowerHud(val plates: Int, val parts: FollowerParts = FollowerParts.All) {
    private val world = World()
    private val movers = ArrayList<Transform>()
    private val matrices = ArrayList<Mat4>()
    private var step = 0

    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null
    }

    init {
        repeat(plates) { index ->
            val mover = world.create()
            val transform = Transform()
            movers += transform
            matrices += Mat4()
            world.add(mover, transform)
            world.add(mover, Name("Mover $index"))
            if (!parts.plates) return@repeat
            val plate = node(null, CanvasElement().apply { kind = CanvasElementKind.Panel; follow = "Mover $index"; width = 70f; height = 20f })
            if (parts.bar) node(plate, CanvasElement().apply { kind = CanvasElementKind.Bar; width = 70f; height = 4f; value = 0.8f })
            if (parts.text) node(plate, CanvasElement().apply { kind = CanvasElementKind.Text; text = "Player $index"; offsetY = 6f })
        }
        if (parts.view) node(null, CanvasElement().apply { kind = CanvasElementKind.View; camera = "minimap"; width = 160f; height = 160f })
        place()
    }

    private fun node(parent: Entity?, element: CanvasElement): Entity =
        world.create().also { world.add(it, Transform(parent = parent)); world.add(it, element) }

    /** Moves every node a step along its row, as a game's movers do between two frames. */
    private fun place() {
        for (index in movers.indices) {
            val x = (index % COLUMNS) * SPACING + (step % SWAY).toFloat()
            movers[index].worldMatrix = matrices[index].setTranslationScale(x, (index / COLUMNS) * SPACING, 0f, 1f)
        }
    }

    /** One frame of [host], with the nodes moved first while [moving]. */
    fun frame(host: ComposeHost, moving: Boolean) {
        if (moving) {
            step++
            place()
        }
        host.frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, projector = projector) }
    }

    private companion object {
        const val COLUMNS = 20
        const val SPACING = 60f
        const val SWAY = 30
        const val WIDTH = 1280
        const val HEIGHT = 720
    }
}

/** Which parts of a [FollowerHud] are made: its plates, each plate's bar and text, and the minimap view. */
internal data class FollowerParts(val plates: Boolean, val bar: Boolean, val text: Boolean, val view: Boolean) {
    companion object {
        val All = FollowerParts(plates = true, bar = true, text = true, view = true)

        /** The cases a heap breakdown measures, each named, from an empty canvas to the whole HUD. */
        val Breakdown: List<Pair<String, FollowerParts>> = listOf(
            "empty" to FollowerParts(plates = false, bar = false, text = false, view = false),
            "view" to FollowerParts(plates = false, bar = false, text = false, view = true),
            "panels" to FollowerParts(plates = true, bar = false, text = false, view = false),
            "panels+bar" to FollowerParts(plates = true, bar = true, text = false, view = false),
            "panels+text" to FollowerParts(plates = true, bar = false, text = true, view = false),
            "all" to All,
        )
    }
}
