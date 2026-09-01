/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.ContactPhase
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a solid body can be asked to report its contacts, and is silent until it is.
 *
 * Both halves matter. Without the first there is nothing to hang a hit sound or impact damage on --
 * only sensors reported, and a sensor is the thing you pass *through*. Without the second every
 * settled pile of crates queues an event per touching pair per step, forever, for events nobody
 * reads.
 */
class JoltSolidContactTest {

    /** A floor, and a sphere dropped onto it from a height that guarantees a landing. */
    private suspend fun droppedSphere(): Triple<PhysicsWorld, BodyHandle, BodyHandle> {
        val world = createJoltPhysicsWorld()
        val floor = world.createBody(
            BoxShape(Vec3f(5f, 0.5f, 5f)),
            Vec3f(0f, -0.5f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        val sphere = world.createBody(
            SphereShape(0.5f),
            Vec3f(0f, 2f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )
        return Triple(world, floor, sphere)
    }

    private fun PhysicsWorld.collectContacts(steps: Int): MutableList<Pair<Long, ContactPhase>> {
        val seen = mutableListOf<Pair<Long, ContactPhase>>()
        repeat(steps) {
            step(1f / 60f)
            drainContacts { event -> seen += event.a.id to event.phase }
        }
        return seen
    }

    @Test
    fun aSolidBodyReportsItsContactsOnceAskedTo() = runTest {
        val (world, _, sphere) = droppedSphere()
        try {
            world.setContactReporting(sphere, true)

            val seen = world.collectContacts(steps = 120)

            assertTrue(
                seen.any { it.second == ContactPhase.BEGAN },
                "a sphere landing on a floor reported no contact at all",
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aSolidBodyIsSilentUntilItIsAskedTo() = runTest {
        val (world, _, _) = droppedSphere()
        try {
            // The control for the test above, and the behaviour every existing scene relies on:
            // nothing was asked for, so nothing is reported no matter what lands on what.
            val seen = world.collectContacts(steps = 120)

            assertEquals(emptyList(), seen, "an unasked-for body reported contacts anyway")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun reportingCanBeTurnedBackOff() = runTest {
        val (world, _, sphere) = droppedSphere()
        try {
            world.setContactReporting(sphere, true)
            world.collectContacts(steps = 120)

            world.setContactReporting(sphere, false)
            // Nudged, so it lifts off and lands again -- otherwise a resting body produces no new
            // transitions and this would pass without the switch doing anything.
            world.addImpulse(sphere, Vec3f(0f, 8f, 0f))
            val afterOff = world.collectContacts(steps = 180)

            assertEquals(emptyList(), afterOff, "contacts kept arriving after reporting was turned off")
        } finally {
            world.destroy()
        }
    }
}
