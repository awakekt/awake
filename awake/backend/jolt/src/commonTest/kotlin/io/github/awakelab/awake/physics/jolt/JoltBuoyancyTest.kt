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
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a body in water floats, and that a body out of it is untouched.
 *
 * Buoyancy is applied per step by the caller rather than being a state the body is in, so the two
 * things worth pinning are that repeated application actually holds something up against gravity,
 * and that applying it to a body above the surface does nothing -- because a caller is expected to
 * pass bodies that have just left the water without checking first.
 */
class JoltBuoyancyTest {

    private val surfaceY = 0f

    /** Deep enough that a floating box settles well clear of it. */
    private val floorY = -20f

    private suspend fun waterWorld(): PhysicsWorld = createJoltPhysicsWorld().also { world ->
        world.createBody(
            BoxShape(Vec3f(20f, 1f, 20f)),
            Vec3f(0f, floorY, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
    }

    private fun PhysicsWorld.box(at: Float): BodyHandle = createBody(
        BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
        Vec3f(0f, at, 0f),
        Quat.IDENTITY,
        MotionType.DYNAMIC,
    )

    /**
     * Steps with buoyancy applied every step, returning the last height reported.
     *
     * The last *reported* height, because a body that comes to rest stops being awake and stops
     * being visited -- reading it afterwards finds nothing.
     */
    private fun PhysicsWorld.floatFor(
        body: BodyHandle,
        steps: Int,
        from: Float,
        buoyancy: Buoyancy = Buoyancy.Water,
        applyBelow: Boolean = true,
    ): Float {
        var y = from
        repeat(steps) {
            if (!applyBelow || y < surfaceY) applyBuoyancy(body, surfaceY, buoyancy, 1f / 60f)
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ -> if (handle == body) y = position.y }
        }
        return y
    }

    @Test
    fun aBodyInWaterFloatsInsteadOfSinkingToTheFloor() = runTest {
        val world = waterWorld()
        try {
            val box = world.box(at = -5f)

            val y = world.floatFor(box, steps = 300, from = -5f)

            // Anywhere near the surface rather than an exact height: the point is that it did not
            // reach the floor, which is where it goes without this.
            assertTrue(y > floorY + 5f, "the box sank despite buoyancy: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun theSameBodyReachesTheFloorWithoutIt() = runTest {
        val world = waterWorld()
        try {
            val box = world.box(at = -5f)

            // The control. Without it, "it floated" proves nothing -- a box that was never going
            // to fall far in 300 steps would pass the test above with the feature removed.
            var y = -5f
            repeat(300) {
                world.step(1f / 60f)
                world.forEachBodyTransform { handle, position, _ -> if (handle == box) y = position.y }
            }

            assertTrue(y < floorY + 3f, "the box did not reach the floor unaided: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aWeakerFluidLetsTheBodySinkFurther() = runTest {
        val world = waterWorld()
        try {
            val floating = world.box(at = -5f)
            val sinking = world.box(at = -5f)

            val floatingY = world.floatFor(floating, steps = 200, from = -5f, buoyancy = Buoyancy.Water)
            val sinkingY = world.floatFor(
                sinking,
                steps = 200,
                from = -5f,
                buoyancy = Buoyancy.Sinking,
            )

            // Strength is the parameter that decides float from sink, so the two presets must not
            // behave the same -- a settings object nothing reads would pass every other test here.
            assertTrue(sinkingY < floatingY, "sinking=$sinkingY floating=$floatingY")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun applyingItToABodyAboveTheSurfaceDoesNothing() = runTest {
        val world = waterWorld()
        try {
            val box = world.box(at = 10f)

            // Applied unconditionally, including well above the water. A caller tracking who is in
            // a volume acts on last frame's answer, so a body that just jumped out gets one more
            // call -- which must not fling it.
            val y = world.floatFor(box, steps = 60, from = 10f, applyBelow = false)

            assertTrue(y < 10f, "the box was pushed up while out of the water: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aStaticBodyIsUnmovedByIt() = runTest {
        val world = waterWorld()
        try {
            val fixed = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(5f, -5f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )

            // There is nothing to push. Passing one must be a no-op rather than an error, because a
            // water volume contains whatever happens to be in it.
            repeat(60) {
                world.applyBuoyancy(fixed, surfaceY, Buoyancy.Water, 1f / 60f)
                world.step(1f / 60f)
            }

            val hit = world.raycast(Vec3f(5f, 10f, 0f), Vec3f(0f, -1f, 0f), 30f)
            assertTrue(hit != null && hit.point.y > -6f, "the static body moved: $hit")
        } finally {
            world.destroy()
        }
    }
}
