/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.TextureAnimation

/**
 * Advances every [TextureClips] by the frame's time and shows its cell, by giving the entity a
 * [TextureAnimation] that holds that cell, which the textured shader draws.
 *
 * The cell is chosen here, on the simulation's clock, not by the shader on the renderer's, so a
 * paused or slowed game holds or slows its sprites, and [TextureClips.isFinished] is something a game
 * can read. The entity's [TextureAnimation] is replaced only when the cell changes, so an entity at
 * 12 frames a second makes 12 small objects a second, not one a frame.
 *
 * Run it once per rendered frame, before the scene is drawn.
 */
class TextureClipSystem : System {
    /** Entities whose first cell has to be added, kept to add after the walk rather than during it. */
    private val unshown = ArrayList<Entity>()

    override fun update(world: World, delta: Float) {
        world.queryEach(TextureClips::class) { entity, clips ->
            clips.advance(delta)
            val cell = clips.frame
            if (clips.shownFrame == cell) return@queryEach
            // Giving an entity a component it lacks changes which entities the query matches, so that
            // waits until the walk is over. Replacing the one it has changes nothing the walk reads.
            if (clips.neverShown && !world.has(entity, TextureAnimation::class)) {
                unshown += entity
            } else {
                world.add(entity, clips.held())
                clips.shownFrame = cell
            }
        }
        if (unshown.isEmpty()) return
        for (entity in unshown) {
            val clips = world.get<TextureClips>(entity) ?: continue
            world.add(entity, clips.held())
            clips.shownFrame = clips.frame
        }
        unshown.clear()
    }
}
