/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.syncTransforms
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sensors and contact events against a real Jolt world.
 *
 * Every claim here is one a fake would happily agree with while the real thing did something else:
 * that the callbacks arrive at all across JNI, that a sensor stops being solid, that Jolt's worker
 * threads do not lose events on the way to the frame thread, and that a destroyed body's id does
 * not carry its reporting to whatever is created next.
 */
class JoltContactEventTest {

    private suspend fun world(gravity: Vec3f = Vec3f(0f, -9.81f, 0f), block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = gravity)
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    private fun PhysicsWorld.box(
        position: Vec3f,
        motionType: MotionType,
        halfExtent: Float = 0.5f,
        sensor: Boolean = false,
    ): BodyHandle = createBody(
        BoxShape(Vec3f(halfExtent, halfExtent, halfExtent)),
        position,
        Quat.IDENTITY,
        motionType,
        sensor = sensor,
    )

    private fun PhysicsWorld.stepAndDrain(steps: Int = 1): List<ContactEvent> = buildList {
        repeat(steps) {
            step(1f / 60f)
            drainContacts { add(it) }
        }
    }

    private fun List<ContactEvent>.involving(handle: BodyHandle, phase: ContactPhase) =
        filter { it.phase == phase && (it.a == handle || it.b == handle) }

    @Test
    fun aBodyFallingIntoASensorIsReported() = runTest {
        world { world ->
            val sensor = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f, sensor = true)
            val faller = world.box(Vec3f(0f, 5f, 0f), MotionType.DYNAMIC)

            val events = world.stepAndDrain(steps = 60)

            val began = events.involving(sensor, ContactPhase.BEGAN)
            assertTrue(began.isNotEmpty(), "the sensor never noticed the box; events were $events")
            assertTrue(
                began.any { it.a == faller || it.b == faller },
                "the event named the sensor but not the box that entered it: $began",
            )
        }
    }

    @Test
    fun aSensorDoesNotHoldUpWhatEntersIt() = runTest {
        world { world ->
            val sensor = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f, sensor = true)
            val faller = world.box(Vec3f(0f, 5f, 0f), MotionType.DYNAMIC)

            world.stepAndDrain(steps = 90)

            // The half of "sensor" a contact event cannot show: it detects without colliding. A
            // trigger volume that quietly stops the player is worse than one that never fires.
            val y = world.syncTransforms().single { it.handle == faller }.position.y
            assertTrue(y < -2f, "the box rested on the sensor instead of passing through: y=$y")
        }
    }

    @Test
    fun leavingASensorIsReportedToo() = runTest {
        world(gravity = Vec3f(0f, 0f, 0f)) { world ->
            val sensor = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f, sensor = true)
            val passer = world.box(Vec3f(-6f, 0f, 0f), MotionType.DYNAMIC)
            world.setLinearVelocity(passer, Vec3f(12f, 0f, 0f))

            val events = world.stepAndDrain(steps = 90)

            assertTrue(
                events.involving(sensor, ContactPhase.BEGAN).isNotEmpty(),
                "the box never entered; events were $events",
            )
            assertTrue(
                events.involving(sensor, ContactPhase.ENDED).isNotEmpty(),
                "the box entered and never left, so a trigger would stay latched: $events",
            )
        }
    }

    @Test
    fun bodiesThatWereNotAskedToReportStaySilent() = runTest {
        world { world ->
            world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f)
            world.box(Vec3f(0f, 3f, 0f), MotionType.DYNAMIC)

            val events = world.stepAndDrain(steps = 90)

            // Jolt reports every touching pair in the scene. Forwarding the ones nothing subscribed
            // to would allocate an event per contact per frame forever.
            assertEquals(emptyList(), events, "an unsubscribed collision produced events")
        }
    }

    @Test
    fun drainingTakesEachEventOnce() = runTest {
        world { world ->
            val sensor = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f, sensor = true)
            world.box(Vec3f(0f, 3f, 0f), MotionType.DYNAMIC)

            val first = world.stepAndDrain(steps = 60)
            val second = buildList { world.drainContacts { add(it) } }

            assertTrue(first.isNotEmpty(), "nothing was reported at all")
            // A drain that left its events behind would replay every pickup on every frame.
            assertEquals(emptyList(), second, "a second drain re-delivered $second")
        }
    }

    @Test
    fun aDestroyedBodyDoesNotPassItsReportingToTheNextOne() = runTest {
        world(gravity = Vec3f(0f, 0f, 0f)) { world ->
            val sensor = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f, sensor = true)
            world.destroyBody(sensor)

            // Jolt hands ids back out. Whatever this is, it never asked to report anything.
            val reused = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC, halfExtent = 2f)
            val faller = world.box(Vec3f(0f, 3f, 0f), MotionType.DYNAMIC)
            world.setLinearVelocity(faller, Vec3f(0f, -6f, 0f))

            val events = world.stepAndDrain(steps = 60)

            assertEquals(
                emptyList(),
                events,
                "body ${reused.id} inherited the destroyed sensor's reporting: $events",
            )
        }
    }
}
