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
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
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
