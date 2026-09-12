/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.RenderSystem3D

/** Advances scene animation before rendering reads [SkinnedPose]. Register before
 * [RenderSystem3D] and after any system that selects clips for the current frame. */
class AnimationSystem : System {
    override fun update(world: World, delta: Float) {
        world.family<Animator, SkinnedPose>().forEach { _, animator, skinnedPose ->
            skinnedPose.jointPalette = animator.player.update(delta).jointPalette(animator.skin)
        }
    }
}
