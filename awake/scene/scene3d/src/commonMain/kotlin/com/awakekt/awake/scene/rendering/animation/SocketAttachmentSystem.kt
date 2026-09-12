/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * ECS system driving attached entities to track animated character skeleton joints in world space.
 *
 * Runs each frame to update every entity carrying both [Transform] and [SocketAttachmentComponent].
 * Reads the target entity's animated [SkinnedPose] and transforms the attached entity's position
 * and rotation to match the joint's position in world space.
 */
class SocketAttachmentSystem : System {
    private val scratchJointMat = Mat4()
    private val scratchWorldMat = Mat4()
    private val scratchRotation = Vec3f()

    /**
     * Updates all active socket attachments in [world] by [delta] seconds.
     *
     * @param world The active ECS world.
     * @param delta Frame elapsed time in seconds.
     */
    @Suppress("CyclomaticComplexMethod")
    override fun update(world: World, delta: Float) {
        world.family<Transform, SocketAttachmentComponent>().forEach { _, transform, attachment ->
            if (!attachment.enabled) return@forEach

            val targetEntity = attachment.targetEntity
            val targetTransform = world.get<Transform>(targetEntity) ?: return@forEach
            val pose = world.get<SkinnedPose>(targetEntity) ?: return@forEach
            if (attachment.jointIndex < 0 && attachment.jointName != null) {
                val skeleton = world.get<Animator>(targetEntity)?.player?.skeleton
                    ?: world.get<ModularCharacterComponent>(targetEntity)?.skeleton
                attachment.jointIndex = skeleton?.findBoneIndex(attachment.jointName!!) ?: return@forEach
            }
            if (attachment.jointIndex < 0) return@forEach

            pose.getJointMatrix(attachment.jointIndex, scratchJointMat)

            // If worldMatrix was not yet initialized by a transform pass, ensure local matrix is evaluated
            val worldMat = if (targetTransform.worldMatrix.data[12] == 0f &&
                targetTransform.worldMatrix.data[13] == 0f &&
                targetTransform.worldMatrix.data[14] == 0f &&
                (targetTransform.position.x != 0f || targetTransform.position.y != 0f || targetTransform.position.z != 0f)
            ) {
                targetTransform.computeLocalMatrix()
            } else {
                targetTransform.worldMatrix
            }

            Mat4.multiplyColumnMajor(worldMat, scratchJointMat, scratchWorldMat)

            transform.position.x = scratchWorldMat.data[12] + attachment.offsetPosition.x
            transform.position.y = scratchWorldMat.data[13] + attachment.offsetPosition.y
            transform.position.z = scratchWorldMat.data[14] + attachment.offsetPosition.z

            extractEuler(scratchWorldMat, scratchRotation)
            transform.rotation.x = scratchRotation.x + attachment.offsetRotation.x
            transform.rotation.y = scratchRotation.y + attachment.offsetRotation.y
            transform.rotation.z = scratchRotation.z + attachment.offsetRotation.z

            // Sync scale from parent
            transform.scale.x = targetTransform.scale.x
            transform.scale.y = targetTransform.scale.y
            transform.scale.z = targetTransform.scale.z
        }
    }

    private fun extractEuler(matrix: Mat4, out: Vec3f) {
        val sx = sqrt(matrix.m00 * matrix.m00 + matrix.m10 * matrix.m10 + matrix.m20 * matrix.m20)
        val sy = sqrt(matrix.m01 * matrix.m01 + matrix.m11 * matrix.m11 + matrix.m21 * matrix.m21)
        val sz = sqrt(matrix.m02 * matrix.m02 + matrix.m12 * matrix.m12 + matrix.m22 * matrix.m22)
        if (sx == 0f || sy == 0f || sz == 0f) {
            out.set(0f, 0f, 0f)
            return
        }
        val r00 = matrix.m00 / sx
        val r10 = matrix.m10 / sx
        val r20 = matrix.m20 / sx
        val r21 = matrix.m21 / sy
        val r22 = matrix.m22 / sz
        out.set(
            atan2(r21, r22),
            asin((-r20).coerceIn(-1f, 1f)),
            atan2(r10, r00),
        )
    }
}
