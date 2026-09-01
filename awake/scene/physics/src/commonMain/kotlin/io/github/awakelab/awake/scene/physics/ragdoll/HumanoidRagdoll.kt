/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.ragdoll

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.CapsuleShape
import kotlin.math.PI

/**
 * An eleven-limb humanoid, sized from a total height and standing at [origin].
 *
 * Proportions are the usual eight-heads figure rounded to something a capsule can express: this is
 * a collision puppet, not an anatomy model, and a ragdoll reads as right when the mass is roughly
 * in the right places rather than when the bones are.
 *
 * Knees and elbows are hinges with limits, because they bend one way and a ball joint at a knee
 * gives the backwards-folding leg that reads as broken rather than as limp. Everything else is a
 * ball joint with a swing cone sized to what the real joint does: a shoulder swings far, a neck and
 * a spine barely at all, and a hip sits between them. The limits are what keep a collapsed figure
 * looking like a body rather than a bag of parts.
 */
fun humanoidRagdoll(
    height: Float = 1.8f,
    origin: Vec3f = Vec3f(0f, 0f, 0f),
): Pair<List<RagdollLimb>, List<RagdollJoint>> {
    val unit = height / TOTAL_UNITS
    val at: (Float, Float) -> Vec3f = { x, y ->
        Vec3f(origin.x + x * unit, origin.y + y * unit, origin.z)
    }
    return humanoidLimbs(unit, at) to humanoidJoints(at)
}

private fun humanoidLimbs(unit: Float, at: (Float, Float) -> Vec3f): List<RagdollLimb> {
    fun limb(halfHeight: Float, radius: Float, x: Float, y: Float) =
        RagdollLimb(CapsuleShape(halfHeight = halfHeight * unit, radius = radius * unit), at(x, y))

    return listOf(
        limb(halfHeight = 0.7f, radius = 0.9f, x = 0f, y = 9.4f), // Pelvis
        limb(halfHeight = 1.6f, radius = 0.9f, x = 0f, y = 12.2f), // Torso
        limb(halfHeight = 0.6f, radius = 0.8f, x = 0f, y = 15.2f), // Head
        limb(halfHeight = 1.3f, radius = 0.4f, x = -1.6f, y = 12.8f), // LeftUpperArm
        limb(halfHeight = 1.3f, radius = 0.35f, x = -1.6f, y = 10f), // LeftLowerArm
        limb(halfHeight = 1.3f, radius = 0.4f, x = 1.6f, y = 12.8f), // RightUpperArm
        limb(halfHeight = 1.3f, radius = 0.35f, x = 1.6f, y = 10f), // RightLowerArm
        limb(halfHeight = 1.8f, radius = 0.55f, x = -0.7f, y = 6.6f), // LeftThigh
        limb(halfHeight = 1.8f, radius = 0.45f, x = -0.7f, y = 2.6f), // LeftShin
        limb(halfHeight = 1.8f, radius = 0.55f, x = 0.7f, y = 6.6f), // RightThigh
        limb(halfHeight = 1.8f, radius = 0.45f, x = 0.7f, y = 2.6f), // RightShin
    )
}

private fun humanoidJoints(at: (Float, Float) -> Vec3f): List<RagdollJoint> = listOf(
    RagdollJoint(
        HumanoidLimb.Pelvis.index,
        HumanoidLimb.Torso.index,
        at(0f, 10.6f),
        swingLimit = SPINE_SWING,
        twistLimit = -SPINE_TWIST..SPINE_TWIST,
    ),
    RagdollJoint(
        HumanoidLimb.Torso.index,
        HumanoidLimb.Head.index,
        at(0f, 14.2f),
        swingLimit = NECK_SWING,
        twistLimit = -NECK_TWIST..NECK_TWIST,
    ),
) + armJoints(at, HumanoidLimb.LeftUpperArm, HumanoidLimb.LeftLowerArm, x = -1.6f) +
    armJoints(at, HumanoidLimb.RightUpperArm, HumanoidLimb.RightLowerArm, x = 1.6f) +
    legJoints(at, HumanoidLimb.LeftThigh, HumanoidLimb.LeftShin, x = -0.7f) +
    legJoints(at, HumanoidLimb.RightThigh, HumanoidLimb.RightShin, x = 0.7f)

/** A shoulder and the elbow below it, on whichever side [x] puts them. */
private fun armJoints(
    at: (Float, Float) -> Vec3f,
    upper: HumanoidLimb,
    lower: HumanoidLimb,
    x: Float,
) = listOf(
    RagdollJoint(
        HumanoidLimb.Torso.index,
        upper.index,
        at(x, 13.9f),
        swingLimit = SHOULDER_SWING,
        twistLimit = -SHOULDER_TWIST..SHOULDER_TWIST,
    ),
    RagdollJoint(upper.index, lower.index, at(x, 11.4f), hingeAxis = ACROSS, limits = 0f..ELBOW_AND_KNEE_LIMIT),
)

/** A hip and the knee below it. The knee bends the other way from an elbow, hence the negated range. */
private fun legJoints(
    at: (Float, Float) -> Vec3f,
    thigh: HumanoidLimb,
    shin: HumanoidLimb,
    x: Float,
) = listOf(
    RagdollJoint(
        HumanoidLimb.Pelvis.index,
        thigh.index,
        at(x, 8.6f),
        swingLimit = HIP_SWING,
        twistLimit = -HIP_TWIST..HIP_TWIST,
    ),
    RagdollJoint(thigh.index, shin.index, at(x, 4.6f), hingeAxis = ACROSS, limits = -ELBOW_AND_KNEE_LIMIT..0f),
)

/** A hinge's axis for a knee or an elbow: both bend in the sagittal plane, so it runs across. */
private val ACROSS = Vec3f(1f, 0f, 0f)

/** The figure is this many units tall, so every measurement above is in eighths of a head. */
private const val TOTAL_UNITS = 16f

/** Just under a right angle: a joint that folds flat lets a limb pass through its own neighbour. */
private const val ELBOW_AND_KNEE_LIMIT = (PI / 2).toFloat()

// Swing cones and twist ranges, roughly what each joint does on a living body and deliberately
// generous: a ragdoll that is limited too tightly resists the floor and settles standing up.
/** The widest joint in the body -- an arm reaches most of a hemisphere. */
private const val SHOULDER_SWING = (PI / 2).toFloat()
private const val SHOULDER_TWIST = (PI / 3).toFloat()

/**
 * A hip flexes about this far forward on a living body, and needs every bit of it here: the cone is
 * measured from the pose the figure was built in, so a ragdoll built standing has to swing its
 * thighs a right angle out of it merely to lie on the floor. A tighter cone than that is a figure
 * that props itself up against the ground forever and never sleeps.
 */
private const val HIP_SWING = (2 * PI / 3).toFloat()
private const val HIP_TWIST = (PI / 6).toFloat()

/** A spine bends by a lot in total and very little at any one joint; this figure has one. */
private const val SPINE_SWING = (PI / 4).toFloat()
private const val SPINE_TWIST = (PI / 6).toFloat()

/** A head that swings much further than this is the thing that reads as a broken neck. */
private const val NECK_SWING = (PI / 4).toFloat()
private const val NECK_TWIST = (PI / 4).toFloat()
