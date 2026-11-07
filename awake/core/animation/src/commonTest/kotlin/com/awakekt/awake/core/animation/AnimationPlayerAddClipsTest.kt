/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnimationPlayerAddClipsTest {
    private fun bone(name: String) = Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList(), name)

    private val skeleton = Skeleton(bones = listOf(bone("hips"), bone("hand")), roots = listOf(0, 1))
    private val hipsSkin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))

    private fun clip(name: String, x: Float, bone: Int = 0, duration: Float = 4f) = AnimationClip(
        name = name,
        channels = listOf(
            AnimationChannel(
                targetBone = bone,
                property = AnimationProperty.Translation,
                sampler = AnimationSampler(floatArrayOf(0f, duration), floatArrayOf(x, 0f, 0f, x, 0f, 0f), 3),
            ),
        ),
    )

    private fun hipsX(player: AnimationPlayer, delta: Float): Float = player.update(delta).jointPalette(hipsSkin)[12]

    @Test
    fun aClipAddedWhileBlendingPlaysNextWithoutAPop() {
        val clips = mapOf("idle" to clip("idle", 0f), "walk" to clip("walk", 10f))
        val player = AnimationPlayer(AnimationLibrary(skeleton, clips))
        player.play("idle")
        player.crossFadeTo("walk", durationSeconds = 1f, playback = AnimationPlayback.Once)
        assertEquals(2.5f, hipsX(player, 0.25f), 1e-4f)

        val timeBefore = player.time
        player.addClips(mapOf("run" to clip("run", 20f)))

        assertEquals(timeBefore, player.time)
        assertEquals("walk", player.activeClipId)
        assertEquals(AnimationPlayback.Once, player.playback)
        assertTrue(player.isPlaying)
        // The blend in progress carries on across the add instead of snapping to either clip.
        assertEquals(5f, hipsX(player, 0.25f), 1e-4f)

        // The added clip fades in from the pose on screen: no jump on its first frame.
        player.crossFadeTo("run", durationSeconds = 0.5f)
        assertEquals(5f, hipsX(player, 0f), 1e-4f)
        assertEquals(12.5f, hipsX(player, 0.25f), 1e-4f)
        assertEquals(20f, hipsX(player, 0.25f), 1e-4f)
        assertEquals(AnimationPlayback.Loop, player.playback)
    }

    @Test
    fun addingTheSameClipAgainIsANoOpButADifferentClipUnderAHeldIdIsRejected() {
        val idle = clip("idle", 0f)
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("idle" to idle)))
        player.addClips(mapOf("idle" to idle))
        assertSame(idle, player.clipEntries["idle"])

        assertFailsWith<IllegalArgumentException> { player.addClips(mapOf("idle" to clip("idle", 1f))) }
    }

    @Test
    fun anAddedClipMustTargetThePlayersSkeleton() {
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("idle" to clip("idle", 0f))))
        assertFailsWith<IllegalArgumentException> { player.addClips(mapOf("bad" to clip("bad", 1f, bone = 5))) }
    }

    @Test
    fun retargetMovesChannelsToTheSameNamedJointAndDropsUnmatchedOnes() {
        val source = Skeleton(bones = listOf(bone("hand"), bone("tail"), bone("hips")), roots = listOf(0, 1, 2))
        val authored = AnimationClip(
            name = "wave",
            channels = listOf(1f, 2f, 3f).mapIndexed { bone, x -> clip("part", x, bone = bone).channels[0] },
        )

        val retargeted = authored.retarget(from = source, to = skeleton)

        assertEquals("wave", retargeted.name)
        assertEquals(listOf(1, 0), retargeted.channels.map { it.targetBone })
        assertEquals(listOf(1f, 3f), retargeted.channels.map { it.sampler.values[0] })
        assertSame(authored, authored.retarget(source, source))
    }
}
