/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BodyTransform
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.Constraint
import io.github.awakelab.awake.physics.ConstraintHandle
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.RaycastHit
import io.github.awakelab.awake.physics.ShapeCastHit
import io.github.awakelab.awake.physics.SphereShape
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.scene.physics.PhysicsSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhysicsSystemTest {
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
            return BodyHandle(nextHandleId++)
        }

        /** The motion type of every body actually created, in order. */
        val builtWith = mutableListOf<MotionType>()

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
        ) = Unit

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
}
