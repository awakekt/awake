/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.animation

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimationPlayerTest {
    private val skeleton = Skeleton(
        bones = listOf(
            Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), matrix = null, children = emptyList()),
        ),
        roots = listOf(0),
    )

    private val skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))

    private fun clip(name: String, x: Float, duration: Float = 1f): AnimationClip = AnimationClip(
        name = name,
        channels = listOf(
            AnimationChannel(
                targetBone = 0,
                property = AnimationProperty.Translation,
                sampler = AnimationSampler(
                    times = floatArrayOf(0f, duration),
                    values = floatArrayOf(x, 0f, 0f, x, 0f, 0f),
                    componentsPerKeyframe = 3,
                ),
            ),
        ),
    )

    private fun player(vararg clips: AnimationClip): AnimationPlayer = AnimationPlayer(
        AnimationLibrary(skeleton, clips.associateBy { requireNotNull(it.name) }),
    )

    private fun translationX(player: AnimationPlayer, delta: Float): Float =
        player.update(delta).jointPalette(skin)[12]

    @Test
    fun loopingPlaybackWrapsAtClipDuration() {
        val player = player(clip("loop", x = 3f))
        player.play("loop")

        assertEquals(3f, translationX(player, 1.25f))
        assertTrue(player.isPlaying)
    }

    @Test
    fun oneShotStopsOnItsFinalPose() {
        val player = player(clip("attack", x = 7f))
        player.play("attack", playback = AnimationPlayback.Once)

        assertEquals(7f, translationX(player, 2f))
        assertFalse(player.isPlaying)
        assertTrue(player.isFinished)
    }

    @Test
    fun wholeClipPlaybackResetsChannelsNotAuthoredByTheNextClip() {
        val translated = clip("translated", x = 9f)
        val bindPose = AnimationClip(name = "bind", channels = emptyList())
        val player = player(translated, bindPose)
        player.play("translated")
        assertEquals(9f, translationX(player, 0f))

        player.play("bind")
        assertEquals(0f, translationX(player, 0f))
    }

    @Test
    fun crossfadeUsesTheCurrentPoseAsItsOutgoingValue() {
        val player = player(clip("idle", x = 0f), clip("run", x = 10f))
        player.play("idle")
        translationX(player, 0f)

        player.crossFadeTo("run", durationSeconds = 1f)

        assertEquals(5f, translationX(player, 0.5f))
        assertEquals(10f, translationX(player, 0.5f))
    }
}
