/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.RenderSystem3D

/**
 * Advances scene animation before rendering reads [SkinnedPose]. Register before
 * [RenderSystem3D] and after any system that selects clips for the current frame.
 *
 * A model the renderer culled last frame, and drew nowhere, only advances its clock: its pose is
 * computed again once it is drawn. One drawn after being culled shows its old pose for one frame,
 * which the renderer's culling margin keeps off screen. A model no renderer has looked at, as in a
 * headless world, is posed every frame.
 */
class AnimationSystem : System {
    override fun update(world: World, delta: Float) {
        world.family<Animator, SkinnedPose>().forEach { _, animator, skinnedPose ->
            val unseen = skinnedPose.culled && !skinnedPose.drawn
            skinnedPose.culled = false
            skinnedPose.drawn = false
            if (unseen) {
                animator.player.advance(delta)
            } else {
                skinnedPose.jointPalette = animator.player.update(delta).jointPalette(animator.skin)
            }
        }
    }
}
