/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.ContactPhase
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.jolt.JoltPhysicsWorld
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a sensor plus buoyancy makes a pool, against a real Jolt world.
 *
 * This is the composition the two features were built for and neither can do alone: physics has no
 * idea where water is, and `applyBuoyancy` is one step's worth of push rather than a state. The
 * sensor answers "who is in it" and the loop answers "how often", and the showcase's pool is
 * exactly this.
 *
 * The occupancy half is what is worth testing here rather than the floating: a body that never
 * leaves the set keeps being pushed after it has climbed out, and one that never enters sinks.
 */
class WaterVolumeJoltTest {

    private var physics: JoltPhysicsWorld? = null

    private val surfaceY = 0.7f

    @AfterTest
    fun tearDown() {
        physics?.destroy()
        physics = null
    }

    /** A pool with a floor under it, so a body that is not held up visibly reaches the bottom. */
    private fun pooledWorld(): Pair<JoltPhysicsWorld, BodyHandle> {
        val world = JoltPhysicsWorld()
        physics = world
        world.createBody(
            BoxShape(Vec3f(5f, 0.5f, 5f)),
            Vec3f(0f, -4f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        val pool = world.createBody(
            BoxShape(Vec3f(2f, 2f, 2f)),
            Vec3f(0f, -1.3f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            sensor = true,
        )
        return world to pool
    }

    /**
     * Runs the showcase's own loop: drain, track occupancy, push whatever is inside.
     *
     * Kept here rather than reaching into the driver because the driver needs a loaded scene; what
     * is being tested is the shape of the loop, and writing it out makes the ordering visible.
     */
    private fun PhysicsWorld.runPool(pool: BodyHandle, body: BodyHandle, steps: Int): Pair<Float, Int> {
        val submerged = mutableSetOf<BodyHandle>()
        var y = 0f
        repeat(steps) {
            drainContacts { event ->
                val other = if (event.a == pool) event.b else event.a
                if (event.a == pool || event.b == pool) {
                    if (event.phase == ContactPhase.BEGAN) submerged += other else submerged -= other
                }
            }
            submerged.forEach { applyBuoyancy(it, surfaceY, Buoyancy.Water, 1f / 60f) }
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ -> if (handle == body) y = position.y }
        }
        return y to submerged.size
    }

    @Test
    fun aBoxDroppedIntoThePoolFloatsInsteadOfReachingTheBottom() {
        val (world, pool) = pooledWorld()
        val box = world.createBody(
            BoxShape(Vec3f(0.4f, 0.4f, 0.4f)),
            Vec3f(0f, 3f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )

        val (y, floating) = world.runPool(pool, box, steps = 420)

        assertTrue(y > -3f, "the box sank to the floor: y=$y")
        assertTrue(floating == 1, "the pool lost track of what is in it: $floating")
    }

    @Test
    fun theSameBoxReachesTheBottomWithoutTheBuoyancyLoop() {
        val (world, pool) = pooledWorld()
        val box = world.createBody(
            BoxShape(Vec3f(0.4f, 0.4f, 0.4f)),
            Vec3f(0f, 3f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )

        // The control. Without it "the box floated" proves nothing -- a box that was never going to
        // fall far in 420 steps would pass the test above with the pool removed entirely.
        var y = 3f
        repeat(420) {
            world.step(1f / 60f)
            world.forEachBodyTransform { handle, position, _ -> if (handle == box) y = position.y }
        }

        assertTrue(y < -3f, "the box did not reach the bottom unaided: y=$y")
        assertTrue(pool.id != 0L, "the pool exists but was deliberately not driven")
    }

    @Test
    fun aBodyThatNeverEntersIsNeverPushed() {
        val (world, pool) = pooledWorld()
        // Beside the pool, over the floor. Nothing should hold it up.
        val box = world.createBody(
            BoxShape(Vec3f(0.4f, 0.4f, 0.4f)),
            Vec3f(4f, 3f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )

        val (y, floating) = world.runPool(pool, box, steps = 300)

        // The occupancy half: a pool that pushed everything would hold this up too, and a trigger
        // that reported everything would be indistinguishable from no trigger at all.
        assertTrue(floating == 0, "the pool claimed a body outside it: $floating")
        assertTrue(y < -3f, "a body outside the water was held up: y=$y")
    }
}
