/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.World

/**
 * Advances named runs once per frame and binds their chosen cell to the sprite renderer.
 *
 * @param isPlaying Whether the host advances simulation; false preserves the current cell and clock.
 */
class SpriteClipSystem(private val isPlaying: () -> Boolean = { true }) : Family2System<Sprite, SpriteClips>(Sprite::class, SpriteClips::class) {
    override fun shouldProcess(world: World, delta: Float): Boolean = isPlaying()

    override fun process(world: World, delta: Float, entity: Entity, first: Sprite, second: SpriteClips) {
        second.advance(delta)
        second.applyTo(first)
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }
}
