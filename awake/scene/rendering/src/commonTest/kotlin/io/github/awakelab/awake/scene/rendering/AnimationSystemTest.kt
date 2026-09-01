/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.animation.AnimationChannel
import io.github.awakelab.awake.core.animation.AnimationClip
import io.github.awakelab.awake.core.animation.AnimationLibrary
import io.github.awakelab.awake.core.animation.AnimationPlayer
import io.github.awakelab.awake.core.animation.AnimationProperty
import io.github.awakelab.awake.core.animation.AnimationSampler
import io.github.awakelab.awake.core.animation.Bone
import io.github.awakelab.awake.core.animation.Skeleton
import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.rendering.animation.Animator
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose
import io.github.awakelab.awake.scene.rendering.animation.AnimationSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimationSystemTest {
    @Test
    fun updatesTheRendererFacingJointPalette() {
        val skeleton = Skeleton(
            bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())),
            roots = listOf(0),
        )
        val clip = AnimationClip(
            name = "move",
            channels = listOf(
                AnimationChannel(
                    targetBone = 0,
                    property = AnimationProperty.Translation,
                    sampler = AnimationSampler(
                        times = floatArrayOf(0f),
                        values = floatArrayOf(4f, 0f, 0f),
                        componentsPerKeyframe = 3,
                    ),
                ),
            ),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("move" to clip))).apply {
            play("move")
        }
        val world = World()
        val entity = world.create()
        val skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))
        world.add(entity, Animator(player, skin))
        world.add(entity, SkinnedPose(FloatArray(16)))

        AnimationSystem().update(world, delta = 0f)

        assertEquals(4f, requireNotNull(world.get<SkinnedPose>(entity)).jointPalette[12])
    }
}
