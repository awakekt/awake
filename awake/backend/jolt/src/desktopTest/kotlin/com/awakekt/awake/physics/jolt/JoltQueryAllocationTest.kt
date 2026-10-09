/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a query costs the garbage collector, measured rather than argued (#570).
 *
 * A kinematic character controller sweeps its capsule about twice per fixed step, ignoring its own
 * body, so a scene's query garbage grows with how many characters it moves. Each jolt-jni object a
 * query builds also registers with a `Cleaner`, which costs more than its own bytes. Before the
 * world reused them, one sweep allocated several kilobytes; the ceilings below sit far under that,
 * so rebuilding the Jolt shape, collector, settings or filters per call fails them.
 *
 * Desktop only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class JoltQueryAllocationTest {

    @Test
    fun aCharacterSweepIgnoringItsOwnBodyStaysUnderItsCeiling() = runTest {
        withScene { world, body ->
            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val from = Vec3f(0f, 1f, 0f)
            val to = Vec3f(3f, 1f, 0f)
            assertNotNull(world.shapeCast(capsule, from, to, ignore = body), "the sweep must reach the wall")

            val perCast = bytesPer(CASTS) { world.shapeCast(capsule, from, to, ignore = body) }

            println("JoltQueryAllocationTest: $perCast B per character sweep")
            assertTrue(perCast < SWEEP_CEILING_BYTES, "a character sweep allocated $perCast B")
        }
    }

    @Test
    fun anOverlapQueryStaysUnderItsCeiling() = runTest {
        withScene { world, _ ->
            val probe = SphereShape(0.5f)
            val at = Vec3f(0f, 1f, 0f)
            var found = 0
            world.overlapShape(probe, at) { found++ }
            assertTrue(found > 0, "the probe must overlap the character")

            val perQuery = bytesPer(CASTS) { world.overlapShape(probe, at) { found++ } }

            println("JoltQueryAllocationTest: $perQuery B per overlap query")
            assertTrue(perQuery < OVERLAP_CEILING_BYTES, "an overlap query allocated $perQuery B")
        }
    }

    /** A floor, a wall at x = 2, and a kinematic capsule body standing at the origin, as a controller has. */
    private suspend fun withScene(block: suspend (PhysicsWorld, com.awakekt.awake.physics.BodyHandle) -> Unit) {
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

        // The result a sweep returns (ShapeCastHit and its two vectors), the one RShapeCast jolt-jni
        // has no setters for, and the wrappers its collector hands back. Rebuilding a Jolt shape,
        // collector, settings object and four filters per call costs several times this.
        const val SWEEP_CEILING_BYTES = 800L

        // One wrapper per overlapping body, and nothing rebuilt per call.
        const val OVERLAP_CEILING_BYTES = 300L
    }
}
