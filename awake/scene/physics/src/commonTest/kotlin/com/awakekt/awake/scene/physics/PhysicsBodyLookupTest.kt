/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyTransform
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** [PhysicsSystem] finds each body by entity slot and by handle, and keeps the two straight (#1428). */
class PhysicsBodyLookupTest {
    private fun World.body(y: Float): PhysicsBody {
        val entity = create()
        add(entity, Transform(position = Vec3f(0f, y, 0f)))
        return PhysicsBody(SphereShape(radius = 1f), MotionType.DYNAMIC).also { add(entity, it) }
    }

    @Test
    fun eachReportedPoseReachesItsOwnEntity() {
        val world = World()
        val bodies = List(200) { world.body(it.toFloat()) }
        val physics = RecordingPhysicsWorld()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)

        physics.scriptedTransforms = bodies.mapIndexed { index, body ->
            BodyTransform(assertNotNull(body.handle), Vec3f(index.toFloat(), -1f, 0f), Quat.IDENTITY)
        }.reversed()
        system.update(world, STEP)

        world.queryEach<Transform, PhysicsBody> { entity, transform, body ->
            assertEquals(bodies.indexOf(body).toFloat(), transform.position.x)
            assertEquals(entity, system.entityFor(assertNotNull(body.handle)))
        }
    }

    @Test
    fun aNewEntityInADestroyedEntitysSlotGetsItsOwnBody() {
        val world = World()
        val physics = RecordingPhysicsWorld()
        val system = PhysicsSystem(physics)
        world.body(0f)
        val old = world.query(PhysicsBody::class).single()
        system.update(world, STEP)
        val oldHandle = assertNotNull(world.get<PhysicsBody>(old)?.handle)

        // Destroyed without destroyBody: the body leaks, as documented, but stays known.
        world.destroy(old)
        val replacement = world.body(5f)
        val fresh = world.query(PhysicsBody::class).single()
        assertEquals(old.id, fresh.id, "the test needs the slot reused")
        system.update(world, STEP)

        val newHandle = assertNotNull(replacement.handle)
        assertNotEquals(oldHandle, newHandle)
        assertEquals(old, system.entityFor(oldHandle))
        assertEquals(fresh, system.entityFor(newHandle))

        // A pose for the leaked body must not land on the entity now in its slot.
        physics.scriptedTransforms = listOf(BodyTransform(oldHandle, Vec3f(9f, 9f, 9f), Quat.IDENTITY))
        system.update(world, STEP)
        assertEquals(5f, world.get<Transform>(fresh)!!.position.y)
        assertEquals(2, physics.created.size, "an unchanged body was rebuilt")
    }

    @Test
    fun destroyBodyForgetsTheHandleAndAMotionEditRebuildsOnlyThatBody() {
        val world = World()
        val first = world.body(0f)
        val second = world.body(1f)
        val physics = RecordingPhysicsWorld()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val firstHandle = assertNotNull(first.handle)
        val firstEntity = assertNotNull(system.entityFor(firstHandle))

        second.motionType = MotionType.STATIC
        system.update(world, STEP)
        assertEquals(3, physics.created.size)
        assertEquals(firstHandle, first.handle)

        system.destroyBody(world, firstEntity)
        assertNull(system.entityFor(firstHandle))
        assertNull(first.handle)
    }

    @Test
    fun interpolationBlendsEveryBodyStillKnown() {
        val world = World()
        val bodies = List(3) { world.body(0f) }
        val physics = RecordingPhysicsWorld()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        physics.scriptedTransforms = bodies.map { BodyTransform(it.handle!!, Vec3f(0f, 2f, 0f), Quat.IDENTITY) }
        system.update(world, STEP)
        physics.scriptedTransforms = bodies.map { BodyTransform(it.handle!!, Vec3f(0f, 4f, 0f), Quat.IDENTITY) }
        system.update(world, STEP)

        system.interpolate(world, 0.5f)

        world.queryEach<Transform, PhysicsBody> { _, transform, _ -> assertEquals(3f, transform.position.y) }
    }

    private companion object {
        const val STEP = 1f / 60f
    }
}
