/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A headless plan advances skinned clips as a drawn one does but poses no bone (#1442), as it leaves
 * out canvas actions (#1430) and particles.
 */
class AnimationPlanTest {
    @Test
    fun aDrawnPlanPosesItsModels() {
        val (player, pose) = runAnimation(hasRenderer = true)

        assertEquals(0.5f, player.time)
        assertEquals(0.5f, pose.jointPalette[TRANSLATION_X])
    }

    @Test
    fun aHeadlessPlanAdvancesTheClipButPosesNoBone() {
        val (player, pose) = runAnimation(hasRenderer = false)

        assertEquals(0.5f, player.time, "the clip advances as on a drawn host")
        assertEquals(0f, pose.jointPalette[TRANSLATION_X], "no bone is posed")
    }

    /** Plans the skinned animation for a host that does or doesn't draw, and runs it 0.5 s on a one-bone model. */
    private fun runAnimation(hasRenderer: Boolean): Pair<AnimationPlayer, SkinnedPose> {
        val plan = SceneSystemPlan(hasPhysics = false, hasRenderer = hasRenderer)
        SkinnedAnimationCapability.plan(SceneDocument(), plan)
        val services = SceneHostServices.headless(input = { GameplayInput(Input().currentSnapshot, InputOwnership()) })
        val system = plan.specs.single { it.name == "animation" }.create(services)

        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        val clip = AnimationClip(
            "walk",
            listOf(AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 2f), floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f), 3))),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("walk" to clip))).apply { play("walk") }
        val pose = SkinnedPose(FloatArray(MATRIX_FLOATS))
        val world = World()
        val model = world.create()
        world.add(model, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
        world.add(model, pose)

        system.update(world, 0.5f)
        return player to pose
    }

    private companion object {
        const val MATRIX_FLOATS = 16
        const val TRANSLATION_X = 12
    }
}
