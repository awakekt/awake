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
 *
 * [updateRate] opts a consuming app into posing far models less often (see [AnimationUpdateRate]);
 * the default, [AnimationUpdateRate.EveryFrame], poses every drawn model every frame as before. A
 * held model still advances its clock, so it resumes on time when it is posed again.
 */
class AnimationSystem(
    private val updateRate: AnimationUpdateRate = AnimationUpdateRate.EveryFrame,
) : System {
    override fun update(world: World, delta: Float) {
        world.family<Animator, SkinnedPose>().forEach { _, animator, skinnedPose ->
            val unseen = skinnedPose.culled && !skinnedPose.drawn
            skinnedPose.culled = false
            skinnedPose.drawn = false
            if (unseen) {
                animator.player.advance(delta)
                return@forEach
            }
            // Far models pose less often when a rate is set; the clock still advances so a held pose
            // resumes on time. The cull/draw gating above is untouched: a drawn model is still posed.
            val interval = updateRate.intervalFor(skinnedPose.cameraDistance)
            if (interval > 0f && skinnedPose.nextPoseIn > 0f) {
                skinnedPose.nextPoseIn -= delta
                animator.player.advance(delta)
                return@forEach
            }
            skinnedPose.nextPoseIn = interval
            val pose = animator.player.update(delta)
            val palette = skinnedPose.jointPalette
            // In place when the palette fits: a new one per model per frame is garbage that lives a frame.
            skinnedPose.jointPalette = if (palette.size == animator.skin.joints.size * MATRIX_FLOATS) {
                pose.jointPalette(animator.skin, palette)
            } else {
                pose.jointPalette(animator.skin)
            }
        }
    }
}

private const val MATRIX_FLOATS = 16
