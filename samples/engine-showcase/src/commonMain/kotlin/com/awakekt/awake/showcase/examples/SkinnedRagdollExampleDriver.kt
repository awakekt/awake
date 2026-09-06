/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.animation.AnimationPose
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.document.Scene
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.examples.ragdoll.Ragdoll
import com.awakekt.awake.showcase.examples.ragdoll.RagdollSkeleton
import com.awakekt.awake.showcase.examples.ragdoll.ragdollFromSkeleton

/**
 * A skinned character going limp: the ragdoll wearing its own mesh.
 *
 * The payoff for the two halves that are otherwise invisible. `ragdollFromSkeleton` shapes the
 * bodies from CesiumMan's own bind pose, and `RagdollSkeleton` writes them back into the pose the
 * joint palette is built from -- so what falls is the character, not a stand-in.
 *
 * The bones below are glTF **node** indices, which is the same thing as a bone index here: the
 * parser builds one bone per node, in order. Twelve of CesiumMan's nineteen joints, because a
 * ragdoll wants the ones that carry weight -- the hands, feet and the second neck joint follow
 * whatever their parent does.
 */
internal object SkinnedRagdollExampleDriver {
    private var ragdoll: Ragdoll? = null
    private var binding: RagdollSkeleton? = null
    private var pose: AnimationPose? = null
    private var ground: BodyHandle? = null
    private var character: Entity? = null
    private var restSeconds = 0f

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "skinned-ragdoll" } ?: return
        val physics = ShowcasePhysics.world ?: return
        character = node.entity
        ground = physics.createBody(
            BoxShape(Vec3f(GROUND_HALF_EXTENT, GROUND_THICKNESS, GROUND_HALF_EXTENT)),
            Vec3f(0f, -GROUND_THICKNESS, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        spawn()
        runtime.world.add(node.entity, SkinnedPose(jointPalette()))
    }

    /** Poses the character from the simulation, then rebuilds the palette the renderer reads. */
    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        val doll = ragdoll ?: return
        val entity = character ?: return
        binding?.apply(doll, requireNotNull(pose))
        runtime.world.get<SkinnedPose>(entity)?.jointPalette = jointPalette()
        restSeconds = if (doll.isAtRest) restSeconds + delta else 0f
        if (restSeconds > REST_SECONDS_BEFORE_RESPAWN) spawn()
    }

    fun detach() {
        ragdoll?.dispose()
        ragdoll = null
        binding = null
        pose = null
        ground?.let { ShowcasePhysics.world?.destroyBody(it) }
        ground = null
        character = null
        restSeconds = 0f
    }

    private fun jointPalette() = requireNotNull(pose).jointPalette(SkinnedExampleDriver.skin())

    /** Drops a fresh figure, replacing whatever was there. */
    private fun spawn() {
        val physics = ShowcasePhysics.world ?: return
        ragdoll?.dispose()
        restSeconds = 0f
        val skeleton = SkinnedExampleDriver.skeleton()
        val rig = ragdollFromSkeleton(
            skeleton,
            bones = RAGDOLL_BONES,
            radius = LIMB_RADIUS,
            // The whole figure lifted, so it has somewhere to fall from. Its own space, so the
            // character's transform stays at the origin and the bones carry the drop.
            origin = Vec3f(0f, DROP_HEIGHT, 0f),
        )
        ragdoll = Ragdoll(physics, rig.limbs, rig.joints)
        binding = RagdollSkeleton(skeleton, rig)
        pose = AnimationPose(skeleton)
    }

    /** Drops a figure without a scene, so a test can exercise the simulation half on its own. */
    internal fun spawnForTest() = spawn()

    /** Poses the skeleton from the simulation and hands back what a renderer would read. */
    internal fun poseForTest(): AnimationPose? {
        val doll = ragdoll ?: return null
        binding?.apply(doll, requireNotNull(pose))
        return pose
    }

    /**
     * CesiumMan's load-bearing joints, as glTF node indices.
     *
     * Pelvis, two spine joints and the neck; then an upper and lower bone per arm and per leg. The
     * hands and feet are left out deliberately: a ragdoll gains nothing from simulating them, and
     * `ragdollFromSkeleton` lets an undriven bone follow its parent.
     */
    private val RAGDOLL_BONES = intArrayOf(
        3, // Skeleton_torso_joint_1 -- the pelvis, and the root of the ragdoll
        12, // Skeleton_torso_joint_2
        13, // torso_joint_3 -- the chest, where the arms hang from
        20, // Skeleton_neck_joint_1
        14, // Skeleton_arm_joint_R -- right upper arm
        15, // Skeleton_arm_joint_R__2_ -- right forearm
        17, // Skeleton_arm_joint_L__4_ -- left upper arm
        18, // Skeleton_arm_joint_L__3_ -- left forearm
        4, // leg_joint_R_1 -- right thigh
        5, // leg_joint_R_2 -- right shin
        8, // leg_joint_L_1 -- left thigh
        9, // leg_joint_L_2 -- left shin
    )

    private const val LIMB_RADIUS = 0.05f
    private const val DROP_HEIGHT = 1.2f
    private const val GROUND_HALF_EXTENT = 8f
    private const val GROUND_THICKNESS = 0.5f
    private const val REST_SECONDS_BEFORE_RESPAWN = 2f
}
