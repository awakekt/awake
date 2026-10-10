/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Keeps each entity with a [SocketAttachmentComponent] on a joint of its target's [SkinnedPose].
 *
 * The attached entity's world transform becomes `world * joint * offset`: the target's world
 * transform, the joint's own transform in the skinned mesh's space, then the attachment's offset, so
 * a grip offset turns with the hand. The joint's transform is read from the pose's palette through
 * the target's skin, its [ModularCharacterComponent]'s or else its [Animator]'s (see
 * [Skin.jointTransform]); a pose with neither is read as if every inverse bind matrix were identity.
 * A joint named in the skeleton is found in the skin, and a joint index is in the skin's joint order,
 * the palette's.
 *
 * Everything is read as it stands when this runs, not as the last [TransformSystem] pass left it: the
 * target's world transform is composed from its own and its ancestors' position, rotation and scale,
 * so the attachment follows a character in the frame it moves. Register this after whatever moves or
 * poses the target, such as [AnimationSystem]. An attached entity with a parent is given the local
 * transform that puts it there.
 */
class SocketAttachmentSystem : System {
    private val targetWorld = Mat4()
    private val parentWorld = Mat4()
    private val joint = Mat4()
    private val offset = Mat4()
    private val onJoint = Mat4()
    private val attached = Mat4()
    private val local = Mat4()
    private val ancestorLocal = Mat4()
    private val product = Mat4()

    /**
     * Updates all active socket attachments in [world] by [delta] seconds.
     *
     * @param world The active ECS world.
     * @param delta Frame elapsed time in seconds.
     */
    override fun update(world: World, delta: Float) {
        world.family<Transform, SocketAttachmentComponent>().forEach { _, transform, attachment ->
            if (!attachment.enabled) return@forEach
            val target = attachment.targetEntity
            val pose = world.get<SkinnedPose>(target) ?: return@forEach
            val animator = world.get<Animator>(target)
            val modular = world.get<ModularCharacterComponent>(target)
            // The skin the palette was written for: ModularSkeletalSystem writes a modular character's with its own.
            val skin = modular?.skin ?: animator?.skin
            val jointIndex = resolveJoint(attachment, skin, animator?.player?.skeleton ?: modular?.skeleton)
            if (jointIndex < 0 || !readJoint(pose, skin, jointIndex) || !world.composeWorld(target, targetWorld)) return@forEach

            offset.setEulerTRS(
                attachment.offsetPosition.x, attachment.offsetPosition.y, attachment.offsetPosition.z,
                attachment.offsetRotation.x, attachment.offsetRotation.y, attachment.offsetRotation.z,
                1f, 1f, 1f,
            )
            Mat4.multiplyColumnMajor(targetWorld, joint, onJoint)
            Mat4.multiplyColumnMajor(onJoint, offset, attached)
            transform.setFrom(localFor(world, transform.parent) ?: return@forEach)
        }
    }

    /** [SocketAttachmentComponent.jointIndex], resolved from its name into the skin's joint order the first time; -1 when it names no joint. */
    private fun resolveJoint(attachment: SocketAttachmentComponent, skin: Skin?, skeleton: Skeleton?): Int {
        if (attachment.jointIndex < 0) {
            val bone = attachment.jointName?.let { skeleton?.findBoneIndex(it) } ?: -1
            val jointIndex = if (bone < 0) -1 else skin?.joints?.indexOf(bone) ?: -1
            if (jointIndex >= 0) attachment.jointIndex = jointIndex
        }
        return attachment.jointIndex
    }

    /** Writes joint [index]'s transform under [pose] into [joint]; false when the palette has no such joint. */
    private fun readJoint(pose: SkinnedPose, skin: Skin?, index: Int): Boolean = if (skin != null) {
        skin.jointTransform(pose.jointPalette, index, joint) != null
    } else {
        ((index + 1) * MATRIX_FLOATS <= pose.jointPalette.size).also { inPalette -> if (inPalette) pose.getJointMatrix(index, joint) }
    }

    /** [attached] in [parent]'s space, or [attached] itself for an entity with no parent; null when the parent's matrix can't be inverted. */
    private fun localFor(world: World, parent: Entity?): Mat4? {
        if (parent == null || !world.composeWorld(parent, parentWorld)) return attached
        return parentWorld.inverse()?.let { inverseParent -> Mat4.multiplyColumnMajor(inverseParent, attached, local) }
    }

    /**
     * Writes [entity]'s world matrix into [out], composed from its own and its ancestors' current
     * position, rotation and scale, as [TransformSystem] composes it; false when it has no [Transform].
     */
    private fun World.composeWorld(entity: Entity, out: Mat4): Boolean {
        val transform = get<Transform>(entity) ?: return false
        transform.computeLocalMatrix(out)
        var ancestor = transform.parent
        var depth = 0
        while (ancestor != null && depth++ < MAX_HIERARCHY_DEPTH) {
            val ancestorTransform = get<Transform>(ancestor) ?: break
            Mat4.multiplyColumnMajor(ancestorTransform.computeLocalMatrix(ancestorLocal), out, product)
            out.set(product)
            ancestor = ancestorTransform.parent
        }
        return true
    }

    /** Sets this transform's position, Euler rotation and scale to [matrix]'s, the inverse of [Transform.computeLocalMatrix]. */
    private fun Transform.setFrom(matrix: Mat4) {
        position.set(matrix.m03, matrix.m13, matrix.m23)
        val determinant = matrix.m00 * (matrix.m11 * matrix.m22 - matrix.m12 * matrix.m21) -
            matrix.m01 * (matrix.m10 * matrix.m22 - matrix.m12 * matrix.m20) +
            matrix.m02 * (matrix.m10 * matrix.m21 - matrix.m11 * matrix.m20)
        val sx = length(matrix.m00, matrix.m10, matrix.m20) * if (determinant < 0f) -1f else 1f
        val sy = length(matrix.m01, matrix.m11, matrix.m21)
        val sz = length(matrix.m02, matrix.m12, matrix.m22)
        scale.set(sx, sy, sz)
        if (sx == 0f || sy == 0f || sz == 0f) {
            rotation.set(0f, 0f, 0f)
            return
        }
        // The composition order Transform uses, Z * Y * X, read back off the unscaled columns.
        val sinPitch = (-matrix.m20 / sx).coerceIn(-1f, 1f)
        if (sinPitch >= 1f - GIMBAL_EPSILON || sinPitch <= -1f + GIMBAL_EPSILON) {
            // At a pole X and Z turn about the same axis: X is folded into Z, as Quat.toEuler does.
            rotation.set(0f, if (sinPitch > 0f) HALF_PI else -HALF_PI, atan2(-matrix.m01 / sy, matrix.m11 / sy))
        } else {
            rotation.set(atan2(matrix.m21 / sy, matrix.m22 / sz), asin(sinPitch), atan2(matrix.m10 / sx, matrix.m00 / sx))
        }
    }

    private fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
}

private const val MATRIX_FLOATS = 16
private const val GIMBAL_EPSILON = 1e-6f
private const val HALF_PI = (PI / 2).toFloat()

/** Deeper than any real hierarchy; it only stops a cycle, which [TransformSystem] reports itself. */
private const val MAX_HIERARCHY_DEPTH = 1024
