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
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.animation.AnimationUpdateRate
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.ModularSkeletalSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.DetailPin
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
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
 * One frame of [ModularSkeletalSystem] over a crowd of modular characters (#1428).
 *
 * - [drawnFrame]: poses every character every frame (full unthrottled baseline).
 * - [visibleCrowdWithRenderFrame] vs [culledCrowdWithRenderFrame]: runs [RenderSystem3D] culling
 *   followed by [ModularSkeletalSystem] on 100% visible vs 75% off-screen crowds.
 * - [distanceThrottledFrame]: all visible characters spread 5m..60m from the camera with opt-in
 *   [AnimationUpdateRate.byDistance] throttling and one [DetailPin] hero.
 * - [heldPoseFrame]: characters holding a static pose (speed 0), skipping redundant palette writes
 *   via [AnimationPlayer.updateIfChanged].
 * - [headlessFrame]: host with no renderer (`hasRenderer = false`).
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
    private val culledWorld = World()
    private val throttledWorld = World()
    private val heldWorld = World()
    private val headlessWorld = World()
    private val drawn = ModularSkeletalSystem()
    private val throttled = ModularSkeletalSystem(AnimationUpdateRate.byDistance(near = 15f, far = 40f, farInterval = 0.1f))
    private val headless = ModularSkeletalSystem(hasRenderer = false)
    private val renderSystem = RenderSystem3D(NoopRenderer(), drawPreparer = NoopPreparer)
    private var frames = 0

    /** Spawns the crowd in every world, runs initial transform/collect/pose so palettes and distances are warm. */
    @Setup
    fun setup() {
        val library = library()
        val skin = Skin(joints = List(BONES) { it }, inverseBindMatrices = List(BONES) { Mat4() })
        val mesh = dummySkinnedMesh()
        val material = dummyMaterial()
        val bounds = Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))
        val camera = Camera(Lens(eye = Vec3f(0f, 2f, 5f), center = Vec3f(0f, 0f, -10f), fovYRadians = 1f, near = 0.1f, far = 200f))
        val transforms = TransformSystem()
        for (world in listOf(drawnWorld, culledWorld, throttledWorld, heldWorld, headlessWorld)) {
            world.add(world.create(), camera)
            repeat(entityCount) { index ->
                val entity = world.create()
                val z = when (world) {
                    culledWorld -> if (index and 3 == 0) -10f else 25f
                    throttledWorld -> -(5f + (index % 56).toFloat())
                    else -> -10f
                }
                world.add(entity, Transform(position = Vec3f(0f, 0f, z)))
                val character = ModularCharacterComponent(skin = skin, skeleton = library.skeleton).apply {
                    equip("body", mesh, material)
                }
                world.add(entity, character)
                world.add(entity, MeshBounds(bounds))
                val player = AnimationPlayer(library).apply {
                    play(WALK)
                    if (world === heldWorld) speed = 0f
                }
                world.add(entity, Animator(player, skin))
                if (world === throttledWorld && index == 0) world.add(entity, DetailPin)
            }
            transforms.update(world, 0f)
            drawn.update(world, 0f)
        }
        // Populate cameraDistance on throttledWorld through the real RenderSystem3D collect path.
        renderSystem.update(throttledWorld, 0f)
        // Warm one held frame so AnimationPlayer.updateIfChanged caches clip/time.
        drawn.update(heldWorld, DELTA)
    }

    /** A posed frame, as a host that draws runs it with every character visible and unthrottled. */
    @Benchmark
    fun drawnFrame(): Int {
        drawn.update(drawnWorld, DELTA)
        return ++frames
    }

    /** Full collect + pose frame with 100% of characters visible inside the frustum. */
    @Benchmark
    fun visibleCrowdWithRenderFrame(): Int {
        renderSystem.update(drawnWorld, 0f)
        drawn.update(drawnWorld, DELTA)
        return ++frames
    }

    /** Full collect + pose frame with 75% of characters behind the camera skipping bone posing. */
    @Benchmark
    fun culledCrowdWithRenderFrame(): Int {
        renderSystem.update(culledWorld, 0f)
        drawn.update(culledWorld, DELTA)
        return ++frames
    }

    /** A visible crowd with distance-based [AnimationUpdateRate] throttling and one pinned hero. */
    @Benchmark
    fun distanceThrottledFrame(): Int {
        throttled.update(throttledWorld, DELTA)
        return ++frames
    }

    /** A crowd holding static poses (speed 0), skipping redundant palette writes. */
    @Benchmark
    fun heldPoseFrame(): Int {
        drawn.update(heldWorld, DELTA)
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

    private fun dummySkinnedMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUvSkin
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun dummyMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private object NoopPreparer : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? = null
        override fun canInstance(format: VertexFormat): Boolean = false
    }

    private companion object {
        const val WALK = "walk"
        const val BONES = 40
        const val KEYS = 30
        const val KEY_SECONDS = 1f / 30f
        const val DELTA = 1f / 30f
    }
}
