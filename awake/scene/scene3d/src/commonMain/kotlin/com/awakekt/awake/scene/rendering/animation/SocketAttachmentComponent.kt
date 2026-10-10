/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity

/**
 * ECS component attaching this entity to a specific animated skeleton bone of [targetEntity].
 *
 * Used for weapons, shields, wings, torches, or equipment pieces that follow an animated character's
 * skeleton without being skinned themselves.
 *
 * @property targetEntity The animated character entity owning the skeleton and [SkinnedPose]: the one
 *   with the [Animator] or [ModularCharacterComponent] whose skin the pose was written for.
 * @property jointIndex The joint to follow, as an index into the target's skin joints, the joint
 *   palette's order, which is not the skeleton's bone order.
 * @property jointName Optional skeleton bone name. When set with a negative [jointIndex], the
 *   system finds the bone in the target's skeleton and its joint in the skin, and stores that joint
 *   in [jointIndex]. A bone that is not one of the skin's joints is not followed.
 * @property offsetPosition Translation from the joint, in the joint's own space, so it turns with it.
 * @property offsetRotation Euler rotation in radians from the joint, in the joint's own space, in the
 *   order `Transform.rotation` uses.
 * @property enabled Whether this socket attachment is active and updated each frame.
 */
data class SocketAttachmentComponent(
    var targetEntity: Entity,
    var jointIndex: Int = -1,
    var jointName: String? = null,
    var offsetPosition: Vec3f = Vec3f(0f, 0f, 0f),
    var offsetRotation: Vec3f = Vec3f(0f, 0f, 0f),
    var enabled: Boolean = true,
)
