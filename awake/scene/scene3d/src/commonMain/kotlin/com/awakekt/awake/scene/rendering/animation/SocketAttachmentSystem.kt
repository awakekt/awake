/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * ECS system driving attached entities to track animated character skeleton joints in world space.
 *
 * Runs each frame to update every entity carrying both [Transform] and [SocketAttachmentComponent].
 * Reads the target entity's animated [SkinnedPose] and transforms the attached entity's position
 * and rotation to match the joint's position in world space.
 */
class SocketAttachmentSystem : System {
    private val tempJointPos = Vec3f()

    /**
     * Updates all active socket attachments in [world] by [delta] seconds.
     *
     * @param world The active ECS world.
     * @param delta Frame elapsed time in seconds.
     */
    override fun update(world: World, delta: Float) {
        world.family<Transform, SocketAttachmentComponent>().forEach { _, transform, attachment ->
            if (!attachment.enabled) return@forEach

            val targetEntity = attachment.targetEntity
            val targetTransform = world.get<Transform>(targetEntity) ?: return@forEach
            val pose = world.get<SkinnedPose>(targetEntity) ?: return@forEach

            // Read local joint translation from the target's joint palette
            pose.getJointPosition(attachment.jointIndex, tempJointPos)

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

            val finalX = worldMat.data[0] * tempJointPos.x + worldMat.data[4] * tempJointPos.y + worldMat.data[8] * tempJointPos.z + worldMat.data[12]
            val finalY = worldMat.data[1] * tempJointPos.x + worldMat.data[5] * tempJointPos.y + worldMat.data[9] * tempJointPos.z + worldMat.data[13]
            val finalZ = worldMat.data[2] * tempJointPos.x + worldMat.data[6] * tempJointPos.y + worldMat.data[10] * tempJointPos.z + worldMat.data[14]

            transform.position.x = finalX + attachment.offsetPosition.x
            transform.position.y = finalY + attachment.offsetPosition.y
            transform.position.z = finalZ + attachment.offsetPosition.z

            // Sync scale from parent
            transform.scale.x = targetTransform.scale.x
            transform.scale.y = targetTransform.scale.y
            transform.scale.z = targetTransform.scale.z
        }
    }
}
