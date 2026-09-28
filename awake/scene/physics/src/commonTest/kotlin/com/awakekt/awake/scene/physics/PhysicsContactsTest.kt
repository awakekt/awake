/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [PhysicsSystem] drains the world's contacts once per step and republishes them, so more than
 * one system can react to the same sensor event.
 */
class PhysicsContactsTest {
    private val world = World()
    private val physics = RecordingPhysicsWorld()
    private val system = PhysicsSystem(physics)

    private fun body(): Entity = world.create().also { entity ->
        world.add(entity, Transform())
        world.add(entity, PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.DYNAMIC))
    }

    private fun handleOf(entity: Entity): BodyHandle = world.get<PhysicsBody>(entity)!!.handle!!

    private fun step() = system.update(world, 1f / 60f)

    @Test
    fun aStepsContactsArePublishedWithTheirEntities() {
        val sensor = body()
        val box = body()
        step()
        physics.pendingContacts += ContactEvent(handleOf(sensor), handleOf(box), ContactPhase.BEGAN)

        step()

        assertEquals(
            listOf(PhysicsContact(handleOf(sensor), handleOf(box), sensor, box, ContactPhase.BEGAN)),
            system.contacts,
        )
    }

    @Test
    fun everyReaderSeesTheSameContacts() {
        val sensor = body()
        val box = body()
        step()
        physics.pendingContacts += ContactEvent(handleOf(sensor), handleOf(box), ContactPhase.BEGAN)
        step()

        // Two consumers in the same fixed step: reading does not consume.
        val first = system.contacts.toList()
        val second = system.contacts.toList()
        assertEquals(1, first.size)
        assertEquals(first, second)
    }

    @Test
    fun contactsLastOneStep() {
        val sensor = body()
        val box = body()
        step()
        physics.pendingContacts += ContactEvent(handleOf(sensor), handleOf(box), ContactPhase.BEGAN)
        step()

        step()

        assertEquals(emptyList(), system.contacts)
    }

    @Test
    fun aBodyThisSystemDidNotBuildHasNoEntity() {
        val box = body()
        step()
        val foreign = BodyHandle(999L)
        physics.pendingContacts += ContactEvent(foreign, handleOf(box), ContactPhase.ENDED)

        step()

        val contact = system.contacts.single()
        assertNull(contact.entityA)
        assertEquals(box, contact.entityB)
    }

    @Test
    fun aDestroyedEntityIsNotReported() {
        val sensor = body()
        val box = body()
        step()
        val boxHandle = handleOf(box)
        world.destroy(box)
        physics.pendingContacts += ContactEvent(handleOf(sensor), boxHandle, ContactPhase.ENDED)

        step()

        val contact = system.contacts.single()
        assertEquals(sensor, contact.entityA)
        assertNull(contact.entityB, "an ENDED event for a destroyed body must not name its dead entity")
    }

    @Test
    fun entityForFindsTheEntityABodyWasBuiltFor() {
        val box = body()
        step()
        assertEquals(box, system.entityFor(handleOf(box)))
        assertNull(system.entityFor(BodyHandle(999L)))
    }
}
