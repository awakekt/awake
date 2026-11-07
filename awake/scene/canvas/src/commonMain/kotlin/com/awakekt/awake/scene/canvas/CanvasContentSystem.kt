/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Recomposes each block of declarative canvas content, [canvasContent], on a frame where tracked
 * state its last pass read has changed: a `StateFlow` or `Store` it was made from, a
 * `mutableStateOf` it read, or a flow it read through `collectAsState()`. On a frame where none
 * has, it checks each block's reads and writes nothing.
 *
 * It runs the blocks on the frame, as every other system does, so content never changes the world
 * from another thread. Run it before the systems that read the canvas, such as `TransformSystem`;
 * the scene runtime's infrastructure systems include it.
 */
class CanvasContentSystem : System {
    override fun update(world: World, delta: Float) {
        world.family<CanvasContents>().forEach { _, contents -> contents.refresh() }
    }
}

/** The content blocks declared under one root; runtime only, never saved. */
internal class CanvasContents {
    private val blocks = ArrayList<CanvasContent>()

    fun add(content: CanvasContent) {
        blocks += content
    }

    fun remove(content: CanvasContent) {
        blocks -= content
    }

    fun refresh() {
        // By index: a block that recomposes may dispose another under the same root.
        var index = 0
        while (index < blocks.size) {
            blocks[index].refresh()
            index++
        }
    }
}

/** The content blocks under [root], made when the first is declared. */
internal fun World.canvasContents(root: Entity): CanvasContents =
    get<CanvasContents>(root) ?: CanvasContents().also { add(root, it) }
