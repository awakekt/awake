/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Advances named runs once per frame and binds their chosen cell to the sprite renderer.
 *
 * @param isPlaying Whether the host advances simulation; false preserves the current cell and clock.
 */
class SpriteClipSystem(private val isPlaying: () -> Boolean = { true }) : System {
    override fun update(world: World, delta: Float) {
        if (!isPlaying()) return
        world.queryEach<Sprite, SpriteClips> { _, sprite, clips ->
            clips.advance(delta)
            clips.applyTo(sprite)
        }
    }
}
