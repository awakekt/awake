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
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

/** A model the renderer culled keeps its clock but is not posed until it is drawn again. */
class UnseenPoseTest {
    private val culling = SceneCullingCompiler(ClipSpace.WebGpu)
    private val collector = SceneDrawCollector(culling)
    private val lookingAt = camera(center = Vec3f.ZERO)
    private val lookingAway = camera(center = Vec3f(0f, 0f, 10f))

    @Test
    fun aCulledModelIsNotPosedButItsClipPlaysOn() {
        val world = World()
        val (model, player) = model(world)
        collect(world, lookingAway)

        AnimationSystem().update(world, delta = 0.5f)

        assertEquals(0f, pose(world, model).jointPalette[12], "not posed")
        assertEquals(0.5f, player.time)

        collect(world, lookingAt)
        AnimationSystem().update(world, delta = 0.5f)

        assertEquals(1f, pose(world, model).jointPalette[12], "posed at the clock it kept")
    }

    @Test
    fun aModelOneViewDrawsIsPosedWhateverAnotherCulled() {
        val world = World()
        val (model, _) = model(world)
        collect(world, lookingAt)
        collect(world, lookingAway)

        AnimationSystem().update(world, delta = 0.5f)

        assertEquals(0.5f, pose(world, model).jointPalette[12])
    }

    @Test
    fun aModelNoRendererLookedAtIsPosed() {
        val world = World()
        val (model, _) = model(world)

        AnimationSystem().update(world, delta = 0.5f)

        assertEquals(0.5f, pose(world, model).jointPalette[12])
    }

    /** A one-bone model at the origin whose clip moves it from x 0 to 2 over 2 s. */
    private fun model(world: World): Pair<Entity, AnimationPlayer> {
        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        val clip = AnimationClip(
            name = "walk",
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 2f), floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f), 3)),
            ),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("walk" to clip))).apply { play("walk") }
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(skinnedMesh(), material()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
        world.add(entity, SkinnedPose(FloatArray(16)))
        return entity to player
    }

    private fun pose(world: World, entity: Entity) = requireNotNull(world.get<SkinnedPose>(entity))

    private fun collect(world: World, camera: Camera) {
        collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
    }

    private fun camera(center: Vec3f) =
        Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = center, fovYRadians = 1f, near = 0.1f, far = 100f))

    private fun skinnedMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUvSkin
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun material(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
