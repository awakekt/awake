/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.animation

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.rendering.RenderSystem

/** Advances scene animation before rendering reads [SkinnedPose]. Register before
 * [RenderSystem] and after any system that selects clips for the current frame. */
class AnimationSystem : System {
    override fun update(world: World, delta: Float) {
        world.family<Animator, SkinnedPose>().forEach { _, animator, skinnedPose ->
            skinnedPose.jointPalette = animator.player.update(delta).jointPalette(animator.skin)
        }
    }
}
