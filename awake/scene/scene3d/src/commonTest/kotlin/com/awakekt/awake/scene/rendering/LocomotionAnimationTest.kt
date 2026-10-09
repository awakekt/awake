/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocomotionAnimationTest {

    @Test
    fun theClipFollowsHowFastTheEntityMoves() {
        val walker = Walker(CLIPS)

        assertEquals("stand", walker.standStill())
        assertEquals("walk", walker.moving(dx = 2f * STEP))
        assertEquals("run", walker.moving(dx = 6f * STEP))
        assertEquals("stand", walker.standStill())
        assertEquals("stand", walker.player.currentClip?.name)
    }

    /** Running up a step slows it for two frames; the run keeps playing through it, not restarting. */
    @Test
    fun aBriefDipKeepsTheRunPlaying() {
        val walker = Walker(CLIPS)
        walker.standStill()
        repeat(SETTLE_FRAMES) { walker.after(dx = 6f * STEP, dy = 0f) }

        val clips = List(2) { walker.after(dx = 3.5f * STEP, dy = 0f) } + List(4) { walker.after(dx = 6f * STEP, dy = 0f) }

        assertEquals(List(6) { "run" }, clips)
    }

    /** Pushing into a wall moves it in fits, as measured in a game at 120 fps: run, walk, stand once each. */
    @Test
    fun aBlockedMoverEasesDownOnce() {
        val walker = Walker(CLIPS)
        walker.standStill()
        repeat(SETTLE_FRAMES) { walker.after(dx = 6f * STEP, dy = 0f) }

        val clips = BLOCKED_SPEEDS.map { walker.after(dx = it * FRAME_120, dy = 0f, step = FRAME_120) }

        assertEquals(listOf("run", "walk", "stand"), clips.distinctConsecutive(), "clips: $clips")
    }

    /** Placed far from the origin, its first frame is not a move from there: walking off, it walks. */
    @Test
    fun anEntityPlacedFarAwayWalksOffAtItsOwnSpeed() {
        val walker = Walker(CLIPS, at = 700f)

        assertEquals("walk", walker.moving(dx = 2f * STEP))
    }

    /** Stopping is still a stop: it stands without walking in place on the way. */
    @Test
    fun stoppingFromARunStandsWithoutWalkingInPlace() {
        val walker = Walker(CLIPS)
        walker.standStill()
        repeat(SETTLE_FRAMES) { walker.after(dx = 6f * STEP, dy = 0f) }

        val clips = List(STILL_FRAMES) { walker.after(dx = 0f, dy = 0f) }

        assertEquals(listOf("run", "stand"), clips.distinctConsecutive(), "clips: $clips")
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

    /** On a character controller, jump means off the ground: a steep climb is still a run. */
    @Test
    fun aGroundContactDecidesTheJump() {
        val walker = Walker(CLIPS, ground = GroundContact(grounded = true))
        walker.standStill()

        assertEquals("run", List(SETTLE_FRAMES) { walker.after(dx = 6f * STEP, dy = 5f * STEP) }.last(), "a steep climb on the ground")
        walker.ground!!.grounded = false
        assertEquals("jump", walker.after(dx = 0f, dy = 0.001f), "off the ground, even hanging still")
        walker.ground.grounded = true
        assertEquals("stand", walker.standStill())
    }

    /** Take-off once, rise, fall, then land once before standing; walking off cuts the landing short. */
    @Test
    fun aJumpPlaysItsPhases() {
        val walker = Walker(PHASES, ground = GroundContact(grounded = true))
        walker.standStill()

        walker.ground!!.grounded = false
        assertEquals("takeoff", walker.after(dx = 0f, dy = 5f * STEP))
        assertTrue(walker.playsOnce, "a take-off plays once")
        repeat(TAKE_OFF_FRAMES) { walker.after(dx = 0f, dy = 5f * STEP) }
        assertEquals("rise", walker.after(dx = 0f, dy = 5f * STEP))
        assertEquals("fall", walker.after(dx = 0f, dy = -5f * STEP))

        walker.ground.grounded = true
        assertEquals("land", walker.after(dx = 0f, dy = -0.001f))
        assertEquals("stand", walker.standStill(), "the landing plays out, then it stands")

        walker.ground.grounded = false
        walker.after(dx = 0f, dy = 5f * STEP)
        walker.ground.grounded = true
        assertEquals("land", walker.after(dx = 0f, dy = -0.001f))
        // Four frames into a landing that lasts 24.
        assertEquals("walk", List(4) { walker.after(dx = 2f * STEP, dy = 0f) }.last(), "walking off cuts the landing short")
    }

    @Test
    fun aClipTheModelLacksKeepsWhatPlays() {
        val walker = Walker(CLIPS.copy(run = "sprint"))

        walker.after(dx = 2f * STEP, dy = 0f)

        assertEquals("walk", walker.after(dx = 6f * STEP, dy = 0f))
    }

    /** One skinned entity with stand, walk, run and jump clips, moved by hand. */
    private class Walker(clips: SceneLocomotionAnimation, val ground: GroundContact? = null, at: Float = 0f) {
        private val world = World()
        private val transform = Transform()
        private val locomotion = LocomotionAnimation(clips)
        private val system = LocomotionAnimationSystem()
        val player = AnimationPlayer(
            AnimationLibrary(
                skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                clips = CLIP_SECONDS.mapValues { (name, seconds) -> timedClip(name, seconds) },
            ),
        )

        init {
            transform.position.x = at
            transform.computeLocalMatrix()
            val entity = world.create()
            ground?.let {
                // Kept by the node that moves, above the model's.
                val mover = world.create()
                world.add(mover, Transform())
                world.add(mover, it)
                transform.parent = mover
            }
            world.add(entity, transform)
            world.add(entity, locomotion)
            world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            system.update(world, STEP)
        }

        /** Moves by ([dx], [dy]) in one frame of [step] seconds, and returns the clip chosen. */
        fun after(dx: Float, dy: Float, step: Float = STEP): String? {
            transform.position.x += dx
            transform.position.y += dy
            transform.computeLocalMatrix()
            system.update(world, step)
            return locomotion.playing
        }

        /** Moves by [dx] a frame for long enough that its speed has settled, and returns the clip chosen. */
        fun moving(dx: Float): String? {
            repeat(SETTLE_FRAMES) { after(dx, dy = 0f) }
            return locomotion.playing
        }

        val playsOnce: Boolean get() = locomotion.playingOnce

        /** Stays put long enough to count as standing, and returns the clip chosen. */
        fun standStill(): String? {
            repeat(STILL_FRAMES) { after(dx = 0f, dy = 0f) }
            return locomotion.playing
        }
    }

    private companion object {
        /** A clip lasting [seconds], keyed on bone 0 at its start and end. */
        fun timedClip(name: String, seconds: Float) = AnimationClip(
            name = name,
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, seconds), FloatArray(6), 3)),
            ),
        )

        val CLIP_SECONDS = mapOf(
            "stand" to 1f, "walk" to 1f, "run" to 1f, "jump" to 1f,
            "takeoff" to 0.25f, "rise" to 0.3f, "fall" to 0.4f, "land" to 0.4f,
        )
        val PHASES = SceneLocomotionAnimation(
            idle = "stand", walk = "walk", run = "run", jump = "rise",
            takeOff = "takeoff", fall = "fall", land = "land", walkAbove = 0.5f,
        )
        const val TAKE_OFF_FRAMES = 16
        const val STEP = 1f / 60f
        const val STILL_FRAMES = 30
        const val LANDING_FRAMES = 20
        const val STEPPED_FRAMES = 12

        /** A third of a second: a speed held this long has settled. */
        const val SETTLE_FRAMES = 20
        const val FRAME_120 = 1f / 120f

        /** Units per second, frame by frame, of a character pushed into a wall, measured at 120 fps. */
        val BLOCKED_SPEEDS = listOf(6f, 6f, 6f, 2.57f, 2.58f, 0f, 0f, 1.27f, 1.27f, 0.61f, 0.61f) + List(19) { 0f }

        fun List<String?>.distinctConsecutive(): List<String?> = filterIndexed { index, clip -> index == 0 || clip != this[index - 1] }
        val CLIPS = SceneLocomotionAnimation(idle = "stand", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f, runAbove = 4f, airborneAbove = 2f)
    }
}
