/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.streaming

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BodyTransform
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.Constraint
import io.github.awakelab.awake.physics.ConstraintHandle
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.RaycastHit
import io.github.awakelab.awake.physics.ShapeCastHit

/**
 * A [PhysicsWorld] that records what was created and destroyed, and simulates nothing.
 *
 * Shared rather than nested in one test because the pairing of create against destroy is what
 * every streaming test asserts, and because this interface keeps growing -- a fake per test file
 * means every new method is a hand-edit in each of them.
 */
/** Records what was created and destroyed; the streamer's whole job is the pairing. */
internal class RecordingPhysicsWorld : PhysicsWorld {
    override val layers: CollisionLayers = CollisionLayers.Default
    val created = mutableListOf<BodyHandle>()
    val destroyed = mutableListOf<BodyHandle>()
    val layersUsed = mutableListOf<CollisionLayer>()
    val positions = mutableListOf<Vec3f>()
    private var next = 1L

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle {
        layersUsed += layer
        positions += Vec3f(position.x, position.y, position.z)
        return BodyHandle(next++).also { created += it }
    }

    override fun destroyBody(handle: BodyHandle) {
        destroyed += handle
    }

    val liveBodies: List<BodyHandle> get() = created.filterNot { it in destroyed }

    /**
     * What [forEachBodyTransform] reports, for a test that needs a scripted simulation.
     *
     * Empty by default, which is also the honest default: it stands for a world where every body
     * has settled, and a settled body is not reported at all.
     */
    var scriptedTransforms: List<BodyTransform> = emptyList()

    // One pair, reused for every body reported below -- see forEachBodyTransform.
    private val scratchPosition = Vec3f(0f, 0f, 0f)
    private val scratchRotation = Quat()

    override fun step(deltaTime: Float) = Unit

    /**
     * Reports [scriptedTransforms] through a single reused position and rotation.
     *
     * Deliberately scratch, because the real backends are: `PhysicsWorld.forEachBodyTransform`
     * hands out values the next body overwrites, and a fake that hands out fresh ones instead
     * silently passes code that keeps the reference. That is not hypothetical -- the pose
     * interpolation in `PhysicsSystem` held the rotation rather than copying it, and this fake
     * was why its tests did not notice.
     */
    override fun forEachBodyTransform(
        action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
    ) = scriptedTransforms.forEach {
        action(it.handle, scratchPosition.set(it.position), scratchRotation.set(it.rotation))
    }

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
    override fun getLinearVelocity(handle: BodyHandle): Vec3f = Vec3f(0f, 0f, 0f)
    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = Unit
    override fun moveKinematic(
        handle: BodyHandle,
        position: Vec3f,
        rotation: Quat,
        deltaTime: Float,
    ) = Unit

    override fun shiftOrigin(offset: Vec3f) = Unit
    override fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
    ): RaycastHit? = null

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

    override fun isActive(handle: BodyHandle): Boolean = false

    override fun createConstraint(constraint: Constraint): ConstraintHandle =
        ConstraintHandle(0)

    override fun destroyConstraint(handle: ConstraintHandle) = Unit

    override fun applyBuoyancy(
        handle: BodyHandle,
        surfaceY: Float,
        buoyancy: Buoyancy,
        deltaTime: Float,
    ) = Unit

    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) = Unit
    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) = Unit

    override fun drainContacts(action: (ContactEvent) -> Unit) = Unit
    override fun destroy() = Unit
}
