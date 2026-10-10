/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

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
import com.awakekt.awake.render.passes.uniforms.debugLayerColor
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rig of an unbound armature root, a hip one unit up and a spine two up. The skin binds the hip
 * and the spine, so the overlay draws one bone, hip to spine, and passes through the root.
 */
class SkeletonDebugLinesTest {

    @Test
    fun aRestingRigDrawsEachBoneFromItsParentJointWhereTheEntityStands() {
        val world = World()
        rig(world, at = Vec3f(5f, 0f, 0f), palette = REST)

        val bone = skeletonLines(world).single()

        assertNear(Vec3f(5f, 1f, 0f), bone.start, "the hip")
        assertNear(Vec3f(5f, 2f, 0f), bone.end, "the spine")
        assertEquals(debugLayerColor(1), bone.color, "the spine's joint colour, as JointWeights paints it")
    }

    @Test
    fun aPosedRigDrawsItsBonesWhereThePoseMovedThem() {
        val world = World()
        // The spine's palette moves it one unit sideways; the hip stays.
        rig(world, at = Vec3f.ZERO, palette = Mat4().data + Mat4().translate(1f, 0f, 0f).data)

        val bone = skeletonLines(world).single()

        assertNear(Vec3f(0f, 1f, 0f), bone.start, "the hip")
        assertNear(Vec3f(1f, 2f, 0f), bone.end, "the spine, posed; at rest it was over the hip")
    }

    @Test
    fun theOverlayDrawsOnlyWhenItIsOn() {
        val world = World()
        rig(world, at = Vec3f.ZERO, palette = REST)

        assertTrue(debugVisualizationLines(world, NoopRenderer(), WorldDebugSettings(showSkeleton = true), 1f).isNotEmpty())
        assertTrue(debugVisualizationLines(world, NoopRenderer(), WorldDebugSettings(), 1f).isEmpty())
    }

    @Test
    fun aJointHangsFromTheNearestBoundBoneAboveIt() {
        assertEquals(listOf(-1, 0), jointParents(SKIN, SKELETON).toList(), "the hip has no bound parent; the spine hangs from the hip")
    }

    private fun skeletonLines(world: World): List<LineSegment> = ArrayList<LineSegment>().also { appendSkeletonLines(world, it) }

    private fun rig(world: World, at: Vec3f, palette: FloatArray) {
        val entity = world.create()
        world.add(entity, Transform().apply { worldMatrix = Mat4().translate(at.x, at.y, at.z) })
        world.add(entity, Animator(AnimationPlayer(AnimationLibrary(SKELETON, mapOf("idle" to IDLE))), SKIN))
        world.add(entity, SkinnedPose(palette))
    }

    private fun assertNear(expected: Vec3f, actual: Vec3f, what: String) {
        val close = abs(expected.x - actual.x) < EPSILON && abs(expected.y - actual.y) < EPSILON && abs(expected.z - actual.z) < EPSILON
        assertTrue(close, "$what: expected $expected, was $actual")
    }

    private companion object {
        const val EPSILON = 1e-4f
        val ONE = Vec3f(1f, 1f, 1f)
        val SKELETON = Skeleton(
            bones = listOf(
                Bone(Vec3f.ZERO, Quat.IDENTITY, ONE, null, listOf(1), name = "armature"),
                Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, ONE, null, listOf(2), name = "hip"),
                Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, ONE, null, emptyList(), name = "spine"),
            ),
            roots = listOf(0),
        )

        // Each joint's inverse bind matrix undoes where it sits at rest: the hip at y 1, the spine at y 2.
        val SKIN = Skin(joints = listOf(1, 2), inverseBindMatrices = listOf(Mat4().translate(0f, -1f, 0f), Mat4().translate(0f, -2f, 0f)))

        // At rest every joint's palette is its global times its inverse bind: the identity.
        val REST = Mat4().data + Mat4().data

        val IDLE = AnimationClip(
            name = "idle",
            channels = listOf(
                AnimationChannel(
                    targetBone = 0,
                    property = AnimationProperty.Translation,
                    sampler = AnimationSampler(times = floatArrayOf(0f), values = floatArrayOf(0f, 0f, 0f), componentsPerKeyframe = 3),
                ),
            ),
        )
    }
}
