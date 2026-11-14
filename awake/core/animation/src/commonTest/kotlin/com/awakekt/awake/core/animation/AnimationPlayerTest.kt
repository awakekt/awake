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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun aHeldPoseIsNotComputedAgain() {
        val player = player(clip("attack", x = 7f, duration = 0.5f), clip("loop", x = 3f))
        player.play("attack", playback = AnimationPlayback.Once)
        assertNotNull(player.updateIfChanged(0.25f), "the first pose is computed")
        assertNotNull(player.updateIfChanged(0.5f), "the clip reaches its end: a new pose")
        assertNull(player.updateIfChanged(0.1f), "a finished clip rests on its last frame")
        assertNull(player.updateIfChanged(0.1f))

        player.play("loop")
        assertNotNull(player.updateIfChanged(0.1f), "a new clip is posed")
        assertNotNull(player.updateIfChanged(0.1f), "a playing loop advances every frame")
        player.speed = 0f
        assertNull(player.updateIfChanged(0.1f), "speed 0 holds the pose already computed")
        assertNull(player.updateIfChanged(0.1f))
    }

    @Test
    fun aCrossfadeIsPosedUntilItEnds() {
        val player = player(clip("a", x = 1f), clip("b", x = 5f))
        player.play("a")
        player.speed = 0f
        player.updateIfChanged(0f)
        assertNull(player.updateIfChanged(0.1f))
        player.speed = 1f
        player.crossFadeTo("b", durationSeconds = 0.2f)
        assertNotNull(player.updateIfChanged(0.1f), "blending")
        assertNotNull(player.updateIfChanged(0.15f), "the blend ends")
        assertEquals(5f, player.updateIfChanged(0f)?.jointPalette(skin)?.get(12) ?: 5f, "and lands on the new clip")
    }

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

    @Test
    fun advancingWithoutAPoseKeepsTheClockUpdateWouldHave() {
        val player = player(clip("attack", x = 7f, duration = 2f))
        player.play("attack", playback = AnimationPlayback.Once)

        player.advance(0.5f)
        assertEquals(0.5f, player.time)
        assertTrue(player.isPlaying)

        player.advance(2f)
        assertTrue(player.isFinished)
    }

    @Test
    fun aCrossfadeAdvancedWithoutAPoseEndsOnTime() {
        val player = player(clip("idle", x = 0f), clip("run", x = 10f))
        player.play("idle")
        translationX(player, 0f)
        player.crossFadeTo("run", durationSeconds = 1f)

        player.advance(1f)

        assertEquals(10f, translationX(player, 0f), "the fade ended while unposed")
    }

    @Test
    fun crossedTimelineMarkerDetectsForwardAndLoopingWrapCrossings() {
        assertTrue(crossedTimelineMarker(prevTime = 0.2f, currTime = 0.6f, duration = 1f, looping = false, markerNormalized = 0.5f))
        assertFalse(crossedTimelineMarker(prevTime = 0.6f, currTime = 0.8f, duration = 1f, looping = false, markerNormalized = 0.5f))
        assertFalse(crossedTimelineMarker(prevTime = 0.8f, currTime = 0.2f, duration = 1f, looping = false, markerNormalized = 0.9f))
        assertTrue(crossedTimelineMarker(prevTime = 0.8f, currTime = 0.2f, duration = 1f, looping = true, markerNormalized = 0.9f))
        assertTrue(crossedTimelineMarker(prevTime = 0.8f, currTime = 0.2f, duration = 1f, looping = true, markerNormalized = 0.1f))
        assertFalse(crossedTimelineMarker(prevTime = 0.8f, currTime = 0.2f, duration = 1f, looping = true, markerNormalized = 0.5f))
        assertFalse(crossedTimelineMarker(prevTime = 0f, currTime = 1f, duration = 0f, looping = false, markerNormalized = 0.5f))
    }
}
