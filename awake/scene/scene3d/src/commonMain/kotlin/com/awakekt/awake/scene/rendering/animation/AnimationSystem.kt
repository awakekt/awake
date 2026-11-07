/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.ecs.get
import com.awakekt.awake.ecs.has
import com.awakekt.awake.scene.rendering.RenderSystem3D

/**
 * Advances scene animation before rendering reads [SkinnedPose]. Register before
 * [RenderSystem3D] and after any system that selects clips for the current frame.
 *
 * A model the renderer culled last frame, and drew nowhere, only advances its clock: its pose is
 * computed again once it is drawn. One drawn after being culled shows its old pose for one frame,
 * which the renderer's culling margin keeps off screen. A model no renderer has looked at, as in a
 * headless world, is posed every frame unless [hasRenderer] is false.
 *
 * [updateRate] opts a consuming app into posing far models less often (see [AnimationUpdateRate]);
 * the default, [AnimationUpdateRate.EveryFrame], poses every drawn model every frame as before. A
 * held model still advances its clock, so it resumes on time when it is posed again.
 *
 * With [hasRenderer] false, for a host that draws nothing such as a game server, every clock, clip
 * switch, crossfade and finished clip advances exactly as it does on a host that draws, so
 * [AnimationPlayer.isFinished] and [AnimationPlayer.activeClipId] read the same frame for frame, but
 * no bone is posed and [SkinnedPose.jointPalette] is left as it was. Two kinds of model are still
 * posed, because something reads their bones without drawing them: the target of an enabled
 * [SocketAttachmentComponent], which [SocketAttachmentSystem] follows, and a model carrying
 * [PosedWithoutRenderer], for a game that reads a bone itself (a hit volume, a muzzle, a grab point).
 * A model posed again after a stretch unposed crossfades from its last posed frame.
 *
 * @param updateRate How often a drawn model far from the camera is posed.
 * @param hasRenderer Whether anything draws the poses; false skips posing all but the models above.
 */
class AnimationSystem(
    private val updateRate: AnimationUpdateRate = AnimationUpdateRate.EveryFrame,
    private val hasRenderer: Boolean,
) : System {
    /** A system for a host that draws, posing as [updateRate] says. */
    constructor(updateRate: AnimationUpdateRate = AnimationUpdateRate.EveryFrame) : this(updateRate, hasRenderer = true)

    override fun update(world: World, delta: Float) {
        if (!hasRenderer) {
            updateHeadless(world, delta)
            return
        }
        world.family(ANIMATOR, SKINNED_POSE).forEach { _, animator, skinnedPose ->
            val unseen = skinnedPose.culled && !skinnedPose.drawn
            skinnedPose.culled = false
            skinnedPose.drawn = false
            if (unseen) {
                animator.player.advance(delta)
                return@forEach
            }
            // Far models pose less often when a rate is set; the clock still advances so a held pose
            // resumes on time. The cull/draw gating above is untouched: a drawn model is still posed.
            // This frame's delta counts toward the hold before testing it, so an interval shorter than
            // a frame poses every frame, and the overshoot carries into the next hold so the pose rate
            // averages 1 / interval instead of rounding each hold up a whole frame (#1423).
            val interval = updateRate.intervalFor(skinnedPose.cameraDistance)
            skinnedPose.nextPoseIn -= delta
            if (interval > 0f && skinnedPose.nextPoseIn > 0f) {
                animator.player.advance(delta)
                return@forEach
            }
            skinnedPose.nextPoseIn = (skinnedPose.nextPoseIn + interval).coerceAtLeast(0f)
            pose(animator, skinnedPose, delta)
        }
    }

    /** Advances every clock; poses only the models whose bones something reads, see the class docs. */
    private fun updateHeadless(world: World, delta: Float) {
        world.family(SOCKET).forEach { _, socket ->
            if (socket.enabled) world.get(socket.targetEntity, SKINNED_POSE)?.boneRead = true
        }
        world.family(ANIMATOR, SKINNED_POSE).forEach { entity, animator, skinnedPose ->
            val read = skinnedPose.boneRead || world.has(entity, POSED_WITHOUT_RENDERER)
            skinnedPose.boneRead = false
            if (read) pose(animator, skinnedPose, delta) else animator.player.advance(delta)
        }
    }

    /** Advances [animator] by [delta] and writes its pose into [skinnedPose]'s palette. */
    private fun pose(animator: Animator, skinnedPose: SkinnedPose, delta: Float) {
        val pose = animator.player.update(delta)
        val palette = skinnedPose.jointPalette
        // In place when the palette fits: a new one per model per frame is garbage that lives a frame.
        skinnedPose.jointPalette = if (palette.size == animator.skin.joints.size * MATRIX_FLOATS) {
            pose.jointPalette(animator.skin, palette)
        } else {
            pose.jointPalette(animator.skin)
        }
    }

    private companion object {
        val ANIMATOR = componentType<Animator>()
        val SKINNED_POSE = componentType<SkinnedPose>()
        val SOCKET = componentType<SocketAttachmentComponent>()
        val POSED_WITHOUT_RENDERER = componentType<PosedWithoutRenderer>()
    }
}

private const val MATRIX_FLOATS = 16
