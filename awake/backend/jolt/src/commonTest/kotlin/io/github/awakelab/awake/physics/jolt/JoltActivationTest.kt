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
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Control over which bodies the simulation is actually integrating.
 *
 * The open-world case: a world holding thousands of crates is not simulating thousands of crates
 * unless something insists, and deactivating the ones nobody is near is how a caller insists
 * otherwise. What is worth pinning is the difference between *not integrating* and *not existing* —
 * a sleeping body still collides, and the failure mode of assuming otherwise is a player walking
 * through scenery.
 */
class JoltActivationTest {

    private fun PhysicsWorld.box(at: Vec3f, motionType: MotionType = MotionType.DYNAMIC): BodyHandle =
        createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), at, Quat.IDENTITY, motionType)

    /** The last pose reported: a sleeping body stops being visited, which is the point here. */
    private fun PhysicsWorld.stepTracking(body: BodyHandle, steps: Int, from: Float): Float {
        var y = from
        repeat(steps) {
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ -> if (handle == body) y = position.y }
        }
        return y
    }

    @Test
    fun aNewDynamicBodyIsActiveAndADeactivatedOneIsNot() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val box = world.box(Vec3f(0f, 5f, 0f))
            assertTrue(world.isActive(box), "a body just created and simulating should be active")

            world.setActive(box, false)

            assertFalse(world.isActive(box), "the body was not put to sleep")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aDeactivatedBodyStopsFalling() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val box = world.box(Vec3f(0f, 20f, 0f))
            world.setActive(box, false)

            val y = world.stepTracking(box, steps = 120, from = 20f)

            // Not integrating means not accumulating gravity either. This is the whole saving: a
            // crate nobody is near costs nothing until something wakes it.
            assertTrue(y > 19f, "a deactivated body kept falling: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun wakingItAgainResumesTheFall() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val box = world.box(Vec3f(0f, 20f, 0f))
            world.setActive(box, false)
            world.stepTracking(box, steps = 60, from = 20f)

            world.setActive(box, true)
            val y = world.stepTracking(box, steps = 120, from = 20f)

            // The control for the test above: if deactivation were a no-op both would fall, and if
            // it were permanent neither would.
            assertTrue(y < 19f, "the body stayed asleep after being woken: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aSleepingBodyIsStillSolidAndIsWokenByWhatHitsIt() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // The sleeping body needs something holding it up, or waking it just drops it and the
            // faller with it -- which is what the first version of this test actually measured.
            world.createBody(
                BoxShape(Vec3f(5f, 0.5f, 5f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val shelf = world.box(Vec3f(0f, 1f, 0f))
            world.stepTracking(shelf, steps = 180, from = 1f)
            assertFalse(world.isActive(shelf), "the shelf should have settled and slept")

            val faller = world.box(Vec3f(0f, 6f, 0f))
            val y = world.stepTracking(faller, steps = 180, from = 6f)

            // Deactivating is not disabling: a sleeping body still collides, and the contact wakes
            // it. An open world that sleeps its distant scenery must not become scenery the player
            // falls through.
            assertTrue(y > 1f, "the faller passed through a sleeping body: y=$y")
            assertTrue(world.isActive(shelf) || y > 1f, "the contact should have woken the shelf")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aStaticBodyIsNeverActive() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val ground = world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC)

            // Nothing to integrate, so asking to wake it is a no-op rather than an error -- which
            // is what lets a caller sweep a region and activate whatever it finds.
            assertFalse(world.isActive(ground))
            world.setActive(ground, true)
            assertFalse(world.isActive(ground), "a static body cannot be woken")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aSettledBodyPutsItselfToSleep() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            world.box(Vec3f(0f, 0f, 0f), MotionType.STATIC)
            val box = world.box(Vec3f(0f, 2f, 0f))

            world.stepTracking(box, steps = 300, from = 2f)

            // Jolt does this on its own, which is why the API is control over *when* rather than a
            // mechanism that did not exist. It is also why forEachBodyTransform stops reporting a
            // body that has come to rest.
            assertFalse(world.isActive(box), "a body resting on the ground should have slept")
        } finally {
            world.destroy()
        }
    }
}
