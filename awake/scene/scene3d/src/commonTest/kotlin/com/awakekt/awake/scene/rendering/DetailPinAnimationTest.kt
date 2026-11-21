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
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.scene.rendering.mesh.DetailPin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [DetailPin]ned skinned model is posed every frame beyond the animation band's far distance, where
 * an unpinned one beside it is held (#1805); so is a model socket-attached to a pinned one.
 */
class DetailPinAnimationTest {
    private val delta = 1f / 60f

    /** Poses beyond `far` are held for half a second: about two a second, against 60 at full rate. */
    private val band = AnimationUpdateRate.byDistance(near = 10f, far = 50f, farInterval = 0.5f)

    @Test
    fun aPinnedModelBeyondTheBandIsPosedEveryFrameAndAnUnpinnedOneIsHeld() {
        val world = World()
        val pinned = skinned(world).also { world.add(it, DetailPin) }
        val unpinned = skinned(world)
        val poses = posesOver(world, listOf(pinned, unpinned), frames = 60)

        assertEquals(60, poses.getValue(pinned), "pinned")
        assertTrue(poses.getValue(unpinned) <= 3, "unpinned beside it posed ${poses.getValue(unpinned)} times in 1 s")
    }

    @Test
    fun aModelAttachedToAPinnedOneIsPosedEveryFrame() {
        val world = World()
        val character = skinned(world).also { world.add(it, DetailPin) }
        val mount = skinned(world).also { world.add(it, SocketAttachmentComponent(targetEntity = character)) }
        val poses = posesOver(world, listOf(character, mount), frames = 30)

        assertEquals(30, poses.getValue(mount))
    }

    @Test
    fun aPinRemovedReturnsTheModelToTheBand() {
        val world = World()
        val model = skinned(world).also { world.add(it, DetailPin) }
        posesOver(world, listOf(model), frames = 10)
        world.remove(model, DetailPin::class)

        assertTrue(posesOver(world, listOf(model), frames = 60).getValue(model) <= 3)
    }

    /** How many times each of [models] was posed over [frames] frames drawn 100 m from the camera. */
    private fun posesOver(world: World, models: List<Entity>, frames: Int): Map<Entity, Int> {
        val system = AnimationSystem(band)
        val previous = models.associateWith { Float.NaN }.toMutableMap()
        val counts = models.associateWith { 0 }.toMutableMap()
        repeat(frames) {
            models.forEach { requireNotNull(world.get<SkinnedPose>(it)).apply { drawn = true; cameraDistance = DISTANCE } }
            system.update(world, delta)
            models.forEach { model ->
                val value = requireNotNull(world.get<SkinnedPose>(model)).jointPalette[12]
                if (value != previous[model]) {
                    counts[model] = counts.getValue(model) + 1
                    previous[model] = value
                }
            }
        }
        return counts
    }

    /** A skinned entity whose clip slides bone 0 along +x one unit a second, so every pose is a new value. */
    private fun skinned(world: World): Entity {
        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        val clip = AnimationClip(
            name = "slide",
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 100f), floatArrayOf(0f, 0f, 0f, 100f, 0f, 0f), 3)),
            ),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("slide" to clip))).apply { play("slide") }
        return world.create().also { entity ->
            world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            world.add(entity, SkinnedPose(FloatArray(16)))
        }
    }

    private companion object {
        /** Past the band's 50 m far distance. */
        const val DISTANCE = 100f
    }
}
