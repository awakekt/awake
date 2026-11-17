/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind

/**
 * A HUD of [elements] canvas elements, in windows of [PER_WINDOW], for timing the check a host that
 * skips unchanged frames makes each frame (#1512): [read] reads [SceneCanvasChanges] once, as the
 * host does before deciding to skip, over a canvas nothing changed in.
 */
internal class SceneCanvasPollWorkload(val elements: Int) {
    private val world = World()
    private val order = CanvasItemOrder(world)
    private val changes = SceneCanvasChanges(world, order)
    private val kinds = arrayOf(CanvasElementKind.Text, CanvasElementKind.Bar, CanvasElementKind.Button)

    init {
        var window = world.create()
        repeat(elements) { index ->
            if (index % PER_WINDOW == 0) {
                window = world.create()
                world.add(window, Transform())
                world.add(window, CanvasElement().apply { kind = CanvasElementKind.Panel })
            }
            val entity = world.create()
            world.add(entity, Transform(parent = window))
            world.add(entity, CanvasElement().apply { kind = kinds[index % kinds.size]; text = "Item"; offsetY = index % PER_WINDOW * 4f })
        }
        world.syncActiveCanvasElements()
        order.items()
        changes.value
    }

    /** One check, as [SceneCanvasChanges] answers it: the object it hands the host. */
    fun read(): Any = changes.value

    private companion object {
        const val PER_WINDOW = 50
    }
}
