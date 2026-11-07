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
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.ModularSkeletalSystem
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
 * One frame of [ModularSkeletalSystem] over a server-sized crowd of modular characters (#1428).
 *
 * [drawnFrame] poses every character, as a host that draws does. [headlessFrame] is a host with no
 * renderer: each clock advances and no character is posed.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class ModularSkeletalBenchmarks {
    @Param("1244")
    var entityCount: Int = 0

    private val drawnWorld = World()
    private val headlessWorld = World()
    private val drawn = ModularSkeletalSystem()
    private val headless = ModularSkeletalSystem(hasRenderer = false)
    private var frames = 0

    /** Spawns the crowd in both worlds and poses it once, so each character has its palette. */
    @Setup
    fun setup() {
        val library = library()
        val skin = Skin(joints = List(BONES) { it }, inverseBindMatrices = List(BONES) { Mat4() })
        for (world in listOf(drawnWorld, headlessWorld)) {
            repeat(entityCount) {
                val entity = world.create()
                world.add(entity, ModularCharacterComponent(skin = skin, skeleton = library.skeleton))
                world.add(entity, Animator(AnimationPlayer(library).apply { play(WALK) }, skin))
            }
            drawn.update(world, 0f)
        }
    }

    /** A posed frame, as a host that draws runs it. */
    @Benchmark
    fun drawnFrame(): Int {
        drawn.update(drawnWorld, DELTA)
        return ++frames
    }

    /** An unposed frame, as a headless host runs it. */
    @Benchmark
    fun headlessFrame(): Int {
        headless.update(headlessWorld, DELTA)
        return ++frames
    }

    /** A chain of [BONES] bones with a walk clip that turns and moves each one. */
    private fun library(): AnimationLibrary {
        val bones = List(BONES) { index ->
            Bone(Vec3f(0f, 0.1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, if (index + 1 < BONES) listOf(index + 1) else emptyList())
        }
        val times = FloatArray(KEYS) { it * KEY_SECONDS }
        val clip = AnimationClip(
            WALK,
            List(BONES) { bone ->
                listOf(
                    AnimationChannel(bone, AnimationProperty.Translation, AnimationSampler(times, FloatArray(KEYS * 3) { it * 0.01f }, 3)),
                    AnimationChannel(bone, AnimationProperty.Rotation, AnimationSampler(times, FloatArray(KEYS * 4) { if (it % 4 == 3) 1f else 0f }, 4)),
                )
            }.flatten(),
        )
        return AnimationLibrary(Skeleton(bones, roots = listOf(0)), mapOf(WALK to clip))
    }

    private companion object {
        const val WALK = "walk"
        const val BONES = 40
        const val KEYS = 30
        const val KEY_SECONDS = 1f / 30f
        const val DELTA = 1f / 30f
    }
}
