/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import kotlin.test.Test
import kotlin.test.assertEquals

class LocomotionAnimationTest {

    @Test
    fun theClipFollowsHowFastTheEntityMoves() {
        val walker = Walker(SceneLocomotionAnimation(idle = "stand", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f, runAbove = 4f, airborneAbove = 2f))

        assertEquals("stand", walker.after(dx = 0f, dy = 0f))
        assertEquals("walk", walker.after(dx = 2f * STEP, dy = 0f))
        assertEquals("run", walker.after(dx = 6f * STEP, dy = 0f))
        assertEquals("jump", walker.after(dx = 6f * STEP, dy = 5f * STEP))
        assertEquals("stand", walker.after(dx = 0f, dy = 0f))
        assertEquals("stand", walker.player.currentClip?.name)
    }

    @Test
    fun aClipTheModelLacksKeepsWhatPlays() {
        val walker = Walker(SceneLocomotionAnimation(idle = "stand", walk = "walk", run = "sprint", walkAbove = 0.5f, runAbove = 4f))

        walker.after(dx = 2f * STEP, dy = 0f)

        assertEquals("walk", walker.after(dx = 6f * STEP, dy = 0f))
    }

    /** One skinned entity with stand, walk, run and jump clips, moved by hand. */
    private class Walker(clips: SceneLocomotionAnimation) {
        private val world = World()
        private val transform = Transform()
        private val locomotion = LocomotionAnimation(clips)
        private val system = LocomotionAnimationSystem()
        val player = AnimationPlayer(
            AnimationLibrary(
                skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                clips = listOf("stand", "walk", "run", "jump").associateWith { AnimationClip(name = it, channels = emptyList()) },
            ),
        )

        init {
            val entity = world.create()
            world.add(entity, transform)
            world.add(entity, locomotion)
            world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            system.update(world, STEP)
        }

        /** Moves by ([dx], [dy]) in one frame, and returns the clip chosen. */
        fun after(dx: Float, dy: Float): String? {
            transform.position.x += dx
            transform.position.y += dy
            system.update(world, STEP)
            return locomotion.playing
        }
    }

    private companion object {
        const val STEP = 1f / 60f
    }
}
