/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

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
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A crowd of animated movers, walking and stopping so locomotion crossfades both ways, allocates
 * nothing a frame in locomotion or animation with escape analysis off (#1555). Kotlin/Wasm has none:
 * a nullable `Entity` in the ground-contact walk boxed once per mover per frame there, 24 B each,
 * 33.6 KB a frame for 1,400 movers.
 */
class LocomotionAllocationProbeTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun aWalkingCrowdAllocatesNothingPerFrame() {
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "this probe needs -XX:-DoEscapeAnalysis")
        val world = World()
        val library = AnimationLibrary(SKELETON, mapOf(IDLE to clip(1f), WALK to clip(WALK_SECONDS)))
        val skin = Skin(joints = List(BONES) { it }, inverseBindMatrices = List(BONES) { Mat4() })
        val transforms = List(MOVERS) { index ->
            val transform = Transform(position = Vec3f(index.toFloat(), 0f, 0f))
            world.create().also {
                world.add(it, transform)
                world.add(it, LocomotionAnimation(SceneLocomotionAnimation(idle = IDLE, walk = WALK, run = WALK, crossFade = CROSS_FADE)))
                world.add(it, Animator(AnimationPlayer(library).apply { play(IDLE) }, skin))
                world.add(it, SkinnedPose(FloatArray(MATRIX_FLOATS * BONES)))
            }
            transform
        }
        val locomotion = LocomotionAnimationSystem()
        val animation = AnimationSystem()
        var frame = 0
        fun step() {
            // A second walking, a second standing.
            if ((frame++ / FRAMES_PER_SECOND) % 2 == 0) transforms.forEach { it.worldMatrix.m03 += STRIDE }
            locomotion.update(world, 1f / FRAMES_PER_SECOND)
            animation.update(world, 1f / FRAMES_PER_SECOND)
        }
        repeat(WARM_UP_FRAMES) { step() }

        val thread = Thread.currentThread().id
        var bytes = 0L
        repeat(MEASURED_FRAMES) {
            // The walkers' own moves are the test's, not the systems'.
            val moving = (frame / FRAMES_PER_SECOND) % 2 == 0
            if (moving) transforms.forEach { it.worldMatrix.m03 += STRIDE }
            frame++
            val before = threads.getThreadAllocatedBytes(thread)
            locomotion.update(world, 1f / FRAMES_PER_SECOND)
            animation.update(world, 1f / FRAMES_PER_SECOND)
            bytes += threads.getThreadAllocatedBytes(thread) - before
        }

        assertEquals(0L, bytes / MEASURED_FRAMES, "bytes per frame for $MOVERS movers")
    }

    private companion object {
        const val MOVERS = 400
        const val BONES = 20
        const val MATRIX_FLOATS = 16
        const val FRAMES_PER_SECOND = 60
        const val WARM_UP_FRAMES = 240
        const val MEASURED_FRAMES = 240
        const val STRIDE = 0.02f
        const val CROSS_FADE = 0.25f
        const val WALK_SECONDS = 0.8f
        const val IDLE = "idle"
        const val WALK = "walk"

        val SKELETON = Skeleton(
            bones = List(BONES) { index ->
                Bone(Vec3f(0f, 0.1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, if (index + 1 < BONES) listOf(index + 1) else emptyList())
            },
            roots = listOf(0),
        )

        /** Every bone moving and turning over [seconds]. */
        fun clip(seconds: Float) = AnimationClip(
            name = null,
            channels = (0 until BONES).flatMap { bone ->
                listOf(
                    AnimationChannel(bone, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, seconds / 2, seconds), FloatArray(9) { 0.01f * it }, 3)),
                    AnimationChannel(
                        bone,
                        AnimationProperty.Rotation,
                        AnimationSampler(floatArrayOf(0f, seconds / 2, seconds), floatArrayOf(0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 0.995f, 0f, 0f, 0f, 1f), 4),
                    ),
                )
            },
        )
    }
}
