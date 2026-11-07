/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.benchmark

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
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Warmup

/**
 * One animation frame of a server-sized crowd (#1442): [entityCount] skinned models, each with a
 * [LocomotionAnimation] choosing its clip and an [Animator] playing it, half of them walking.
 *
 * [drawnFrame] is a host that draws: every model is posed. [headlessFrame] is a host with no
 * renderer: clocks, clip switches and crossfades advance the same, and no model is posed.
 * [locomotion] isolates the clip selection, whose component lookups #1442 resolves once.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class HeadlessAnimationBenchmarks {
    @Param("1244")
    var entityCount: Int = 0

    private val world = World()
    private val transforms = ArrayList<Transform>()
    private val locomotion = LocomotionAnimationSystem()
    private val drawn = AnimationSystem()
    private val headless = AnimationSystem(hasRenderer = false)
    private var step = 0

    /** Spawns the crowd. */
    @Setup
    fun setup() {
        val library = library()
        val skin = Skin(joints = List(BONES) { it }, inverseBindMatrices = List(BONES) { Mat4() })
        val clips = SceneLocomotionAnimation(idle = IDLE, walk = WALK, run = WALK)
        repeat(entityCount) {
            val entity = world.create()
            val transform = Transform()
            transforms += transform
            world.add(entity, transform)
            world.add(entity, LocomotionAnimation(clips))
            val player = AnimationPlayer(library).apply { play(IDLE) }
            world.add(entity, Animator(player, skin))
            world.add(entity, SkinnedPose(player.update(0f).jointPalette(skin)))
        }
    }

    /** Clip selection and a posed frame, as a host that draws runs them. */
    @Benchmark
    fun drawnFrame(): Int {
        walkHalf()
        locomotion.update(world, DELTA)
        drawn.update(world, DELTA)
        return step
    }

    /** Clip selection and an unposed frame, as a headless host runs them. */
    @Benchmark
    fun headlessFrame(): Int {
        walkHalf()
        locomotion.update(world, DELTA)
        headless.update(world, DELTA)
        return step
    }

    /** Clip selection alone. */
    @Benchmark
    fun locomotion(): Int {
        walkHalf()
        locomotion.update(world, DELTA)
        return step
    }

    /** Moves every other model one walking step, so it plays its walk clip and the rest idle. */
    private fun walkHalf() {
        step++
        for (index in transforms.indices step 2) transforms[index].worldMatrix.m03 = step * WALK_STEP
    }

    /** A chain of [BONES] bones with an idle and a walk clip that turn and move each one. */
    private fun library(): AnimationLibrary {
        val bones = List(BONES) { index ->
            Bone(Vec3f(0f, 0.1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, if (index + 1 < BONES) listOf(index + 1) else emptyList())
        }
        val times = FloatArray(KEYS) { it * KEY_SECONDS }
        fun clip(name: String, amplitude: Float) = AnimationClip(
            name,
            List(BONES) { bone ->
                listOf(
                    AnimationChannel(bone, AnimationProperty.Translation, AnimationSampler(times, FloatArray(KEYS * 3) { it * amplitude }, 3)),
                    AnimationChannel(bone, AnimationProperty.Rotation, AnimationSampler(times, FloatArray(KEYS * 4) { if (it % 4 == 3) 1f else 0f }, 4)),
                )
            }.flatten(),
        )
        return AnimationLibrary(Skeleton(bones, roots = listOf(0)), mapOf(IDLE to clip(IDLE, 0.001f), WALK to clip(WALK, 0.01f)))
    }

    private companion object {
        const val IDLE = "idle"
        const val WALK = "walk"
        const val BONES = 40
        const val KEYS = 30
        const val KEY_SECONDS = 1f / 30f
        const val DELTA = 1f / 30f
        const val WALK_STEP = 0.1f
    }
}
