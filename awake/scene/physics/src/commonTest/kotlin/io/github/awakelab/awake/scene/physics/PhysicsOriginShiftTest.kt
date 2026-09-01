/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.Constraint
import io.github.awakelab.awake.physics.ConstraintHandle
import io.github.awakelab.awake.physics.BodyTransform
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.RaycastHit
import io.github.awakelab.awake.physics.ShapeCastHit
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.world.FloatingOriginSystem
import io.github.awakelab.awake.scene.world.StreamObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A physics body has to move with the world when the origin shifts, or it does not move at all.
 *
 * `FloatingOriginSystem` rebases transforms; `PhysicsSystem` then writes the simulation's answer
 * back over them every frame. So for anything with a body, shifting the transform alone lasts
 * exactly one frame -- the prop snaps back to where physics still has it, and the scene splits
 * into a world that moved and objects that did not. This asserts the two halves agree.
 */
class PhysicsOriginShiftTest {

    @Test
    fun aShiftReachesTheSimulation() {
        val world = World()
        world.spawnObserver(x = 5_000f)
        val physics = RecordingPhysicsWorld()
        val system = FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM)
        system.addListener(PhysicsOriginShiftListener(physics))

        system.update(world, DELTA)

        assertEquals(
            1,
            physics.shifts.size,
            "The simulation was never told the world moved, so every body stays at the old " +
                "origin and drags its transform back there on the next step.",
        )
        assertEquals(-5 * QUANTUM, physics.shifts.single().x)
    }

    @Test
    fun aShiftMovesTheBodyByTheSameOffsetAsTheTransform() {
        val world = World()
        val observer = world.spawnObserver(x = 5_000f)
        val physics = RecordingPhysicsWorld()
        val system = FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM)
        system.addListener(PhysicsOriginShiftListener(physics))

        system.update(world, DELTA)

        val transformMoved = 5_000f - requireNotNull(world.get<Transform>(observer)).position.x
        assertEquals(
            -transformMoved,
            physics.shifts.single().x,
            TOLERANCE,
            "Transforms and bodies must move by the SAME offset. Any difference is a body " +
                "sliding relative to the world it is standing on, which reads as physics drift " +
                "rather than as a rebase.",
        )
    }

    @Test
    fun aSceneBelowTheThresholdTellsPhysicsNothing() {
        val world = World()
        world.spawnObserver(x = THRESHOLD - 1f)
        val physics = RecordingPhysicsWorld()
        val system = FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM)
        system.addListener(PhysicsOriginShiftListener(physics))

        system.update(world, DELTA)

        assertTrue(
            physics.shifts.isEmpty(),
            "No shift happened, so waking every body in the simulation would be pure cost.",
        )
    }

    private fun World.spawnObserver(x: Float) = create().also { entity ->
        add(entity, Transform(position = Vec3f(x, 0f, 0f)))
        add(entity, StreamObserver)
    }

    /** Records what it was asked to move; everything else is unreachable in these tests. */
    private class RecordingPhysicsWorld : PhysicsWorld {
        override val layers: CollisionLayers = CollisionLayers.Default

        val shifts = mutableListOf<Vec3f>()

        override fun shiftOrigin(offset: Vec3f) {
            shifts += Vec3f(offset.x, offset.y, offset.z)
        }

        override fun createBody(
            shape: PhysicsShape,
            position: Vec3f,
            rotation: Quat,
            motionType: MotionType,
            layer: CollisionLayer,
            sensor: Boolean,
        ): BodyHandle = error("not needed for this test")

        override fun destroyBody(handle: BodyHandle) = error("not needed for this test")
        override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) =
            error("not needed for this test")

        override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) =
            error("not needed for this test")

        override fun getLinearVelocity(handle: BodyHandle): Vec3f = error("not needed for this test")
        override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = error("not needed for this test")
        override fun moveKinematic(
            handle: BodyHandle,
            position: Vec3f,
            rotation: Quat,
            deltaTime: Float,
        ) = error("not needed for this test")

        override fun step(deltaTime: Float) = Unit
        override fun forEachBodyTransform(
            action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
        ) = Unit
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
        ): ShapeCastHit? =
            error("not needed for this test")
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

    private companion object {
        const val THRESHOLD = 2_048f
        const val QUANTUM = 1_024f
        const val DELTA = 1f / 60f
        const val TOLERANCE = 0.001f
    }
}
