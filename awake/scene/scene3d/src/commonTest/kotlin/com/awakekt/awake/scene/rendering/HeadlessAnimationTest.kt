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
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.PosedWithoutRenderer
import com.awakekt.awake.scene.rendering.animation.SceneFloatKey
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.SceneVec3Key
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A host with no renderer advances every clip as one that draws does, and poses no bone nothing reads (#1442). */
class HeadlessAnimationTest {
    @Test
    fun clipStateAdvancesFrameForFrameAsOnAHostThatDraws() {
        val drawn = Model(AnimationSystem())
        val headless = Model(AnimationSystem(hasRenderer = false))
        val untouched = headless.pose.jointPalette.copyOf()
        var finishedFrames = 0

        repeat(FRAMES) { frame ->
            // The same game script on both hosts: walk, strike once, then crossfade back mid-strike.
            for (model in listOf(drawn, headless)) {
                if (frame == STRIKE_FRAME) model.player.play(ATTACK, AnimationPlayback.Once)
                if (frame == FADE_FRAME) model.player.crossFadeTo(WALK, durationSeconds = 0.3f)
                if (frame == SECOND_STRIKE_FRAME) model.player.crossFadeTo(ATTACK, 0.2f, AnimationPlayback.Once)
                model.step(dx = if (frame < STRIKE_FRAME) 0.1f else 0.02f)
            }
            assertEquals(drawn.player.activeClipId, headless.player.activeClipId, "activeClipId, frame $frame")
            assertEquals(drawn.player.isFinished, headless.player.isFinished, "isFinished, frame $frame")
            assertEquals(drawn.player.isPlaying, headless.player.isPlaying, "isPlaying, frame $frame")
            assertEquals(drawn.player.time, headless.player.time, "time, frame $frame")
            assertEquals(drawn.locomotion.playing, headless.locomotion.playing, "locomotion clip, frame $frame")
            if (headless.player.isFinished) finishedFrames++
        }

        assertTrue(finishedFrames > 0, "the strike finished on both hosts")
        assertTrue(!drawn.pose.jointPalette.contentEquals(untouched), "the drawn host posed its model")
        assertSame(headless.paletteAtStart, headless.pose.jointPalette, "no new palette on the headless host")
        assertContentEquals(untouched, headless.pose.jointPalette, "and nothing written into the old one")
    }

    @Test
    fun aSocketTargetIsPosedWithoutARenderer() {
        val model = Model(AnimationSystem(hasRenderer = false))
        val weapon = model.world.create()
        model.world.add(weapon, Transform())
        model.world.add(weapon, SocketAttachmentComponent(model.entity, jointIndex = 0))

        model.step(dx = 0f)

        assertEquals(0.5f, model.pose.jointPalette[TRANSLATION_X], "posed, since the socket reads its bone")

        model.world.get<SocketAttachmentComponent>(weapon)!!.enabled = false
        model.step(dx = 0f)

        assertEquals(0.5f, model.pose.jointPalette[TRANSLATION_X], "no longer posed once the socket is off")
    }

    @Test
    fun aModelMarkedPosedWithoutRendererIsPosed() {
        val model = Model(AnimationSystem(hasRenderer = false))
        model.world.add(model.entity, PosedWithoutRenderer)

        model.step(dx = 0f)

        assertEquals(0.5f, model.pose.jointPalette[TRANSLATION_X])
    }

    @Test
    fun keyframesStillMoveTheNodeWithoutARendererButLeaveItsAlpha() {
        val world = World()
        val transform = Transform()
        val material = PbrMaterial(baseColorFactor = Color(1f, 0f, 0f, 1f))
        val entity = world.create()
        world.add(entity, transform)
        world.add(entity, material)
        world.add(
            entity,
            KeyframeAnimation(
                SceneKeyframeAnimation(
                    duration = 2f,
                    position = listOf(SceneVec3Key(0f, SceneVec3(0f, 0f, 0f)), SceneVec3Key(1f, SceneVec3(0f, 2f, 0f))),
                    alpha = listOf(SceneFloatKey(0f, 0f), SceneFloatKey(1f, 1f)),
                ),
            ),
        )

        KeyframeAnimationSystem(hasRenderer = false).update(world, 0.5f)

        assertEquals(1f, transform.position.y, "colliders and sockets follow the moved node")
        assertEquals(1f, material.baseColorFactor.a, "only a renderer reads the alpha")
    }

    /** A one-bone model whose clips move its bone along x, picked by locomotion as it moves. */
    private class Model(private val animation: AnimationSystem) {
        val world = World()
        val entity: Entity = world.create()
        private val transform = Transform()
        val locomotion = LocomotionAnimation(SceneLocomotionAnimation(idle = IDLE, walk = WALK, run = WALK, crossFade = 0.25f))
        private val locomotionSystem = LocomotionAnimationSystem()
        val player = AnimationPlayer(
            AnimationLibrary(
                Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                mapOf(IDLE to clip(1f), WALK to clip(2f), ATTACK to clip(0.6f)),
            ),
        ).apply { play(IDLE) }
        val pose = SkinnedPose(FloatArray(MATRIX_FLOATS))
        val paletteAtStart: FloatArray = pose.jointPalette

        init {
            world.add(entity, transform)
            world.add(entity, locomotion)
            world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            world.add(entity, pose)
        }

        /** One frame: the model moves [dx], locomotion picks its clip and the animation system runs. */
        fun step(dx: Float) {
            transform.worldMatrix.m03 += dx
            locomotionSystem.update(world, STEP)
            animation.update(world, STEP)
        }
    }

    private companion object {
        const val IDLE = "idle"
        const val WALK = "walk"
        const val ATTACK = "attack"
        const val STEP = 0.5f
        const val FRAMES = 24
        const val STRIKE_FRAME = 6
        const val FADE_FRAME = 9
        const val SECOND_STRIKE_FRAME = 15
        const val MATRIX_FLOATS = 16
        const val TRANSLATION_X = 12

        /** A clip lasting [seconds] that moves the bone along x one unit a second. */
        fun clip(seconds: Float) = AnimationClip(
            name = null,
            channels = listOf(
                AnimationChannel(
                    0,
                    AnimationProperty.Translation,
                    AnimationSampler(floatArrayOf(0f, seconds), floatArrayOf(0f, 0f, 0f, seconds, 0f, 0f), 3),
                ),
            ),
        )
    }
}
