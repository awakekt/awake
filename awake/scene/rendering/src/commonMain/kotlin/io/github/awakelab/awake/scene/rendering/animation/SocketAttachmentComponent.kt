/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.animation

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity

/**
 * ECS component attaching this entity to a specific animated skeleton bone of [targetEntity].
 *
 * Used for weapons, shields, wings, torches, or equipment pieces that follow an animated character's
 * skeleton without being skinned themselves.
 *
 * @property targetEntity The animated character entity owning the skeleton and [SkinnedPose].
 * @property jointIndex The bone index within the target character's joint palette to follow.
 * @property offsetPosition Relative translation offset applied in the joint's coordinate space.
 * @property offsetRotation Relative Euler rotation offset.
 * @property enabled Whether this socket attachment is active and updated each frame.
 */
data class SocketAttachmentComponent(
    var targetEntity: Entity,
    var jointIndex: Int,
    var offsetPosition: Vec3f = Vec3f(0f, 0f, 0f),
    var offsetRotation: Vec3f = Vec3f(0f, 0f, 0f),
    var enabled: Boolean = true,
)
