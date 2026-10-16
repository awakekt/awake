/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Posing a model every frame, crossfades included, allocates nothing once its palette exists. */
class PoseAllocationTest {
    private val skeleton = Skeleton(
        bones = listOf(
            Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), matrix = null, children = listOf(1)),
            Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), matrix = null, children = emptyList()),
        ),
        roots = listOf(0),
    )
    private val skin = Skin(joints = listOf(0, 1), inverseBindMatrices = listOf(Mat4(), Mat4()))

    private fun clip(name: String, x: Float) = AnimationClip(
        name = name,
        channels = listOf(
            AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 0.5f, 1f), floatArrayOf(0f, 0f, 0f, x, 0f, 0f, 0f, 0f, 0f), 3)),
            AnimationChannel(1, AnimationProperty.Rotation, AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f, 0f, 1f, 0f, 0.7071f, 0f, 0.7071f), 4)),
        ),
    )

    @Test
    fun posingEveryFrameAllocatesNothing() {
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("idle" to clip("idle", 1f), "run" to clip("run", 2f))))
        val palette = FloatArray(skin.joints.size * 16)
        fun frames(count: Int) = repeat(count) { frame ->
            if (frame % 30 == 0) player.crossFadeTo(if (frame % 60 == 0) "run" else "idle", durationSeconds = 0.2f)
            player.update(1f / 60f).jointPalette(skin, palette)
        }
        player.play("idle")
        frames(WARMUP_FRAMES)

        val before = threadBean.currentThreadAllocatedBytes
        frames(MEASURED_FRAMES)
        val perFrame = (threadBean.currentThreadAllocatedBytes - before) / MEASURED_FRAMES

        assertEquals(0L, perFrame, "bytes allocated per posed frame")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_FRAMES = 600
    }
}
