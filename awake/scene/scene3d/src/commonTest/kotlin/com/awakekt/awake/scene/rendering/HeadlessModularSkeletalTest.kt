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
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.ModularSkeletalSystem
import com.awakekt.awake.scene.rendering.animation.PosedWithoutRenderer
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/** A modular character on a host with no renderer advances its clip and poses no bone nothing reads (#1428). */
class HeadlessModularSkeletalTest {
    @Test
    fun aDrawnCharacterIsPosedIntoItsOwnPalette() {
        val character = Character(ModularSkeletalSystem())
        val palette = character.pose.jointPalette

        repeat(3) { character.step() }

        assertSame(palette, character.pose.jointPalette, "written in place, no palette per frame")
        assertEquals(1.5f, palette[TRANSLATION_X])
    }

    @Test
    fun aHeadlessCharacterAdvancesItsClockButIsNotPosed() {
        val drawn = Character(ModularSkeletalSystem())
        val headless = Character(ModularSkeletalSystem(hasRenderer = false))

        repeat(3) {
            drawn.step()
            headless.step()
            assertEquals(drawn.player.time, headless.player.time, "the clock advances the same")
        }

        assertEquals(0f, headless.pose.jointPalette[TRANSLATION_X], "no bone posed")
    }

    @Test
    fun aSocketTargetIsPosedWithoutARenderer() {
        val character = Character(ModularSkeletalSystem(hasRenderer = false))
        val weapon = character.world.create()
        character.world.add(weapon, Transform())
        character.world.add(weapon, SocketAttachmentComponent(character.entity, jointIndex = 0))

        character.step()
        assertEquals(0.5f, character.pose.jointPalette[TRANSLATION_X], "posed, since the socket reads its bone")

        character.world.get<SocketAttachmentComponent>(weapon)!!.enabled = false
        character.step()
        assertEquals(0.5f, character.pose.jointPalette[TRANSLATION_X], "no longer posed once the socket is off")
    }

    @Test
    fun aCharacterMarkedPosedWithoutRendererIsPosed() {
        val character = Character(ModularSkeletalSystem(hasRenderer = false))
        character.world.add(character.entity, PosedWithoutRenderer)

        character.step()

        assertEquals(0.5f, character.pose.jointPalette[TRANSLATION_X])
    }

    @Test
    fun aHeadlessCharacterWithNoPoseYetIsPosedOnceSoItHasOne() {
        val character = Character(ModularSkeletalSystem(hasRenderer = false), withPose = false)

        character.step()
        val pose = assertNotNull(character.world.get<SkinnedPose>(character.entity))
        assertEquals(0.5f, pose.jointPalette[TRANSLATION_X])

        character.step()
        assertEquals(0.5f, pose.jointPalette[TRANSLATION_X], "then left unposed")
    }

    /** A one-bone modular character whose clip moves its bone along x one unit a second. */
    private class Character(private val system: ModularSkeletalSystem, withPose: Boolean = true) {
        val world = World()
        val entity = world.create()
        val player = AnimationPlayer(
            AnimationLibrary(
                Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                mapOf(WALK to clip()),
            ),
        ).apply { play(WALK) }
        private val skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))

        init {
            world.add(entity, Transform())
            world.add(entity, ModularCharacterComponent(skin = skin))
            world.add(entity, Animator(player, skin))
            if (withPose) world.add(entity, SkinnedPose(FloatArray(MATRIX_FLOATS)))
        }

        val pose: SkinnedPose get() = world.get<SkinnedPose>(entity)!!

        fun step() = system.update(world, STEP)
    }

    private companion object {
        const val WALK = "walk"
        const val STEP = 0.5f
        const val MATRIX_FLOATS = 16
        const val TRANSLATION_X = 12

        fun clip() = AnimationClip(
            name = null,
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 4f), floatArrayOf(0f, 0f, 0f, 4f, 0f, 0f), 3)),
            ),
        )
    }
}
