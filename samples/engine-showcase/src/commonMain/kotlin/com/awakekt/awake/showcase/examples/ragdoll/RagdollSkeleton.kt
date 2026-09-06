/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.ragdoll

import com.awakekt.awake.core.animation.AnimationPose
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f

/**
 * Drives a skinned character's bones from a [Ragdoll], so a corpse wears its own mesh.
 *
 * This is the half that makes a ragdoll visible. Without it a ragdoll is eleven invisible capsules
 * falling next to a character that is still playing its idle animation.
 *
 * **The ragdoll is in world space and a skeleton is not**, which is the whole of the work here: a
 * bone's pose is stored relative to its parent bone, so every limb's world pose has to be walked
 * back down the hierarchy into the parent-relative form [AnimationPose] holds. Quaternions rather
 * than matrices throughout -- both a rigid body and a bone driven by one are rotation plus
 * translation with no scale, so there is nothing a matrix inverse would buy.
 *
 * A bone no limb is bound to keeps whatever the pose already had. That is what lets a ragdoll drive
 * the eleven bones it has opinions about while fingers and facial bones stay where the animation
 * left them.
 */
class RagdollSkeleton(
    private val skeleton: Skeleton,
    /**
     * Which bone each limb drives, indexed by limb, `-1` for a limb that drives none.
     *
     * A ragdoll is a coarse figure and a character's skeleton usually is not, so this is a mapping
     * rather than an assumption: eleven capsules onto whichever bones of sixty they stand for.
     */
    private val boneForLimb: IntArray,
    /**
     * Where each limb's bone sits in that limb's own frame, from [RagdollRig].
     *
     * Empty means the two frames coincide, which is only true of a ragdoll built to match the
     * skeleton exactly. For a rig whose bones point down their own local axis -- most of them --
     * this is what keeps the mesh from shearing; see [RagdollRig] for why it lives here rather than
     * in the collision shape.
     */
    private val boneOffsetPosition: List<Vec3f> = emptyList(),
    private val boneOffsetRotation: List<Quat> = emptyList(),
) {
    /** Built from a rig, which already measured the offsets against the skeleton's bind pose. */
    constructor(skeleton: Skeleton, rig: RagdollRig) : this(
        skeleton,
        rig.boneForLimb,
        rig.boneOffsetPosition,
        rig.boneOffsetRotation,
    )

    init {
        require(boneForLimb.all { it == UNBOUND || it in skeleton.bones.indices }) {
            "a limb is bound to a bone outside the skeleton: ${boneForLimb.toList()}"
        }
        require(boneOffsetPosition.size == boneOffsetRotation.size) {
            "every limb offset needs both halves: ${boneOffsetPosition.size} positions, " +
                "${boneOffsetRotation.size} rotations"
        }
        require(boneOffsetPosition.isEmpty() || boneOffsetPosition.size == boneForLimb.size) {
            "offsets were given for ${boneOffsetPosition.size} limbs but there are ${boneForLimb.size}"
        }
    }

    // Scratch, reused every frame: this runs per character per frame and a ragdoll that allocated
    // a pose per bone would produce garbage at the rate the game is played.
    private val limbPosition = List(boneForLimb.size) { Vec3f(0f, 0f, 0f) }
    private val limbRotation = List(boneForLimb.size) { Quat() }
    private val boundLimb = IntArray(skeleton.bones.size) { UNBOUND }.also { bound ->
        boneForLimb.forEachIndexed { limb, bone -> if (bone != UNBOUND) bound[bone] = limb }
    }
    private val globalPosition = List(skeleton.bones.size) { Vec3f(0f, 0f, 0f) }

    /** Reused for a baked matrix's translation, which has nowhere else to live. */
    private val scratchLocal = Vec3f(0f, 0f, 0f)
    private val globalRotation = MutableList(skeleton.bones.size) { Quat() }

    /**
     * Writes [ragdoll]'s current limb poses into [pose].
     *
     * [modelPosition] and [modelRotation] are where the character itself stands -- the transform the
     * skeleton is drawn under. The ragdoll simulates in world space and the skeleton is drawn in
     * the model's, so passing the wrong one puts the corpse at the origin, or twice as far from it.
     */
    fun apply(
        ragdoll: Ragdoll,
        pose: AnimationPose,
        modelPosition: Vec3f = Vec3f(0f, 0f, 0f),
        modelRotation: Quat = Quat.IDENTITY,
    ) {
        ragdoll.forEachLimb { index, position, rotation ->
            if (index < limbPosition.size) {
                // Copied rather than held: both values are scratch, reused between limbs.
                if (boneOffsetPosition.isEmpty()) {
                    limbPosition[index].set(position)
                    limbRotation[index].set(rotation)
                } else {
                    // The body moved; the bone rides along at the fixed offset measured at bind.
                    val offset = rotation.rotate(boneOffsetPosition[index])
                    limbPosition[index].set(position.x + offset.x, position.y + offset.y, position.z + offset.z)
                    limbRotation[index].set(boneOffsetRotation[index] * rotation)
                }
            }
        }
        // World into model space once, so the walk below works entirely in the skeleton's own frame.
        val intoModel = modelRotation.conjugate()
        skeleton.roots.forEach { root -> writeBone(root, PARENT_OF_ROOT, pose, modelPosition, intoModel) }
    }

    /**
     * Poses one bone and then its children, carrying each bone's model-space pose down the chain.
     *
     * Depth-first and top-down, because a bone's local transform is only meaningful once its
     * parent's model-space pose is known -- which is exactly what a parent has just computed.
     */
    private fun writeBone(
        bone: Int,
        parent: Int,
        pose: AnimationPose,
        modelPosition: Vec3f,
        intoModel: Quat,
    ) {
        val parentPosition = if (parent == PARENT_OF_ROOT) ORIGIN else globalPosition[parent]
        val parentRotation = if (parent == PARENT_OF_ROOT) Quat.IDENTITY else globalRotation[parent]
        val limb = boundLimb[bone]
        if (limb == UNBOUND) {
            // Nothing drives this bone, so it keeps the pose it has and only has to report where
            // that puts it -- otherwise a driven bone below it would be measured from the wrong
            // place.
            //
            // A bone authored as a baked matrix is read from that matrix rather than from the pose,
            // which is the rule playback itself follows: the pose has no values for such a bone, so
            // taking them anyway composes an identity where a real transform belongs. A glTF Z-up
            // root is exactly this, and getting it wrong lays the whole figure on its side.
            val matrix = skeleton.bones[bone].matrix
            val local = matrix?.let { scratchLocal.set(it.m03, it.m13, it.m23) } ?: pose.boneTranslation(bone)
            val rotation = matrix?.let(::rotationOf) ?: pose.boneRotation(bone)
            globalPosition[bone].set(parentPosition).add(parentRotation.rotate(local))
            globalRotation[bone].set(rotation * parentRotation)
        } else {
            // The limb's world pose, in the model's frame.
            val position = limbPosition[limb]
            globalPosition[bone].set(
                intoModel.rotate(
                    Vec3f(
                        position.x - modelPosition.x,
                        position.y - modelPosition.y,
                        position.z - modelPosition.z,
                    ),
                ),
            )
            globalRotation[bone].set(limbRotation[limb] * intoModel)
            // And back out into the parent-relative form the pose stores.
            val inverseParent = parentRotation.conjugate()
            val offset = globalPosition[bone]
            pose.setBoneTransform(
                bone,
                inverseParent.rotate(
                    Vec3f(
                        offset.x - parentPosition.x,
                        offset.y - parentPosition.y,
                        offset.z - parentPosition.z,
                    ),
                ),
                globalRotation[bone] * inverseParent,
            )
        }
        skeleton.bones[bone].children.forEach { child ->
            writeBone(child, bone, pose, modelPosition, intoModel)
        }
    }

    private companion object {
        /** In [boneForLimb] a limb that drives nothing; in [boundLimb] a bone nothing drives. */
        const val UNBOUND = -1

        /** A root bone's parent, which is the model's own transform rather than another bone. */
        const val PARENT_OF_ROOT = -1

        val ORIGIN = Vec3f(0f, 0f, 0f)
    }
}
