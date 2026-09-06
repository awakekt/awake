/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f
import kotlin.jvm.JvmInline
import kotlin.math.PI
import kotlin.math.abs

/**
 * A live constraint, returned by [PhysicsWorld.createConstraint].
 *
 * Dies with either body it joins: [PhysicsWorld.destroyBody] takes its constraints with it, so a
 * handle held past that point refers to nothing and must not be used.
 */
@JvmInline
value class ConstraintHandle(val id: Long)

/**
 * A rule tying two bodies together, for the joints a world is made of.
 *
 * Three types, because three cover the verbs that asked for them: a hinge is every door, lid, lever
 * and wheel, a distance is every rope, chain and grapple, and a ball-and-socket is every shoulder,
 * hip and neck. Jolt has seven more; each can be added when something needs it, which is cheaper
 * than guessing at parameters nobody has used.
 *
 * Anchors are given in **world space, at the moment of creation** — Jolt converts them to each
 * body's local frame there and then. Bodies do not have to be touching, or even near each other,
 * but wherever they are when this is created is the arrangement the constraint treats as its rest
 * pose.
 */
sealed interface Constraint {
    val bodyA: BodyHandle
    val bodyB: BodyHandle
}

/**
 * Lets two bodies rotate about one shared axis and nothing else — a door, a lid, a lever, a wheel.
 *
 * [limits] are radians about [axis], measured from the arrangement the bodies are in when this is
 * created. `null` swings freely, which is a turntable rather than a door: a door wants something
 * like `0f..(PI / 2)`, and a door with no stop swings through its own frame.
 */
data class HingeConstraint(
    override val bodyA: BodyHandle,
    override val bodyB: BodyHandle,
    /** The world-space point both bodies pivot about. */
    val point: Vec3f,
    /** The world-space axis they turn around; a door's is vertical. */
    val axis: Vec3f,
    val limits: ClosedFloatingPointRange<Float>? = null,
) : Constraint {
    init {
        require(bodyA != bodyB) { "a constraint joins two different bodies" }
        require(limits == null || !limits.isEmpty()) { "an empty limit range locks the hinge: $limits" }
    }
}

/**
 * Holds two points on two bodies within a range of each other — a rope, a chain, a grapple.
 *
 * A rope is [minDistance] `0` and a [maxDistance] of its length: the bodies may come as close as
 * they like and are caught at the end. Equal minimum and maximum is a rigid link, which is stiff
 * and stable in a way a rope is not; a spring is a maximum with slack in it.
 *
 * **This is not a rope simulation.** One constraint is one link, so a rope that should drape over
 * an edge or coil wants a chain of bodies joined by several of these.
 */
data class DistanceConstraint(
    override val bodyA: BodyHandle,
    override val bodyB: BodyHandle,
    /** The world-space attachment point on [bodyA]. */
    val pointA: Vec3f,
    /** The world-space attachment point on [bodyB]. */
    val pointB: Vec3f,
    val minDistance: Float = 0f,
    val maxDistance: Float,
) : Constraint {
    init {
        require(bodyA != bodyB) { "a constraint joins two different bodies" }
        require(minDistance >= 0f && minDistance.isFinite()) {
            "minDistance must be finite and >= 0: $minDistance"
        }
        require(maxDistance >= minDistance && maxDistance.isFinite()) {
            "maxDistance must be finite and >= minDistance: $minDistance..$maxDistance"
        }
    }
}

/**
 * Pins two bodies at a point and lets them pivot about it within a cone — a shoulder, a hip, a neck.
 *
 * The limits are described the way an anatomy diagram does. [swingLimit] is how far the joint can
 * bend away from [twistAxis] in any direction, and [twistLimit] is how far it can rotate about it:
 * a shoulder swings far and twists moderately, a neck does neither very much, and both together are
 * what stops a ragdoll folding into shapes a body cannot make.
 *
 * [twistAxis] is the direction the joint points down when it is at rest — for a limb, along the
 * bone, away from the joint. It is only a reference frame: the swing cone opens around it, and the
 * twist is measured about it.
 *
 * **The swing cone is symmetric, and that is a backend limit rather than a choice.** Jolt can also
 * make a pyramid-shaped, asymmetric swing, but its swing type is not settable through every binding
 * this engine builds on, so all four are pinned to the cone they agree on. An elbow, which really is
 * asymmetric, wants [HingeConstraint] anyway.
 *
 * Backed by Jolt's six-DOF constraint (translations locked, rotations limited) rather than its
 * point or swing-twist constraints, which are the natural fit and are absent from JoltC.
 */
data class BallSocketConstraint(
    override val bodyA: BodyHandle,
    override val bodyB: BodyHandle,
    /** The world-space point both bodies pivot about. */
    val point: Vec3f,
    /** The world-space direction the joint points at rest; swing opens around it, twist turns about it. */
    val twistAxis: Vec3f,
    /** Half-angle of the swing cone in radians, in `0..PI`. [FREE_SWING] is unlimited. */
    val swingLimit: Float = FREE_SWING,
    /** Twist range about [twistAxis] in radians, within `-PI..PI`. `null` twists freely. */
    val twistLimit: ClosedFloatingPointRange<Float>? = null,
) : Constraint {
    init {
        require(bodyA != bodyB) { "a constraint joins two different bodies" }
        require(swingLimit in 0f..FREE_SWING) { "swingLimit is a cone half-angle in 0..PI: $swingLimit" }
        require(twistAxis.length3() > 0f) { "twistAxis has no direction: $twistAxis" }
        twistLimit?.let {
            require(!it.isEmpty()) { "an empty twist range locks the joint: $it" }
            require(it.start >= -FREE_SWING && it.endInclusive <= FREE_SWING) {
                "twistLimit must lie within -PI..PI: $it"
            }
        }
    }

    /**
     * A unit direction perpendicular to [twistAxis], for the backend's second reference axis.
     *
     * Derived here rather than in each backend because the choice is not arbitrary: it is where a
     * twist of zero points, so two backends picking differently would measure [twistLimit] from two
     * different places and the same joint would behave differently per platform.
     */
    fun swingReferenceAxis(): Vec3f {
        val axis = twistAxis.normalized()
        // Cross with whichever world axis this one leans on least: crossing with a near-parallel
        // vector gives a near-zero result, which normalises into noise.
        val fallback = if (abs(axis.x) < abs(axis.y) && abs(axis.x) < abs(axis.z)) {
            Vec3f(1f, 0f, 0f)
        } else if (abs(axis.y) < abs(axis.z)) {
            Vec3f(0f, 1f, 0f)
        } else {
            Vec3f(0f, 0f, 1f)
        }
        return axis.cross(fallback).normalized()
    }

    companion object {
        /** A swing of half a turn in every direction, which is no limit at all. */
        const val FREE_SWING: Float = PI.toFloat()
    }
}
