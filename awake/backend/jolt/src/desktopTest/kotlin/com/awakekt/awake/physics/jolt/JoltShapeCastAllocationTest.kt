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
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a character controller's sweeps allocate.
 *
 * A controller sweeps its capsule about twice a tick, so a crowd of characters turns whatever one
 * cast allocates into a steady stream of garbage. Desktop only: it counts with the JVM's per-thread
 * allocation counter, which the other targets do not have.
 */
class JoltShapeCastAllocationTest {

    @Test
    fun aCapsuleSweptOverTheGroundAllocatesLittle() = runTest {
        assertUnderCeiling("over the ground", bytesPerCast(ownBody = false))
    }

    /** The controller's sweep starts inside its own body, so the closest hit is the one it ignores. */
    @Test
    fun aCapsuleSweptFromInsideItsOwnBodyAllocatesLittle() = runTest {
        assertUnderCeiling("from inside its own body", bytesPerCast(ownBody = true))
    }

    private fun assertUnderCeiling(case: String, bytes: Long) {
        println("JoltShapeCastAllocation: a sweep $case allocates $bytes bytes")
        assertTrue(bytes <= CEILING_BYTES, "a sweep $case allocated $bytes bytes, over $CEILING_BYTES")
    }

    /** Sweeps down onto a floor and sideways past nothing, as a controller's ground probe and move do. */
    private suspend fun bytesPerCast(ownBody: Boolean): Long {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            world.createBody(BoxShape(Vec3f(50f, 0.5f, 50f)), Vec3f(0f, -1.5f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val self = if (ownBody) world.createBody(CAPSULE, Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.KINEMATIC) else null
            repeat(WARM_UP_CASTS) { world.sweep(it, self) }
            val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            val thread = Thread.currentThread().id
            val before = threads.getThreadAllocatedBytes(thread)
            repeat(MEASURED_CASTS) { world.sweep(it, self) }
            return (threads.getThreadAllocatedBytes(thread) - before) / MEASURED_CASTS
        } finally {
            world.destroy()
        }
    }

    private fun PhysicsWorld.sweep(index: Int, self: BodyHandle?) {
        val to = if (index % 2 == 0) DOWN else ACROSS
        shapeCast(CAPSULE, FROM, to, ignore = self)
    }

    private companion object {
        val CAPSULE = CapsuleShape(halfHeight = 0.4f, radius = 0.4f)
        val FROM = Vec3f(0f, 0f, 0f)
        val DOWN = Vec3f(0f, -2f, 0f)
        val ACROSS = Vec3f(2f, 0f, 0f)
        const val WARM_UP_CASTS = 2_000
        const val MEASURED_CASTS = 20_000

        /** 268 and 556 bytes today; 1,612 and 2,921 when every cast built its own Jolt objects. */
        const val CEILING_BYTES = 1_024L
    }
}
