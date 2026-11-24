/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayback
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
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.animation.AnimationUpdateRate
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.ModularSkeletalSystem
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.DetailPin
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ModularSkeletalSystem] matches [com.awakekt.awake.scene.rendering.animation.AnimationSystem]:
 * off-screen characters keep their clock without posing, distant characters obey
 * [AnimationUpdateRate] (except [DetailPin] targets), and held poses skip redundant recomputes.
 */
class ModularSkeletalCullingAndRateTest {
    private val culling = SceneCullingCompiler(ClipSpace.WebGpu)
    private val collector = SceneDrawCollector(culling)
    private val lookingAt = camera(center = Vec3f.ZERO)
    private val lookingAway = camera(center = Vec3f(0f, 0f, 10f))

    @Test
    fun aCulledModularCharacterAdvancesItsClockWithoutPosing() {
        val world = World()
        val (entity, player) = modularCharacter(world)
        val system = ModularSkeletalSystem()

        collect(world, lookingAway)
        system.update(world, delta = 0.5f)
        assertEquals(0f, pose(world, entity).jointPalette[TRANSLATION_X], "culled character must not pose")
        assertEquals(0.5f, player.time, "culled character must still advance its animation clock")

        collect(world, lookingAt)
        system.update(world, delta = 0.5f)
        assertEquals(1f, pose(world, entity).jointPalette[TRANSLATION_X], "visible character resumes at current clock")
    }

    @Test
    fun distanceUpdateRateThrottlesFarModularCharactersUnlessPinned() {
        val world = World()
        val (far, farPlayer) = modularCharacter(world, z = -40f)
        val (pinned, pinnedPlayer) = modularCharacter(world, z = -40f)
        world.add(pinned, DetailPin)
        TransformSystem().update(world, 0f)

        val rate = AnimationUpdateRate.byDistance(near = 10f, far = 30f, farInterval = 0.2f)
        val system = ModularSkeletalSystem(rate)

        // First frame poses both (nextPoseIn starts at 0) and schedules a 0.2s hold for the unpinned one.
        collect(world, lookingAt)
        system.update(world, delta = 0.05f)
        assertEquals(0.05f, pose(world, far).jointPalette[TRANSLATION_X], 1e-5f)
        assertEquals(0.05f, pose(world, pinned).jointPalette[TRANSLATION_X], 1e-5f)

        // Second frame within the 0.2s hold: far character advances clock only, pinned poses every frame.
        collect(world, lookingAt)
        system.update(world, delta = 0.05f)
        assertEquals(0.10f, farPlayer.time, 1e-5f)
        assertEquals(0.05f, pose(world, far).jointPalette[TRANSLATION_X], 1e-5f, "far character holds pose during interval")
        assertEquals(0.10f, pinnedPlayer.time, 1e-5f)
        assertEquals(0.10f, pose(world, pinned).jointPalette[TRANSLATION_X], 1e-5f, "pinned character poses every frame")

        // After enough time passes (> 0.2s total since first pose), far character updates to current clock.
        collect(world, lookingAt)
        system.update(world, delta = 0.20f)
        assertEquals(0.30f, pose(world, far).jointPalette[TRANSLATION_X], 1e-5f)
    }

    @Test
    fun heldPoseSkipsRedundantJointPaletteWrites() {
        val world = World()
        val (entity, player) = modularCharacter(world)
        player.speed = 0f
        val system = ModularSkeletalSystem()

        // First update poses once at t = 0.
        system.update(world, delta = 0.5f)
        val palette = pose(world, entity).jointPalette
        // Sentinel write proves a second update with unchanged clip/time does not overwrite the palette.
        palette[TRANSLATION_X] = 99f
        system.update(world, delta = 0.5f)
        assertEquals(99f, palette[TRANSLATION_X], "held pose must skip redundant jointPalette recomputation")
    }

    @Test
    fun multiViewCulledAndDrawnCharacterStillPoses() {
        val world = World()
        val (entity, _) = modularCharacter(world)
        val system = ModularSkeletalSystem()

        // View 1 culls the character, View 2 draws it in the same frame: culled=true AND drawn=true must still pose.
        collect(world, lookingAway)
        collect(world, lookingAt)
        system.update(world, delta = 0.5f)
        assertEquals(0.5f, pose(world, entity).jointPalette[TRANSLATION_X], 1e-5f, "character drawn in any view must pose")
    }

    @Test
    fun culledCharacterFinishesOnceClipOnTimeAndPosesAtEndWhenVisible() {
        val world = World()
        val (entity, player) = modularCharacter(world)
        player.play("walk", playback = AnimationPlayback.Once)
        val system = ModularSkeletalSystem()

        // Culled for the entire 4s duration: clip finishes off-screen without writing the palette.
        collect(world, lookingAway)
        system.update(world, delta = 4.5f)
        assertEquals(true, player.isFinished, "Once clip must finish on time while culled")
        assertEquals(0f, pose(world, entity).jointPalette[TRANSLATION_X], "culled character must not pose")

        // Enters view after finishing: first visible frame must pose at the clip's end translation (4f).
        collect(world, lookingAt)
        system.update(world, delta = 0.016f)
        assertEquals(4f, pose(world, entity).jointPalette[TRANSLATION_X], 1e-5f, "newly visible finished character must pose at clip end")
    }

    private fun modularCharacter(world: World, z: Float = 0f): Pair<Entity, AnimationPlayer> {
        val skeleton = Skeleton(
            bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())),
            roots = listOf(0),
        )
        val clip = AnimationClip(
            name = "walk",
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 4f), floatArrayOf(0f, 0f, 0f, 4f, 0f, 0f), 3)),
            ),
        )
        val skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("walk" to clip))).apply { play("walk") }
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(0f, 0f, z)))
        val character = ModularCharacterComponent(skin = skin, skeleton = skeleton)
        character.equip("body", skinnedMesh(), dummyMaterial())
        world.add(entity, character)
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        world.add(entity, Animator(player, skin))
        world.add(entity, SkinnedPose(FloatArray(MATRIX_FLOATS)))
        return entity to player
    }

    private fun pose(world: World, entity: Entity) = requireNotNull(world.get<SkinnedPose>(entity))

    private fun collect(world: World, camera: Camera) {
        collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
    }

    private fun camera(center: Vec3f) =
        Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = center, fovYRadians = 1f, near = 0.1f, far = 200f))

    private fun skinnedMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUvSkin
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun dummyMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val MATRIX_FLOATS = 16
        const val TRANSLATION_X = 12
    }
}
