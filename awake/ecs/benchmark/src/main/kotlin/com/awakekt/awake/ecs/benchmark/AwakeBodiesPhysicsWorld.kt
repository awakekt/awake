/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BodyTransformVisitor
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.Constraint
import com.awakekt.awake.physics.ConstraintHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.RaycastHit
import com.awakekt.awake.physics.ShapeCastHit

/**
 * A [PhysicsWorld] that simulates nothing and reports every body as awake on every step, through one
 * reused position and rotation as the real backends do. It isolates what `PhysicsSystem` itself costs
 * per step from what a solver costs: the worst case for the system, a region where nothing settles.
 */
@Suppress("TooManyFunctions") // One per PhysicsWorld member; the count is the interface's.
class AwakeBodiesPhysicsWorld : PhysicsWorld {
    override val layers: CollisionLayers = CollisionLayers.Default
    private var handles = LongArray(INITIAL_CAPACITY)
    private var count = 0
    private var next = 1L
    private val scratchPosition = Vec3f(0f, 0f, 0f)
    private val scratchRotation = Quat()

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle {
        if (count == handles.size) handles = handles.copyOf(count * 2)
        // Spread like a backend's ids, which are not the entity order.
        val id = (next++ * ID_SPREAD) and Long.MAX_VALUE
        handles[count++] = id
        return BodyHandle(id)
    }

    override fun destroyBody(handle: BodyHandle) {
        val index = (0 until count).firstOrNull { handles[it] == handle.id } ?: return
        handles[index] = handles[--count]
    }

    override fun step(deltaTime: Float) = Unit

    override fun forEachBodyTransform(action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit) =
        visitBodyTransforms { id, position, rotation -> action(BodyHandle(id), position, rotation) }

    // As every Awake backend does since #1428: the handle goes out as its raw id, so nothing is boxed per body.
    override fun visitBodyTransforms(visitor: BodyTransformVisitor) {
        for (index in 0 until count) {
            scratchPosition.set(index.toFloat(), 1f, 0f)
            visitor.visit(handles[index], scratchPosition, scratchRotation)
        }
    }

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
    override fun getLinearVelocity(handle: BodyHandle): Vec3f = Vec3f(0f, 0f, 0f)
    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = Unit
    override fun moveKinematic(handle: BodyHandle, position: Vec3f, rotation: Quat, deltaTime: Float) = Unit
    override fun shiftOrigin(offset: Vec3f) = Unit

    override fun raycast(origin: Vec3f, direction: Vec3f, maxDistance: Float, onlyLayer: CollisionLayer?): RaycastHit? =
        null

    override fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastHit? = null

    override fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer?,
        onOverlap: (BodyHandle) -> Unit,
    ) = Unit

    override fun setActive(handle: BodyHandle, active: Boolean) = Unit
    override fun isActive(handle: BodyHandle): Boolean = true
    override fun createConstraint(constraint: Constraint): ConstraintHandle = ConstraintHandle(0)
    override fun destroyConstraint(handle: ConstraintHandle) = Unit
    override fun applyBuoyancy(handle: BodyHandle, surfaceY: Float, buoyancy: Buoyancy, deltaTime: Float) = Unit
    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) = Unit
    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) = Unit
    override fun drainContacts(action: (ContactEvent) -> Unit) = Unit
    override fun destroy() = Unit

    private companion object {
        const val INITIAL_CAPACITY = 256
        const val ID_SPREAD = 0x9E3779B97F4AL
    }
}
