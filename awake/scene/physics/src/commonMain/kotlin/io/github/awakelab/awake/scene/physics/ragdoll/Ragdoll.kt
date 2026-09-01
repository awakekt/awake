/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.ragdoll

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BallSocketConstraint
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.ConstraintHandle
import io.github.awakelab.awake.physics.HingeConstraint
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld

/** One limb: a shape and where it starts. */
data class RagdollLimb(
    val shape: PhysicsShape,
    val position: Vec3f,
    val rotation: Quat = Quat.IDENTITY,
)

/**
 * What holds two limbs together.
 *
 * [hingeAxis] `null` is a ball joint -- free to rotate any way, which is the cheap ragdoll every
 * engine starts with. An axis makes it a hinge, which is what a knee or an elbow actually is: they
 * bend one way, and a ball joint at a knee gives the backwards-folding leg that reads as broken
 * rather than as limp.
 */
data class RagdollJoint(
    /** Index into the limb list. */
    val parent: Int,
    /** Index into the limb list. */
    val child: Int,
    /** Where the two limbs are pinned, in world space at build time. */
    val anchor: Vec3f,
    val hingeAxis: Vec3f? = null,
    /** Radians about [hingeAxis]; ignored by a ball joint. */
    val limits: ClosedFloatingPointRange<Float>? = null,
    /**
     * How far a ball joint bends away from the limb it holds, in radians; ignored by a hinge.
     *
     * This is what stops a shoulder folding flat against the back. The default is no limit at all,
     * which is the cheap ragdoll -- limp, and able to reach poses a body cannot.
     */
    val swingLimit: Float = BallSocketConstraint.FREE_SWING,
    /** How far a ball joint rotates about the limb it holds, in radians; `null` twists freely. */
    val twistLimit: ClosedFloatingPointRange<Float>? = null,
)

/**
 * A jointed set of bodies that goes limp under gravity.
 *
 * Built above [PhysicsWorld] out of bodies and constraints rather than bound to Jolt's own
 * `Ragdoll`, for the reason the character controller is: **JoltC exposes no ragdoll at all**, so a
 * binding to it would work on three targets and throw on iOS. A ragdoll *is* bodies and
 * constraints; Jolt's class is convenience over that, not a capability, and the convenience is
 * cheaper to lose than the target.
 *
 * Ball joints are [BallSocketConstraint]s, so a shoulder can be told how far it bends and twists.
 * Whoever describes the ragdoll owns those limits: the default is none, which is limp and can reach
 * poses a body cannot, and [humanoidRagdoll] is the worked example of what real ones look like.
 *
 * **Whoever builds one owns [dispose].** Nothing else destroys the bodies, and a ragdoll dropped
 * without it leaves a pile of limbs simulating where the character died.
 */
class Ragdoll(
    private val world: PhysicsWorld,
    limbs: List<RagdollLimb>,
    joints: List<RagdollJoint>,
    layer: CollisionLayer = CollisionLayers.Moving,
) {
    /** One body per limb, in the order they were described. */
    val bodies: List<BodyHandle> = limbs.map { limb ->
        world.createBody(limb.shape, limb.position, limb.rotation, MotionType.DYNAMIC, layer)
    }

    private val constraints: List<ConstraintHandle> = joints.map { joint ->
        require(joint.parent in bodies.indices && joint.child in bodies.indices) {
            "a joint names limbs outside the ragdoll: ${joint.parent} -> ${joint.child}"
        }
        val parent = bodies[joint.parent]
        val child = bodies[joint.child]
        world.createConstraint(
            if (joint.hingeAxis != null) {
                HingeConstraint(parent, child, joint.anchor, joint.hingeAxis, joint.limits)
            } else {
                BallSocketConstraint(
                    bodyA = parent,
                    bodyB = child,
                    point = joint.anchor,
                    // Down the bone: from the joint out towards the limb it carries. That is what
                    // the swing cone opens around and what the twist turns about, so deriving it
                    // from the pose saves every caller restating what the geometry already says.
                    twistAxis = limbs[joint.child].position - joint.anchor,
                    swingLimit = joint.swingLimit,
                    twistLimit = joint.twistLimit,
                )
            },
        )
    }

    // Last known pose per limb, seeded from where the ragdoll was built. Retained rather than read
    // fresh because PhysicsWorld.forEachBodyTransform reports only what a backend has awake, and a
    // settled limb stops being awake -- so a caller reading straight from the world watches limbs
    // vanish one by one as the body comes to rest. Held here so every consumer gets that right
    // rather than each rediscovering it.
    private val positions = limbs.map { Vec3f(it.position.x, it.position.y, it.position.z) }
    private val rotations = limbs.map { Quat(it.rotation.x, it.rotation.y, it.rotation.z, it.rotation.w) }

    /**
     * Visits every limb's current pose, for a caller writing them into transforms.
     *
     * **The pose values are scratch and are reused between limbs**, the same shape and for the same
     * reason as [PhysicsWorld.forEachBodyTransform]: a ragdoll read every frame that allocated a
     * vector and a quaternion per limb would produce garbage at the rate the game is played.
     *
     * Every limb, every call, including ones that have settled -- a settled limb reports the pose
     * it came to rest in.
     */
    fun forEachLimb(action: (index: Int, position: Vec3f, rotation: Quat) -> Unit) {
        world.forEachBodyTransform { handle, position, rotation ->
            val index = bodies.indexOf(handle)
            if (index >= 0) {
                positions[index].set(position)
                // Copied component by component, never assigned: the rotation handed to this
                // callback is scratch and the next body overwrites it, so holding the reference
                // would leave every limb sharing whichever one happened to be visited last.
                rotations[index].x = rotation.x
                rotations[index].y = rotation.y
                rotations[index].z = rotation.z
                rotations[index].w = rotation.w
            }
        }
        positions.indices.forEach { index -> action(index, positions[index], rotations[index]) }
    }

    /** Whether every limb has settled, so a caller can stop reading it. */
    val isAtRest: Boolean get() = bodies.none { world.isActive(it) }

    /**
     * Destroys the constraints and then the bodies.
     *
     * Constraints first is not tidiness: Jolt does not detach one when a body it references is
     * destroyed, and `destroyBody` cleans up after itself only because the backend was made to.
     * Doing it in this order keeps the ragdoll's teardown independent of that.
     */
    fun dispose() {
        constraints.forEach(world::destroyConstraint)
        bodies.forEach(world::destroyBody)
    }
}
