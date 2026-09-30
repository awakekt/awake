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
        val walker = Walker(CLIPS)

        assertEquals("stand", walker.standStill())
        assertEquals("walk", walker.after(dx = 2f * STEP, dy = 0f))
        assertEquals("run", walker.after(dx = 6f * STEP, dy = 0f))
        assertEquals("stand", walker.standStill())
        assertEquals("stand", walker.player.currentClip?.name)
    }

    /** Near the top of a jump it barely moves up or down, and that is still a jump. */
    @Test
    fun aJumpPlaysFromTakeOffUntilItLands() {
        val walker = Walker(CLIPS)
        walker.standStill()

        assertEquals("jump", walker.after(dx = 0f, dy = 5f * STEP), "taking off")
        assertEquals("jump", walker.after(dx = 0f, dy = 0.05f * STEP), "at the top")
        assertEquals("jump", walker.after(dx = 0f, dy = -0.05f * STEP), "past the top")
        assertEquals("jump", walker.after(dx = 0f, dy = -5f * STEP), "falling")
        assertEquals("stand", walker.standStill(), "landed")
    }

    /** Landing on a ledge while still rising: holding its height a moment is landing too. */
    @Test
    fun holdingItsHeightAfterRisingLands() {
        val walker = Walker(CLIPS)
        walker.standStill()

        walker.after(dx = 0f, dy = 5f * STEP)
        assertEquals("jump", walker.after(dx = 2f * STEP, dy = 0f))
        repeat(LANDING_FRAMES) { walker.after(dx = 2f * STEP, dy = 0f) }

        assertEquals("walk", walker.after(dx = 2f * STEP, dy = 0f))
    }

    /** Stepped at 60 Hz and drawn at 120, it stays put every other frame without stopping. */
    @Test
    fun aMoverSteppedAtAFixedRateKeepsItsClipBetweenSteps() {
        val walker = Walker(CLIPS)
        walker.standStill()

        repeat(STEPPED_FRAMES) { frame ->
            val clip = walker.after(dx = if (frame % 2 == 0) 4f * STEP else 0f, dy = 0f)
            if (frame > 0) assertEquals("walk", clip, "frame $frame")
        }
    }

    @Test
    fun aClipTheModelLacksKeepsWhatPlays() {
        val walker = Walker(CLIPS.copy(run = "sprint"))

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
            transform.computeLocalMatrix()
            system.update(world, STEP)
            return locomotion.playing
        }

        /** Stays put long enough to count as standing, and returns the clip chosen. */
        fun standStill(): String? {
            repeat(STILL_FRAMES) { after(dx = 0f, dy = 0f) }
            return locomotion.playing
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val STILL_FRAMES = 30
        const val LANDING_FRAMES = 20
        const val STEPPED_FRAMES = 12
        val CLIPS = SceneLocomotionAnimation(idle = "stand", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f, runAbove = 4f, airborneAbove = 2f)
    }
}
