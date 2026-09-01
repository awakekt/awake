/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.ragdoll

import io.github.awakelab.awake.core.animation.Skeleton
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.CapsuleShape
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A ragdoll shaped to a particular character, and where each limb sits relative to its bone.
 *
 * [boneOffsetPosition] and [boneOffsetRotation] are the whole reason this type exists. A capsule
 * wants to lie *along* a bone, and a bone's own frame usually points somewhere else entirely --
 * glTF rigs routinely run their bones down local X or Z, and a character exported from one tool
 * disagrees with the same character exported from another. So the two frames differ by a fixed
 * rigid transform, measured once at the bind pose and applied on the way back out.
 *
 * Without it there are only bad choices: orient each body to its bone and the collision capsules
 * stick out sideways from the limbs they stand for, or orient them along the limbs and the skinned
 * mesh shears the moment the ragdoll is applied.
 *
 * ponytail: this is deliberately not Jolt's `RotatedTranslatedShape`, which is the other way to
 * express it. That shape is absent from JoltC, and its natural substitute -- a compound shape with
 * one child -- Jolt rejects outright ("Compound needs at least 2 sub shapes"). A quaternion per
 * limb costs nothing and needs no backend to grow.
 */
class RagdollRig(
    val limbs: List<RagdollLimb>,
    val joints: List<RagdollJoint>,
    /** Which bone each limb drives, for [RagdollSkeleton]. */
    val boneForLimb: IntArray,
    /** Where the bone sits in its limb's frame, per limb. */
    val boneOffsetPosition: List<Vec3f>,
    /** How the bone is turned in its limb's frame, per limb. */
    val boneOffsetRotation: List<Quat>,
)

/**
 * Builds a ragdoll shaped to [skeleton]'s bind pose, one capsule per named bone.
 *
 * Each capsule runs from its bone to the bone below it, which is what a limb is: a thigh is the
 * span between a hip and a knee, and neither joint is a limb on its own. A bone with no driven bone
 * under it -- a head, a foot -- gets a stub of [tipLength], since there is nothing to measure it
 * against.
 *
 * Joints follow the same hierarchy: each limb is pinned to the nearest driven bone above it, at
 * that limb's own bone, because that is where the two bones meet.
 *
 * Everything is produced in the skeleton's own space. A caller placing the character somewhere adds
 * its transform when creating the [Ragdoll] and passes the same one to [RagdollSkeleton.apply];
 * measuring the offsets here in model space is what keeps those two independent.
 */
@Suppress("LongParameterList")
fun ragdollFromSkeleton(
    skeleton: Skeleton,
    /** The bones to give a limb to, in the order the limbs will be created. */
    bones: IntArray,
    radius: Float = DEFAULT_LIMB_RADIUS,
    tipLength: Float = DEFAULT_TIP_LENGTH,
    swingLimit: Float = DEFAULT_SWING_LIMIT,
    twistLimit: ClosedFloatingPointRange<Float>? = DEFAULT_TWIST_LIMIT,
    origin: Vec3f = Vec3f(0f, 0f, 0f),
): RagdollRig {
    require(bones.isNotEmpty()) { "a ragdoll needs at least one bone" }
    require(bones.all { it in skeleton.bones.indices }) {
        "a bone is outside the skeleton: ${bones.toList()}"
    }
    require(bones.toSet().size == bones.size) { "the same bone was given two limbs: ${bones.toList()}" }

    val bind = skeleton.bindPose()
    val driven = bones.toHashSet()
    val parentBone = skeleton.parents()
    val limbForBone = HashMap<Int, Int>()
    bones.forEachIndexed { limb, bone -> limbForBone[bone] = limb }

    val limbs = ArrayList<RagdollLimb>(bones.size)
    val offsetPositions = ArrayList<Vec3f>(bones.size)
    val offsetRotations = ArrayList<Quat>(bones.size)
    val joints = ArrayList<RagdollJoint>()

    bones.forEachIndexed { limbIndex, bone ->
        val start = bind.position[bone]
        val end = limbFarEnd(skeleton, bind, bone, driven, tipLength)

        val axis = Vec3f(end.x - start.x, end.y - start.y, end.z - start.z)
        val length = max(sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z), MINIMUM_LIMB_LENGTH)
        // A capsule's own axis is Y, so the body is turned to put that along the limb.
        val limbRotation = rotationFromYTo(axis)
        val limbPosition = Vec3f(
            origin.x + (start.x + end.x) / 2f,
            origin.y + (start.y + end.y) / 2f,
            origin.z + (start.z + end.z) / 2f,
        )
        limbs += RagdollLimb(
            // Half the span, less the caps, so the capsule ends where the bones do.
            CapsuleShape(halfHeight = max(length / 2f - radius, MINIMUM_HALF_HEIGHT), radius = radius),
            limbPosition,
            limbRotation,
        )

        // The bone, expressed in the limb's frame. Measured once, here, at the pose where both are
        // known -- afterwards the body moves and this stays put.
        val intoLimb = limbRotation.conjugate()
        offsetPositions += intoLimb.rotate(
            Vec3f(
                start.x + origin.x - limbPosition.x,
                start.y + origin.y - limbPosition.y,
                start.z + origin.z - limbPosition.z,
            ),
        )
        offsetRotations += bind.rotation[bone] * intoLimb

        // Pinned to the nearest driven bone above, at this bone -- which is where they meet.
        // The takeIf halves are not decoration: a root's parent is -1, and without them the walk
        // uses that as an index on its very next step.
        val ancestor = generateSequence(parentBone[bone].takeIf { it >= 0 }) {
            parentBone[it].takeIf { parent -> parent >= 0 }
        }.firstOrNull { it in driven }
        if (ancestor != null) {
            joints += RagdollJoint(
                parent = requireNotNull(limbForBone[ancestor]),
                child = limbIndex,
                anchor = Vec3f(origin.x + start.x, origin.y + start.y, origin.z + start.z),
                swingLimit = swingLimit,
                twistLimit = twistLimit,
            )
        }
    }

    return RagdollRig(limbs, joints, bones.copyOf(), offsetPositions, offsetRotations)
}


/**
 * Where a limb's far end sits: the mean of the driven bones hanging off [bone].
 *
 * The mean rather than the first, so a pelvis with two thighs under it gets a body between them
 * instead of one arbitrarily following a leg. A bone with nothing driven below it has only its own
 * direction to go on, and gets a stub of [tipLength].
 */
private fun limbFarEnd(
    skeleton: Skeleton,
    bind: BindPose,
    bone: Int,
    driven: Set<Int>,
    tipLength: Float,
): Vec3f {
    val start = bind.position[bone]
    val children = skeleton.descendantsDrivenBy(bone, driven)
    if (children.isEmpty()) {
        val forward = bind.rotation[bone].rotate(Vec3f(0f, 1f, 0f))
        return Vec3f(
            start.x + forward.x * tipLength,
            start.y + forward.y * tipLength,
            start.z + forward.z * tipLength,
        )
    }
    var x = 0f
    var y = 0f
    var z = 0f
    children.forEach { child ->
        x += bind.position[child].x
        y += bind.position[child].y
        z += bind.position[child].z
    }
    return Vec3f(x / children.size, y / children.size, z / children.size)
}

/** Every bone's model-space transform in the skeleton's authored bind pose. */
internal class BindPose(val position: List<Vec3f>, val rotation: List<Quat>)

internal fun Skeleton.bindPose(): BindPose {
    val positions = MutableList(bones.size) { Vec3f(0f, 0f, 0f) }
    val rotations = MutableList(bones.size) { Quat.IDENTITY }

    fun visit(bone: Int, parentPosition: Vec3f, parentRotation: Quat) {
        val local = bones[bone]
        // A bone authored as a baked matrix carries both halves in that matrix, and taking only one
        // of them puts every bone below it in the wrong place: CesiumMan's Z-up root is exactly
        // such a bone. TRS bones, which is nearly all of them, skip the extraction entirely.
        val localRotation = local.matrix?.let(::rotationOf) ?: local.rotation
        val localTranslation = local.matrix
            ?.let { Vec3f(it.m03, it.m13, it.m23) }
            ?: local.translation
        val turned = parentRotation.rotate(localTranslation)
        positions[bone] = Vec3f(
            parentPosition.x + turned.x,
            parentPosition.y + turned.y,
            parentPosition.z + turned.z,
        )
        // local then parent, not the other way round: Quat.times is Hamilton with its operands
        // swapped, so `a * b` means "a then b" -- and hierarchy composition is the parent applied
        // last. QuatCompositionTest pins this against the matrix path playback actually uses.
        rotations[bone] = localRotation * parentRotation
        bones[bone].children.forEach { visit(it, positions[bone], rotations[bone]) }
    }
    roots.forEach { visit(it, Vec3f(0f, 0f, 0f), Quat.IDENTITY) }
    return BindPose(positions, rotations)
}

/** Each bone's parent, or `-1` for a root. */
internal fun Skeleton.parents(): IntArray = IntArray(bones.size) { -1 }.also { parents ->
    bones.forEachIndexed { index, bone -> bone.children.forEach { parents[it] = index } }
}

/**
 * The driven bones nearest below [bone], skipping over undriven ones in between.
 *
 * A rig usually has bones a ragdoll has no opinion about sitting between the ones it does -- twist
 * bones, roll bones, an extra spine segment. Stopping at the first driven descendant on each branch
 * is what lets a coarse eleven-limb figure sit on a sixty-bone rig.
 */
internal fun Skeleton.descendantsDrivenBy(bone: Int, driven: Set<Int>): List<Int> {
    val found = mutableListOf<Int>()
    fun walk(current: Int) {
        bones[current].children.forEach { child ->
            if (child in driven) found += child else walk(child)
        }
    }
    walk(bone)
    return found
}

/** The rotation part of a baked matrix, assuming it carries no scale -- a joint matrix does not. */
internal fun rotationOf(matrix: Mat4): Quat {
    val trace = matrix.m00 + matrix.m11 + matrix.m22
    return if (trace > 0f) {
        val s = sqrt(trace + 1f) * 2f
        Quat((matrix.m21 - matrix.m12) / s, (matrix.m02 - matrix.m20) / s, (matrix.m10 - matrix.m01) / s, s / 4f)
    } else if (matrix.m00 > matrix.m11 && matrix.m00 > matrix.m22) {
        val s = sqrt(1f + matrix.m00 - matrix.m11 - matrix.m22) * 2f
        Quat(s / 4f, (matrix.m01 + matrix.m10) / s, (matrix.m02 + matrix.m20) / s, (matrix.m21 - matrix.m12) / s)
    } else if (matrix.m11 > matrix.m22) {
        val s = sqrt(1f + matrix.m11 - matrix.m00 - matrix.m22) * 2f
        Quat((matrix.m01 + matrix.m10) / s, s / 4f, (matrix.m12 + matrix.m21) / s, (matrix.m02 - matrix.m20) / s)
    } else {
        val s = sqrt(1f + matrix.m22 - matrix.m00 - matrix.m11) * 2f
        Quat((matrix.m02 + matrix.m20) / s, (matrix.m12 + matrix.m21) / s, s / 4f, (matrix.m10 - matrix.m01) / s)
    }
}

/** The shortest rotation taking `+Y` -- a capsule's own axis -- onto [direction]. */
private fun rotationFromYTo(direction: Vec3f): Quat {
    val length = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
    val x = direction.x / length
    val y = direction.y / length
    val z = direction.z / length
    return when {
        // No direction to align to, so any rotation is as good as another.
        length < MINIMUM_LIMB_LENGTH -> Quat.IDENTITY
        // Antiparallel is the one case the half-way construction cannot express: the axis it needs
        // is undefined, and any perpendicular one is correct. Z is perpendicular to Y.
        y < -1f + ANTIPARALLEL_SLACK -> Quat(0f, 0f, 1f, 0f)
        else -> {
            // The half-way quaternion between +Y and the direction, normalised.
            val w = 1f + y
            val norm = sqrt(z * z + x * x + w * w)
            Quat(z / norm, 0f, -x / norm, w / norm)
        }
    }
}

private const val DEFAULT_LIMB_RADIUS = 0.06f
private const val DEFAULT_TIP_LENGTH = 0.12f
private const val MINIMUM_LIMB_LENGTH = 1e-4f
private const val MINIMUM_HALF_HEIGHT = 0.01f
private const val ANTIPARALLEL_SLACK = 1e-4f

/** Generous by default: a rig this is measured from is not necessarily built standing. */
private val DEFAULT_SWING_LIMIT = (kotlin.math.PI / 2).toFloat()
private val DEFAULT_TWIST_LIMIT = -(kotlin.math.PI / 4).toFloat()..(kotlin.math.PI / 4).toFloat()
