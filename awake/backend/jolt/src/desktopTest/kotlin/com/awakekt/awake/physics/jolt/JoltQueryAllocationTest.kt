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
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a query costs the garbage collector, measured rather than argued (#570).
 *
 * A kinematic character controller sweeps its capsule about twice per fixed step, ignoring its own
 * body, so a scene's query garbage grows with how many characters it moves. Each jolt-jni object a
 * query builds also registers with a `Cleaner`, which costs more than its own bytes. With native
 * `IgnoreMultipleBodiesFilter` and [JoltNativeQueryOps], steady-state queries allocate only their
 * returned hit value (and nullable `BodyHandle` box when `ignore` is passed) -- zero `Cleaner`
 * registrations and zero intermediate `JoltPhysicsObject` wrappers.
 *
 * Desktop only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class JoltQueryAllocationTest {

    @Test
    fun nativeQueryFastPathIsActiveOnDesktop() {
        assertNotNull(JoltNativeQueryOps.bound, "JoltNativeQueryOps must bind on desktop jolt-jni")
    }

    @Test
    fun aCharacterSweepIgnoringItsOwnBodyStaysUnderItsCeiling() = runTest {
        withScene { world, body ->
            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val from = Vec3f(0f, 1f, 0f)
            val to = Vec3f(3f, 1f, 0f)
            assertNotNull(world.shapeCast(capsule, from, to, ignore = body), "the sweep must reach the wall")

            val perCast = bytesPer(CASTS) { world.shapeCast(capsule, from, to, ignore = body) }

            println("JoltQueryAllocationTest: $perCast B per character sweep (ignore=body, hit)")
            assertTrue(perCast < SWEEP_IGNORE_CEILING_BYTES, "a character sweep allocated $perCast B")
        }
    }

    @Test
    fun aPlainShapeCastHitAndMissStayUnderTheirCeilings() = runTest {
        withScene { world, _ ->
            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val hitFrom = Vec3f(1f, 1f, 0f)
            val hitTo = Vec3f(3f, 1f, 0f)
            val missFrom = Vec3f(0f, 10f, 0f)
            val missTo = Vec3f(3f, 10f, 0f)
            assertNotNull(world.shapeCast(capsule, hitFrom, hitTo), "the sweep must reach the wall")
            assertNull(world.shapeCast(capsule, missFrom, missTo), "the high sweep must miss")

            val perHit = bytesPer(CASTS) { world.shapeCast(capsule, hitFrom, hitTo) }
            val perMiss = bytesPer(CASTS) { world.shapeCast(capsule, missFrom, missTo) }

            println("JoltQueryAllocationTest: $perHit B per shapeCast (no ignore, hit)")
            println("JoltQueryAllocationTest: $perMiss B per shapeCast (no ignore, miss)")
            assertTrue(perHit < SWEEP_PLAIN_CEILING_BYTES, "a plain shapeCast hit allocated $perHit B")
            assertTrue(perMiss < MISS_CEILING_BYTES, "a plain shapeCast miss allocated $perMiss B")
        }
    }

    @Test
    fun sweepingThroughSensorsAndAlternatingIgnoredBodiesStaysUnderCeiling() = runTest {
        withScene { world, bodyA ->
            val bodyB = world.createBody(
                CapsuleShape(halfHeight = 0.5f, radius = 0.3f),
                Vec3f(0f, 1f, 2f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )
            // A sensor enclosing both characters and extending toward the wall.
            world.createBody(
                BoxShape(Vec3f(1.5f, 2f, 4f)),
                Vec3f(0.5f, 1f, 1f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )
            world.step(1f / 60f)

            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val fromA = Vec3f(0f, 1f, 0f)
            val toA = Vec3f(3f, 1f, 0f)
            val fromB = Vec3f(0f, 1f, 2f)
            val toB = Vec3f(3f, 1f, 2f)
            assertNotNull(world.shapeCast(capsule, fromA, toA, ignore = bodyA))
            assertNotNull(world.shapeCast(capsule, fromB, toB, ignore = bodyB))

            var toggle = false
            val perCast = bytesPer(CASTS) {
                toggle = !toggle
                if (toggle) {
                    world.shapeCast(capsule, fromA, toA, ignore = bodyA)
                } else {
                    world.shapeCast(capsule, fromB, toB, ignore = bodyB)
                }
            }

            println("JoltQueryAllocationTest: $perCast B per alternating character sweep inside sensor")
            assertTrue(perCast < SWEEP_IGNORE_CEILING_BYTES, "alternating sweeps inside sensor allocated $perCast B")
        }
    }

    @Test
    fun anOverlapQueryStaysUnderItsCeiling() = runTest {
        withScene { world, _ ->
            val probe = SphereShape(0.5f)
            val at = Vec3f(0f, 1f, 0f)
            var found = 0
            val visitor: (BodyHandle) -> Unit = { found++ }
            world.overlapShape(probe, at, onOverlap = visitor)
            assertTrue(found > 0, "the probe must overlap the character")

            val perQuery = bytesPer(CASTS) { world.overlapShape(probe, at, onOverlap = visitor) }

            println("JoltQueryAllocationTest: $perQuery B per overlap query")
            assertTrue(perQuery < OVERLAP_CEILING_BYTES, "an overlap query allocated $perQuery B")
        }
    }

    @Test
    fun aRaycastStaysUnderItsCeiling() = runTest {
        withScene { world, body ->
            val origin = Vec3f(-1f, 1f, 0f)
            val insideOrigin = Vec3f(0f, 1f, 0f)
            val missOrigin = Vec3f(0f, 10f, 0f)
            val direction = Vec3f(1f, 0f, 0f)
            assertNotNull(world.raycast(origin, direction, maxDistance = 10f), "the ray must hit the capsule")
            assertNotNull(
                world.raycast(insideOrigin, direction, maxDistance = 10f, ignore = body),
                "the ray ignoring self must hit the wall",
            )
            assertNull(world.raycast(missOrigin, direction, maxDistance = 10f), "the high ray must miss")

            val perRay = bytesPer(CASTS) { world.raycast(origin, direction, maxDistance = 10f) }
            val perRayIgnore = bytesPer(CASTS) {
                world.raycast(insideOrigin, direction, maxDistance = 10f, ignore = body)
            }
            val perRayMiss = bytesPer(CASTS) { world.raycast(missOrigin, direction, maxDistance = 10f) }

            println("JoltQueryAllocationTest: $perRay B per raycast (no ignore, hit)")
            println("JoltQueryAllocationTest: $perRayIgnore B per raycast (ignore=body, hit)")
            println("JoltQueryAllocationTest: $perRayMiss B per raycast (no ignore, miss)")
            assertTrue(perRay < RAY_CEILING_BYTES, "a raycast allocated $perRay B")
            assertTrue(perRayIgnore < RAY_IGNORE_CEILING_BYTES, "an ignoring raycast allocated $perRayIgnore B")
            assertTrue(perRayMiss < MISS_CEILING_BYTES, "a missed raycast allocated $perRayMiss B")
        }
    }

    /** A floor, a wall at x = 2, and a kinematic capsule body standing at the origin, as a controller has. */
    private suspend fun withScene(block: suspend (PhysicsWorld, BodyHandle) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            world.createBody(BoxShape(Vec3f(20f, 0.1f, 20f)), Vec3f(0f, -0.1f, 0f), Quat.IDENTITY, MotionType.STATIC)
            world.createBody(BoxShape(Vec3f(0.25f, 2f, 5f)), Vec3f(2f, 2f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val body = world.createBody(
                CapsuleShape(halfHeight = 0.5f, radius = 0.3f),
                Vec3f(0f, 1f, 0f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )
            world.step(1f / 60f)
            block(world, body)
        } finally {
            world.destroy()
        }
    }

    /** Average bytes this thread allocates per run of [action], after warming the JIT and the world's reuse. */
    private inline fun bytesPer(runs: Int, action: () -> Unit): Long {
        repeat(WARMUP) { action() }
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        repeat(runs) { action() }
        return (threads.currentThreadAllocatedBytes - before) / runs
    }

    private companion object {
        const val WARMUP = 2_000
        const val CASTS = 5_000

        // ShapeCastHit (32 B) + point Vec3f (24 B) + normal Vec3f (24 B) = 80 B.
        // Passing non-null `ignore: BodyHandle?` adds a 24 B inline-class box at the call site (= 104 B).
        const val SWEEP_PLAIN_CEILING_BYTES = 128L
        const val SWEEP_IGNORE_CEILING_BYTES = 160L

        // One BodyHandle box (24 B) per overlapping body delivered to `(BodyHandle) -> Unit`.
        const val OVERLAP_CEILING_BYTES = 64L

        // RaycastHit (32 B) + point Vec3f (24 B) = 56 B (+ 24 B BodyHandle? box when ignoring = 80 B).
        const val RAY_CEILING_BYTES = 96L
        const val RAY_IGNORE_CEILING_BYTES = 128L

        // A miss allocates nothing in steady state; allow a tiny margin for JIT/measurement noise.
        const val MISS_CEILING_BYTES = 32L
    }
}
