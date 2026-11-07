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
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ModularSkeletalSystem] writes each character's joint palette in place, so a posed crowd allocates
 * nothing per frame, and a headless one poses nothing at all (#1428).
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class ModularSkeletalAllocationProbeTest {
    @Test
    fun aDrawnCrowdAllocatesNothingPerFrame() = assertNoAllocation(ModularSkeletalSystem())

    @Test
    fun aHeadlessCrowdAllocatesNothingPerFrame() = assertNoAllocation(ModularSkeletalSystem(hasRenderer = false))

    private fun assertNoAllocation(system: ModularSkeletalSystem) {
        val world = crowd()
        repeat(WARMUP_FRAMES) { system.update(world, STEP) }

        val start = allocated()
        repeat(MEASURED_FRAMES) { system.update(world, STEP) }

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per frame")
    }

    private fun crowd(): World {
        val world = World()
        val skeleton = Skeleton(
            bones = List(BONES) { Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, if (it + 1 < BONES) listOf(it + 1) else emptyList()) },
            roots = listOf(0),
        )
        val clip = AnimationClip(
            name = null,
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f), 3)),
            ),
        )
        val library = AnimationLibrary(skeleton, mapOf(WALK to clip))
        val skin = Skin(joints = List(BONES) { it }, inverseBindMatrices = List(BONES) { Mat4() })
        repeat(CHARACTERS) {
            val entity = world.create()
            val player = AnimationPlayer(library).apply { play(WALK) }
            world.add(entity, ModularCharacterComponent(skin = skin, skeleton = skeleton))
            world.add(entity, Animator(player, skin))
        }
        return world
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val WALK = "walk"
        const val BONES = 8
        const val CHARACTERS = 64
        const val STEP = 1f / 60f
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600
    }
}
