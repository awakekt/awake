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
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Speed-matched locomotion playback (#1226): scaling the walk/run clip by ground speed over the
 * authored reference speed keeps the stride matched to travel, so the feet plant instead of skating.
 */
class LocomotionSpeedMatchTest {

    @Test
    fun theDefaultPlaysClipsAtTheirAuthoredRate() {
        val walker = Walker(CLIPS)
        walker.glide(speed = 2f, seconds = 0.5f)
        assertEquals(1f, walker.player.speed, "no reference speed set: authored rate, unchanged behavior")
    }

    @Test
    fun walkPlaybackScalesWithGroundSpeed() {
        val reference = 1.4f
        val walker = Walker(CLIPS.copy(walkReferenceSpeed = reference))
        walker.glide(speed = 0.7f, seconds = 0.6f)
        // Half the authored speed should play the walk cycle at half rate.
        assertEquals(0.7f / reference, walker.player.speed, 0.05f)
    }

    @Test
    fun aNonReferencedClipKeepsItsAuthoredRate() {
        val walker = Walker(CLIPS.copy(walkReferenceSpeed = 1.4f))
        walker.standStill(seconds = 0.6f)
        assertEquals(1f, walker.player.speed, "idle has no reference speed: authored rate")
    }

    @Test
    fun strideStaysConstantAcrossSpeedsSoFeetDoNotSkate() {
        val reference = 1.4f
        val cycleSeconds = CLIP_SECONDS.getValue("walk")
        val authoredStride = reference * cycleSeconds

        val slowStride = strideOverOneCycle(speed = 0.7f, reference = reference)
        val fastStride = strideOverOneCycle(speed = 3.0f, reference = reference)

        // The ground covered per animation cycle is the authored stride at any speed -> no skating.
        assertEquals(authoredStride, slowStride, authoredStride * 0.1f, "slow mover's ground stride per cycle")
        assertEquals(authoredStride, fastStride, authoredStride * 0.1f, "fast mover's ground stride per cycle")
    }

    /** Ground distance covered during exactly one walk-clip cycle at the given settled [speed]. */
    private fun strideOverOneCycle(speed: Float, reference: Float): Float {
        val walker = Walker(CLIPS.copy(walkReferenceSpeed = reference))
        walker.glide(speed = speed, seconds = 0.5f) // let the smoothed speed settle
        walker.advanceToClipWrap(speed) // align to a cycle boundary
        val startX = walker.x
        walker.advanceToClipWrap(speed) // one full cycle, wrap to wrap
        return walker.x - startX
    }

    /** One skinned entity moved along +x by hand, driven by [LocomotionAnimationSystem]. */
    private class Walker(clips: SceneLocomotionAnimation) {
        private val world = World()
        private val transform = Transform()
        private val system = LocomotionAnimationSystem()
        private val entity = world.create()
        val player = AnimationPlayer(
            AnimationLibrary(
                skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                clips = CLIP_SECONDS.mapValues { (name, seconds) -> timedClip(name, seconds) },
            ),
        )

        val x: Float get() = transform.position.x

        init {
            transform.computeLocalMatrix()
            world.add(entity, transform)
            world.add(entity, LocomotionAnimation(clips))
            world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            system.update(world, STEP)
        }

        fun step(speed: Float) {
            transform.position.x += speed * STEP
            transform.computeLocalMatrix()
            system.update(world, STEP)
            // AnimationSystem advances the clock in the real pipeline; mirror that so the clip plays.
            player.advance(STEP)
        }

        fun glide(speed: Float, seconds: Float) = repeat((seconds / STEP).toInt()) { step(speed) }

        /** Steps at [speed] until the looping clip wraps once (its playback time decreases). */
        fun advanceToClipWrap(speed: Float) {
            var previous = player.time
            var guard = 0
            while (guard++ < 100_000) {
                step(speed)
                val now = player.time
                if (now < previous) return
                previous = now
            }
        }

        fun standStill(seconds: Float) = repeat((seconds / STEP).toInt()) {
            transform.computeLocalMatrix()
            system.update(world, STEP)
        }
    }

    private companion object {
        const val STEP = 1f / 60f

        val CLIP_SECONDS = mapOf("stand" to 1f, "walk" to 1f, "run" to 1f, "jump" to 1f)

        val CLIPS = SceneLocomotionAnimation(idle = "stand", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f, runAbove = 4f, airborneAbove = 2f)

        fun timedClip(name: String, seconds: Float) = AnimationClip(
            name = name,
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, seconds), FloatArray(6), 3)),
            ),
        )
    }
}
