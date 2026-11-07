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
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.AnimationUpdateRate
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opt-in distance update rate (#1223): far models are posed less often, the default poses every
 * frame unchanged, and a throttled model keeps the same clock so playback timing does not drift.
 */
class AnimationUpdateRateSystemTest {
    private val delta = 1f / 60f

    @Test
    fun theDefaultRatePosesEveryDrawnFrame() {
        assertEquals(60, recomputes(AnimationUpdateRate.EveryFrame, distance = 500f, frames = 60))
    }

    @Test
    fun aFarModelIsPosedAnOrderOfMagnitudeLessOften() {
        val rate = AnimationUpdateRate.byDistance(near = 10f, far = 50f, farInterval = 0.5f)
        val everyFrame = recomputes(AnimationUpdateRate.EveryFrame, distance = 100f, frames = 60)
        val throttled = recomputes(rate, distance = 100f, frames = 60)

        assertEquals(60, everyFrame, "control: full rate recomputes every frame")
        // 1 s of frames, a pose held ~0.5 s: a couple of recomputes, down from 60 -- this is the
        // jointPalette work #1223 is about, cut by more than 10x for a far crowd.
        assertTrue(throttled <= 3, "far model recomputed $throttled times, expected <= 3")
        assertTrue(everyFrame >= throttled * 10, "expected >= 10x fewer recomputes, was $everyFrame vs $throttled")
    }

    @Test
    fun aNearModelIsStillPosedEveryFrame() {
        val rate = AnimationUpdateRate.byDistance(near = 10f, far = 50f, farInterval = 0.5f)
        assertEquals(30, recomputes(rate, distance = 5f, frames = 30), "within near: posed every frame")
    }

    @Test
    fun throttlingKeepsTheSameClockSoPlaybackResumesOnTime() {
        val frames = 40
        val atFullRate = clockAfter(AnimationUpdateRate.EveryFrame, distance = 1f, frames)
        val throttled = clockAfter(AnimationUpdateRate.byDistance(0f, 10f, 0.5f), distance = 100f, frames)

        assertEquals(frames * delta, atFullRate, 1e-4f, "control advances one delta per frame")
        assertEquals(atFullRate, throttled, 1e-4f, "a held model advanced its clock identically")
    }

    @Test
    fun aHeldPoseIsStableBetweenRecomputes() {
        val (world, entity) = scene()
        val system = AnimationSystem(AnimationUpdateRate.byDistance(near = 0f, far = 0f, farInterval = 10f))
        val pose = requireNotNull(world.get<SkinnedPose>(entity))

        drawn(pose, distance = 100f); system.update(world, delta)
        val afterFirstPose = pose.jointPalette[12]
        repeat(5) { drawn(pose, distance = 100f); system.update(world, delta) }

        assertTrue(afterFirstPose > 0f, "the first drawn frame still poses")
        assertEquals(afterFirstPose, pose.jointPalette[12], "pose held while throttled")
    }

    @Test
    fun anIntervalShorterThanAFramePosesEveryFrame() {
        // #1423: a 1.4 ms hold, just past `near`, held every other frame at 60 fps.
        assertEquals(60, recomputes(fixed(0.0014f), distance = 100f, frames = 60))
    }

    @Test
    fun overOneSecondThePoseCountMatchesOneOverTheInterval() {
        // #1423 measured 20, 12 and 7.5 poses per second for these holds at 60 fps.
        for (interval in floatArrayOf(0.0286f, 0.0571f, 0.1f)) {
            val poses = recomputes(fixed(interval), distance = 100f, frames = 60)
            val expected = 1f / interval
            assertTrue(abs(poses - expected) <= 1f, "a $interval s hold posed $poses times in 1 s, expected ~$expected")
        }
    }

    @Test
    fun theClipClockStaysOnTimeWhileHeld() {
        val interval = 0.1f
        val (world, entity) = scene()
        val system = AnimationSystem(fixed(interval))
        val pose = requireNotNull(world.get<SkinnedPose>(entity))
        val player = requireNotNull(world.get<Animator>(entity)).player
        var maxLag = 0f
        repeat(60) {
            drawn(pose, distance = 100f)
            system.update(world, delta)
            // The clip slides one unit per second, so the shown translation is the clock it was posed at.
            maxLag = maxOf(maxLag, player.time - pose.jointPalette[12])
        }

        assertEquals(60 * delta, player.time, 1e-4f, "the clock advances one delta per frame while held")
        assertTrue(maxLag < interval, "a held pose lagged the clock by $maxLag s, more than its $interval s hold")
    }

    /** A rate holding every model's pose for [interval] seconds, whatever its distance. */
    private fun fixed(interval: Float) = AnimationUpdateRate { interval }

    /** Recomputes over [frames] drawn frames at [distance]: a monotonic clip makes each pose a new palette value. */
    private fun recomputes(rate: AnimationUpdateRate, distance: Float, frames: Int): Int {
        val (world, entity) = scene()
        val system = AnimationSystem(rate)
        val pose = requireNotNull(world.get<SkinnedPose>(entity))
        var previous = Float.NaN
        var count = 0
        repeat(frames) {
            drawn(pose, distance)
            system.update(world, delta)
            val value = pose.jointPalette[12]
            if (value != previous) {
                count++
                previous = value
            }
        }
        return count
    }

    private fun clockAfter(rate: AnimationUpdateRate, distance: Float, frames: Int): Float {
        val (world, entity) = scene()
        val system = AnimationSystem(rate)
        val pose = requireNotNull(world.get<SkinnedPose>(entity))
        val player = requireNotNull(world.get<Animator>(entity)).player
        repeat(frames) {
            drawn(pose, distance)
            system.update(world, delta)
        }
        return player.time
    }

    /** Marks the pose as drawn this frame at [distance], as the collector would before the system runs. */
    private fun drawn(pose: SkinnedPose, distance: Float) {
        pose.drawn = true
        pose.cameraDistance = distance
    }

    /** One skinned entity whose clip slides bone 0 along +x by one unit per second over 100 s (no wrap here). */
    private fun scene(): Pair<World, Entity> {
        val skeleton = Skeleton(
            bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())),
            roots = listOf(0),
        )
        val clip = AnimationClip(
            name = "slide",
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 100f), floatArrayOf(0f, 0f, 0f, 100f, 0f, 0f), 3)),
            ),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("slide" to clip))).apply { play("slide") }
        val world = World()
        val entity = world.create()
        world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
        world.add(entity, SkinnedPose(FloatArray(16)))
        return world to entity
    }
}
