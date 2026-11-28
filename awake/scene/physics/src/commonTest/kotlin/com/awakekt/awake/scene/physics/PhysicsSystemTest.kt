/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BodyTransform
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.Constraint
import com.awakekt.awake.physics.ConstraintHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsCapabilityException
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.RaycastHit
import com.awakekt.awake.physics.ShapeCastHit
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhysicsSystemTest {
    @Test
    fun aLegacyBackendDelegatesAllButRejectsRestrictedMotionBeforeCreatingABody() {
        val backend = FakePhysicsWorld()
        backend.createBody(
            SphereShape(0.5f),
            Vec3f(),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
            degreesOfFreedom = DegreesOfFreedom.ALL,
        )
        assertEquals(1, backend.createBodyCallCount)
        assertFailsWith<PhysicsCapabilityException> {
            backend.createBody(
                SphereShape(0.5f),
                Vec3f(),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
                degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
        }
        assertEquals(1, backend.createBodyCallCount)
    }

    /** Pure ECS-wiring fake -- no real simulation, just records calls and hands back a
     * scripted [syncTransforms] result so this test doesn't need jolt-jni. */
    private class FakePhysicsWorld : PhysicsWorld {
        var createBodyCallCount = 0
            private set
        var stepCallCount = 0
            private set
        var syncTransformsCallCount = 0
            private set
        private var nextHandleId = 0L
        override val layers: CollisionLayers = CollisionLayers.Default

        var scriptedTransforms: List<BodyTransform> = emptyList()

        override fun createBody(
            shape: PhysicsShape,
            position: Vec3f,
            rotation: Quat,
            motionType: MotionType,
            layer: CollisionLayer,
            sensor: Boolean,
        ): BodyHandle {
            createBodyCallCount++
            builtWith += motionType
            createdPositions += position.copy()
            createdRotations += rotation.copy()
            return BodyHandle(nextHandleId++)
        }

        /** The motion type of every body actually created, in order. */
        val builtWith = mutableListOf<MotionType>()
        val createdPositions = mutableListOf<Vec3f>()
        val createdRotations = mutableListOf<Quat>()
        val kinematicMoves = mutableListOf<KinematicMove>()

        data class KinematicMove(
            val handle: BodyHandle,
            val position: Vec3f,
            val rotation: Quat,
            val deltaTime: Float,
        )

        val destroyed = mutableListOf<BodyHandle>()

        /** Recorded, not ignored: a rebuild that forgets to destroy leaks a body that keeps
         * simulating, and the only visible difference is a second handle appearing. */
        override fun destroyBody(handle: BodyHandle) {
            destroyed += handle
        }

        /** Unused here: PhysicsOriginShiftTest covers the rebase path. */
        override fun shiftOrigin(offset: Vec3f) = Unit

        override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
        override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) = Unit
        override fun getLinearVelocity(handle: BodyHandle): Vec3f = Vec3f(0f, 0f, 0f)
        override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = Unit
        override fun moveKinematic(
            handle: BodyHandle,
            position: Vec3f,
            rotation: Quat,
            deltaTime: Float,
        ) {
            kinematicMoves += KinematicMove(handle, position.copy(), rotation.copy(), deltaTime)
        }

        override fun step(deltaTime: Float) {
            stepCallCount++
        }

        override fun forEachBodyTransform(
            action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
        ) {
            syncTransformsCallCount++
            scriptedTransforms.forEach { action(it.handle, it.position, it.rotation) }
        }

        override fun raycast(
            origin: Vec3f,
            direction: Vec3f,
            maxDistance: Float,
            onlyLayer: CollisionLayer?,
        ): RaycastHit? =
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

    @Test
    fun createsBodyOnceLazilyAndSyncsTransformBackToEntity() {
        val world = World()
        val entity = world.create()
        val transform = Transform(position = Vec3f(0f, 10f, 0f))
        world.add(entity, transform)
        val physicsBody =
            PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.DYNAMIC)
        world.add(entity, physicsBody)
        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)

        system.update(world, 1f / 60f)

        assertEquals(1, physicsWorld.createBodyCallCount)
        assertEquals(1, physicsWorld.stepCallCount)
        assertEquals(1, physicsWorld.syncTransformsCallCount)
        val handle = physicsBody.handle
        assertNotNull(handle, "expected PhysicsSystem to assign a handle after creating the body")

        // Second update must not create a second body for the same entity.
        system.update(world, 1f / 60f)
        assertEquals(1, physicsWorld.createBodyCallCount)
        assertEquals(2, physicsWorld.stepCallCount)
        assertEquals(2, physicsWorld.syncTransformsCallCount)
        assertEquals(handle, physicsBody.handle)

        physicsWorld.scriptedTransforms = listOf(
            BodyTransform(
                handle = handle,
                position = Vec3f(1f, 2f, 3f),
                rotation = Quat.fromEuler(Vec3f(0f, 0.5f, 0f)),
            ),
        )
        system.update(world, 1f / 60f)

        assertEquals(1f, transform.position.x)
        assertEquals(2f, transform.position.y)
        assertEquals(3f, transform.position.z)
        assertEquals(0.5f, transform.rotation.y)
    }

    @Test
    fun ignoresSyncedTransformsForUnknownHandles() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(0f, 0f, 0f)))
        world.add(
            entity,
            PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC),
        )
        val physicsWorld = FakePhysicsWorld()
        physicsWorld.scriptedTransforms = listOf(
            BodyTransform(
                handle = BodyHandle(999L),
                position = Vec3f(5f, 5f, 5f),
                rotation = Quat.IDENTITY,
            ),
        )
        val system = PhysicsSystem(physicsWorld)

        system.update(world, 1f / 60f)

        val transform = world.get<Transform>(entity)!!
        assertTrue(transform.position.x == 0f && transform.position.y == 0f && transform.position.z == 0f)
    }

    /**
     * The gap the scene-editor audit recorded as blocking a `PhysicsBody` inspector: the body was
     * created once and never rebuilt, so editing `motionType` would change the component while the
     * simulation kept running the old body -- a control that silently does nothing.
     */
    @Test
    fun changingMotionTypeRebuildsTheBodyAndDestroysTheOldOne() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(0f, 10f, 0f)))
        val physicsBody =
            PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.DYNAMIC)
        world.add(entity, physicsBody)
        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)

        system.update(world, 1f / 60f)
        val first = physicsBody.handle
        assertNotNull(first)

        // What the inspector does.
        physicsBody.motionType = MotionType.STATIC
        system.update(world, 1f / 60f)

        assertEquals(2, physicsWorld.createBodyCallCount, "the edit did not rebuild the body")
        assertEquals(listOf(first), physicsWorld.destroyed, "the old body was left simulating")
        assertEquals(
            listOf(MotionType.DYNAMIC, MotionType.STATIC),
            physicsWorld.builtWith,
            "the rebuilt body was not created with the edited motion type",
        )
        assertNotNull(physicsBody.handle)
        assertTrue(physicsBody.handle != first, "the component still points at the destroyed body")

        // Steady state again: an unchanged motion type must not rebuild every frame.
        system.update(world, 1f / 60f)
        assertEquals(2, physicsWorld.createBodyCallCount, "a body was rebuilt without an edit")
    }

    @Test
    fun settledStaticBodiesRespondToInPlaceMotionEditExternalHandleClearAndNewEntity() {
        val world = World()
        val staticBodies = List(20) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(index.toFloat(), 0f, 0f)))
            PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC).also {
                world.add(entity, it)
            }
        }
        val dynamicEntity = world.create()
        val dynamicTransform = Transform(position = Vec3f(0f, 5f, 0f))
        world.add(dynamicEntity, dynamicTransform)
        val dynamicBody = PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.DYNAMIC)
        world.add(dynamicEntity, dynamicBody)

        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)

        system.update(world, 1f / 60f)
        assertEquals(21, physicsWorld.createBodyCallCount)

        // Steady-state step with only the dynamic body reporting a pose: interpolate updates it and skips static bodies.
        val dynamicHandle = assertNotNull(dynamicBody.handle)
        physicsWorld.scriptedTransforms = listOf(
            BodyTransform(dynamicHandle, Vec3f(0f, 4f, 0f), Quat.IDENTITY),
        )
        system.update(world, 1f / 60f)
        physicsWorld.scriptedTransforms = listOf(
            BodyTransform(dynamicHandle, Vec3f(0f, 2f, 0f), Quat.IDENTITY),
        )
        system.update(world, 1f / 60f)
        system.interpolate(world, 0.5f)
        assertEquals(3f, dynamicTransform.position.y, 1e-4f)

        // 1) In-place motionType edit on a settled static body wakes the sync path and rebuilds it.
        val edited = staticBodies[5]
        val previousHandle = assertNotNull(edited.handle)
        edited.motionType = MotionType.DYNAMIC
        system.update(world, 1f / 60f)
        assertEquals(22, physicsWorld.createBodyCallCount)
        assertTrue(previousHandle in physicsWorld.destroyed)

        // 2) Adding a new entity wakes the sync path via family.changes.
        val addedEntity = world.create()
        world.add(addedEntity, Transform(position = Vec3f(50f, 0f, 0f)))
        val addedBody = PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC)
        world.add(addedEntity, addedBody)
        system.update(world, 1f / 60f)
        assertEquals(23, physicsWorld.createBodyCallCount)
        assertNotNull(addedBody.handle)

        // 3) Clearing a handle externally wakes the sync path via handle edit tracking.
        addedBody.handle = null
        system.update(world, 1f / 60f)
        assertEquals(24, physicsWorld.createBodyCallCount)
        assertNotNull(addedBody.handle)

        // 4) Destroying a posed body removes it cleanly from the posed interpolation list.
        system.destroyBody(world, dynamicEntity)
        world.destroy(dynamicEntity)
        physicsWorld.scriptedTransforms = emptyList()
        system.update(world, 1f / 60f)
        system.interpolate(world, 0.5f)
        assertEquals(24, physicsWorld.createBodyCallCount)
    }

    @Test
    fun childStaticColliderUsesParentWorldPositionAndRotationAndDebugLinesMatch() {
        val world = World()
        val parent = world.create()
        val halfPi = kotlin.math.PI.toFloat() * 0.5f
        world.add(
            parent,
            Transform(
                position = Vec3f(10f, 2f, -4f),
                rotation = Vec3f(0f, halfPi, 0f),
                scale = Vec3f(2f, 1f, 2f),
            ),
        )
        val child = world.create()
        world.add(
            child,
            Transform(
                position = Vec3f(0f, 1f, 1.5f),
                parent = parent,
            ),
        )
        val body = PhysicsBody(shape = SphereShape(radius = 0.5f), motionType = MotionType.STATIC)
        world.add(child, body)

        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)
        system.update(world, 1f / 60f)

        // Parent yaw +90 deg maps local +Z (1.5 * scale 2 = 3.0) onto world +X -> (13, 3, -4).
        val builtPos = physicsWorld.createdPositions.single()
        assertEquals(13f, builtPos.x, 1e-4f)
        assertEquals(3f, builtPos.y, 1e-4f)
        assertEquals(-4f, builtPos.z, 1e-4f)

        val expectedRot = Quat.fromEuler(Vec3f(0f, halfPi, 0f))
        val builtRot = physicsWorld.createdRotations.single()
        val dot = kotlin.math.abs(
            builtRot.x * expectedRot.x + builtRot.y * expectedRot.y +
                builtRot.z * expectedRot.z + builtRot.w * expectedRot.w,
        )
        assertEquals(1f, dot, 1e-4f, "child collider rotation must inherit parent world rotation")

        // Debug wireframes must agree with the body's world position rather than local (0, 1, 1.5).
        val debugLines = physicsDebugLines(world)
        assertTrue(debugLines.isNotEmpty())
        val avgX = debugLines.map { (it.start.x + it.end.x) * 0.5f }.average().toFloat()
        val avgY = debugLines.map { (it.start.y + it.end.y) * 0.5f }.average().toFloat()
        val avgZ = debugLines.map { (it.start.z + it.end.z) * 0.5f }.average().toFloat()
        assertEquals(13f, avgX, 0.05f)
        assertEquals(3f, avgY, 0.05f)
        assertEquals(-4f, avgZ, 0.05f)
    }

    @Test
    fun kinematicBodyDrivesMoveKinematicWhenParentOrOwnTransformMovesAndPreservesChildLocalTransform() {
        val world = World()
        val platform = world.create()
        val platformTransform = Transform(position = Vec3f(0f, 3f, -10f))
        world.add(platform, platformTransform)

        val collider = world.create()
        val colliderTransform = Transform(position = Vec3f(0f, 0.25f, 0f), parent = platform)
        world.add(collider, colliderTransform)
        val body = PhysicsBody(shape = SphereShape(radius = 0.5f), motionType = MotionType.KINEMATIC)
        world.add(collider, body)

        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)
        val step = 1f / 60f

        // Step 1: creates kinematic body at world (0, 3.25, -10), does not moveKinematic while stationary.
        system.update(world, step)
        val handle = assertNotNull(body.handle)
        assertEquals(Vec3f(0f, 3.25f, -10f), physicsWorld.createdPositions.single())
        assertTrue(physicsWorld.kinematicMoves.isEmpty())

        // Step 2: keyframe animation moves the parent platform forward; PhysicsSystem drives Jolt via moveKinematic.
        platformTransform.position.set(0f, 3.5f, -12f)
        physicsWorld.scriptedTransforms = listOf(BodyTransform(handle, Vec3f(0f, 3.75f, -12f), Quat.IDENTITY))
        system.update(world, step)

        assertEquals(1, physicsWorld.kinematicMoves.size)
        assertEquals(handle, physicsWorld.kinematicMoves[0].handle)
        assertEquals(Vec3f(0f, 3.75f, -12f), physicsWorld.kinematicMoves[0].position)
        assertEquals(step, physicsWorld.kinematicMoves[0].deltaTime)
        // Parented child's local transform MUST remain (0, 0.25, 0), never overwritten with world (0, 3.75, -12).
        assertEquals(Vec3f(0f, 0.25f, 0f), colliderTransform.position)

        // Step 3: platform pauses at endpoint -> one final stationary moveKinematic clears velocity in Jolt.
        system.update(world, step)
        assertEquals(2, physicsWorld.kinematicMoves.size)
        assertEquals(Vec3f(0f, 3.75f, -12f), physicsWorld.kinematicMoves[1].position)

        // Step 4: still parked -> no redundant moveKinematic calls.
        system.update(world, step)
        assertEquals(2, physicsWorld.kinematicMoves.size)
    }

    @Test
    fun incrementalBodySyncAvoidsFullScanOnPureAdditionsAndRemovals() {
        val world = World()
        val initialEntities = List(100) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(index.toFloat(), 0f, 0f)))
            world.add(entity, PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC))
            entity
        }
        val physicsWorld = FakePhysicsWorld()
        val system = PhysicsSystem(physicsWorld)

        // Initial step builds all 100 bodies.
        system.update(world, 1f / 60f)
        assertEquals(100, physicsWorld.createBodyCallCount)
        assertEquals(100, system.lastBodySyncScannedCount)

        // Steady state scans 0 bodies.
        system.update(world, 1f / 60f)
        assertEquals(0, system.lastBodySyncScannedCount)

        // Pure append of 3 new static colliders scans only those 3 tail entries, not all 103.
        val appended = List(3) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(200f + index, 0f, 0f)))
            val body = PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC)
            world.add(entity, body)
            entity to body
        }
        system.update(world, 1f / 60f)
        assertEquals(103, physicsWorld.createBodyCallCount)
        assertEquals(3, system.lastBodySyncScannedCount, "pure appends must scan only newly appended entries")
        appended.forEach { (_, body) -> assertNotNull(body.handle) }

        // Pure removal via destroyBody + world.destroy scans 0 settled bodies.
        for (i in 0 until 5) {
            system.destroyBody(world, initialEntities[i])
            world.destroy(initialEntities[i])
        }
        system.update(world, 1f / 60f)
        assertEquals(103, physicsWorld.createBodyCallCount)
        assertEquals(0, system.lastBodySyncScannedCount, "pure removals must not rescan settled bodies")

        // Destroying a body while keeping its entity alive recreates only that detached entity.
        val keptEntity = initialEntities[10]
        val keptBody = assertNotNull(world.get<PhysicsBody>(keptEntity))
        val oldHandle = assertNotNull(keptBody.handle)
        system.destroyBody(world, keptEntity)
        system.update(world, 1f / 60f)
        assertEquals(104, physicsWorld.createBodyCallCount)
        assertEquals(1, system.lastBodySyncScannedCount, "rebuilding a kept detached body scans only that entity")
        assertNotNull(keptBody.handle)
        assertTrue(keptBody.handle != oldHandle)

        // Mixed remove + add in the same step falls back to a full scan and creates the new body cleanly.
        system.destroyBody(world, initialEntities[11])
        world.destroy(initialEntities[11])
        val mixedEntity = world.create()
        world.add(mixedEntity, Transform(position = Vec3f(300f, 0f, 0f)))
        val mixedBody = PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.STATIC)
        world.add(mixedEntity, mixedBody)
        system.update(world, 1f / 60f)
        assertEquals(105, physicsWorld.createBodyCallCount)
        assertNotNull(mixedBody.handle)
    }
}
