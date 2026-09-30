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
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.DistanceConstraint
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The "Physics" and "Jolt" guides show the backend-neutral `PhysicsWorld` API. Every sample is a
 * region here, run against a real Jolt world, so the pages cannot drift from what the API does.
 */
class PhysicsApiDocsSampleTest {

    @Test
    fun aBoxFallsOntoAFloor() = runTest {
        var boxY = 0f
        // --8<-- [start:world]
        val world = createJoltPhysicsWorld() // gravity (0, -9.81, 0), CollisionLayers.Default
        try {
            world.createBody(BoxShape(Vec3f(10f, 0.1f, 10f)), Vec3f(0f, -0.1f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val box = world.createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 5f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)

            repeat(120) {
                world.step(1f / 60f)
                // Visits bodies that may have moved; position and rotation are reused scratch values.
                world.forEachBodyTransform { handle, position, _ -> if (handle == box) boxY = position.y }
            }
        } finally {
            world.destroy() // native memory is not garbage collected
        }
        // --8<-- [end:world]
        assertEquals(0.5f, boxY, 0.05f)
    }

    @Test
    fun queriesFindTheFloor() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val floor = world.createBody(BoxShape(Vec3f(10f, 0.1f, 10f)), Vec3f(0f, -0.1f, 0f), Quat.IDENTITY, MotionType.STATIC)

            // --8<-- [start:raycast]
            val hit = world.raycast(origin = Vec3f(0f, 10f, 0f), direction = Vec3f(0f, -1f, 0f), maxDistance = 50f)
            if (hit != null) {
                println("hit ${hit.handle} at ${hit.point}, ${hit.distance} away")
            }
            // --8<-- [end:raycast]
            assertEquals(floor, assertNotNull(hit).handle)
            assertEquals(10f, hit.distance, 0.01f)

            // --8<-- [start:shape-cast]
            val sweep = world.shapeCast(SphereShape(0.25f), from = Vec3f(0f, 5f, 0f), to = Vec3f(0f, -5f, 0f))
            // sweep?.normal is the surface normal at the first contact; fraction is 0..1 along the sweep.
            // --8<-- [end:shape-cast]
            assertEquals(floor, assertNotNull(sweep).handle)
            assertTrue(sweep.normal.y > 0.9f, "the floor faces up: ${sweep.normal}")

            // --8<-- [start:overlap]
            val inside = mutableListOf<BodyHandle>()
            world.overlapShape(SphereShape(1f), Vec3f(0f, 0f, 0f)) { handle -> inside += handle }
            // --8<-- [end:overlap]
            assertEquals(listOf(floor), inside)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aSensorReportsWhatFallsThroughIt() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // --8<-- [start:sensor]
            val trigger = world.createBody(SphereShape(1f), Vec3f(0f, 2f, 0f), Quat.IDENTITY, MotionType.STATIC, sensor = true)
            val ball = world.createBody(SphereShape(0.25f), Vec3f(0f, 5f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)

            val entered = mutableListOf<BodyHandle>()
            repeat(90) {
                world.step(1f / 60f)
                world.drainContacts { event ->
                    if (event.phase == ContactPhase.BEGAN) entered += if (event.a == trigger) event.b else event.a
                }
            }
            // --8<-- [end:sensor]
            assertTrue(ball in entered, "the ball passed through the trigger: $entered")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aRayCanIgnoreEverythingButTheWorld() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val floor = world.createBody(BoxShape(Vec3f(10f, 0.1f, 10f)), Vec3f(0f, -0.1f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val crate = world.createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 2f, 0f), Quat.IDENTITY, MotionType.KINEMATIC)

            // --8<-- [start:only-layer]
            val down = Vec3f(0f, -1f, 0f)
            val anything = world.raycast(Vec3f(0f, 10f, 0f), down, maxDistance = 50f)
            val levelOnly = world.raycast(Vec3f(0f, 10f, 0f), down, maxDistance = 50f, onlyLayer = CollisionLayers.World)
            // --8<-- [end:only-layer]

            assertEquals(crate, assertNotNull(anything).handle)
            assertEquals(floor, assertNotNull(levelOnly).handle)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun debrisPassesThroughMovingBodies() = runTest {
        // --8<-- [start:layers]
        val debris = CollisionLayer(2)
        val layers = CollisionLayers(count = 3, movingLayers = setOf(CollisionLayers.Moving, debris)) { a, b ->
            if (a == debris || b == debris) {
                a == CollisionLayers.World || b == CollisionLayers.World // debris ignores other moving things
            } else {
                a == CollisionLayers.Moving || b == CollisionLayers.Moving
            }
        }
        val world = createJoltPhysicsWorld(layers = layers)
        // --8<-- [end:layers]
        try {
            // A block in the moving layer that debris must fall through.
            world.createBody(BoxShape(Vec3f(2f, 0.5f, 2f)), Vec3f(0f, 1f, 0f), Quat.IDENTITY, MotionType.KINEMATIC)
            // --8<-- [start:layer-body]
            val chip = world.createBody(SphereShape(0.1f), Vec3f(0f, 4f, 0f), Quat.IDENTITY, MotionType.DYNAMIC, layer = debris)
            // --8<-- [end:layer-body]

            val y = world.settle(chip, steps = 60, startY = 4f)

            assertTrue(layers.collides(debris, CollisionLayers.World))
            assertTrue(!layers.collides(debris, CollisionLayers.Moving))
            assertTrue(!layers.collides(debris, debris))
            assertTrue(y < 0.5f, "debris must fall through the moving block: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aRopeHoldsItsBody() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // --8<-- [start:constraint]
            val anchor = world.createBody(BoxShape(Vec3f(0.2f, 0.2f, 0.2f)), Vec3f(0f, 10f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val weight = world.createBody(SphereShape(0.3f), Vec3f(0f, 9f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)
            val rope = world.createConstraint(
                DistanceConstraint(
                    bodyA = anchor,
                    bodyB = weight,
                    pointA = Vec3f(0f, 10f, 0f), // world-space anchors, read once
                    pointB = Vec3f(0f, 9f, 0f),
                    maxDistance = 2f,
                ),
            )
            // --8<-- [end:constraint]
            val y = world.settle(weight, steps = 120, startY = 9f)
            assertTrue(y > 7.5f && y < 9.5f, "the weight hangs on its rope: y=$y")
            world.destroyConstraint(rope)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aHullRestsOnAHeightfield() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // --8<-- [start:heightfield]
            val sampleCount = 16
            val heights = FloatArray(sampleCount * sampleCount) // flat ground at y = 0
            val terrain = HeightFieldShape(heights, sampleCount, scale = Vec3f(1f, 1f, 1f))
            world.createBody(terrain, Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC) // static only
            // --8<-- [end:heightfield]

            // --8<-- [start:convex-hull]
            val wedge = ConvexHullShape(
                floatArrayOf(
                    -0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, // base
                    -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, // ridge
                ),
            )
            val prop = world.createBody(wedge, Vec3f(0f, 3f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)
            // --8<-- [end:convex-hull]

            val y = world.settle(prop, steps = 180, startY = 3f)
            assertTrue(y > -0.3f && y < 0.5f, "the hull rests on the heightfield: y=$y")
        } finally {
            world.destroy()
        }
    }

    /** The last reported height; a settled body stops being visited. */
    private fun PhysicsWorld.settle(body: BodyHandle, steps: Int, startY: Float): Float {
        var y = startY
        repeat(steps) {
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ -> if (handle == body) y = position.y }
        }
        return y
    }
}
