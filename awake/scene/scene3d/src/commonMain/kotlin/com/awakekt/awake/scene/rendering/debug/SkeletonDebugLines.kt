/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.core.math.transformPosition
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.debugLayerColor
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose

/**
 * Each animated skin's bones as lines, in its current pose: one from every joint to the nearest
 * joint above it in the skeleton, in the colour `RenderDebugView.JointWeights` gives that joint, so
 * the bones and the weight paint read together. A bone the skin doesn't bind, such as an armature
 * root, is passed through rather than drawn.
 *
 * A joint's posed position is its palette matrix applied to where it sits in the bind pose, which is
 * the inverse of its inverse bind matrix, then the entity's world matrix: the same transform the
 * skinning shaders put a vertex bound wholly to that joint through.
 *
 * @see com.awakekt.awake.render.passes.uniforms.RenderDebugView.JointWeights
 */
internal fun appendSkeletonLines(world: World, lines: MutableList<LineSegment>) {
    world.family<Animator, SkinnedPose>().forEach { entity, animator, pose ->
        val transform = world.get<Transform>(entity) ?: return@forEach
        val skin = animator.skin
        val joints = posedJoints(skin, pose, transform.worldMatrix)
        jointParents(skin, animator.player.skeleton).forEachIndexed { joint, parent ->
            val from = joints.getOrNull(parent) ?: return@forEachIndexed
            val to = joints.getOrNull(joint) ?: return@forEachIndexed
            lines += LineSegment(from, to, debugLayerColor(joint))
        }
    }
}

/** Each joint of [skin] in the world, as [pose] and [world] place it; null where its bind matrix can't be inverted. */
internal fun posedJoints(skin: Skin, pose: SkinnedPose, world: Mat4): List<Vec3f?> {
    val palette = Mat4()
    return skin.inverseBindMatrices.mapIndexed { joint, inverseBind ->
        val bind = inverseBind.inverse() ?: return@mapIndexed null
        pose.getJointMatrix(joint, palette)
        val posed = palette.transformPosition(Vec4(bind.m03, bind.m13, bind.m23, 1f))
        val placed = world.transformPosition(posed)
        Vec3f(placed.x, placed.y, placed.z)
    }
}

/** For each joint of [skin], the joint its bone hangs from in [skeleton], skipping bones the skin doesn't bind; -1 for none. */
internal fun jointParents(skin: Skin, skeleton: Skeleton): IntArray {
    val boneParent = IntArray(skeleton.bones.size) { -1 }
    skeleton.bones.forEachIndexed { bone, node -> node.children.forEach { child -> if (child in boneParent.indices) boneParent[child] = bone } }
    val jointOfBone = HashMap<Int, Int>(skin.joints.size)
    skin.joints.forEachIndexed { joint, bone -> jointOfBone[bone] = joint }
    return IntArray(skin.joints.size) { joint ->
        var bone = boneParent.getOrElse(skin.joints[joint]) { -1 }
        // A cycle in a malformed skeleton ends the walk rather than looping.
        var steps = 0
        while (bone >= 0 && bone !in jointOfBone && steps++ < boneParent.size) bone = boneParent[bone]
        jointOfBone[bone] ?: -1
    }
}
