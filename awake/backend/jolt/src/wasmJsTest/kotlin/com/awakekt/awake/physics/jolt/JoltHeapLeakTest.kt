/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BallSocketConstraint
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.DistanceConstraint
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.HingeConstraint
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The module's heap is fixed at 128 MiB, so a call that keeps a few bytes on it ends a game that
 * makes it a few thousand times a frame with `Aborted(OOM)`.
 */
class JoltHeapLeakTest {
    @Test
    fun repeatedCallsLeaveTheHeapWhereItWas() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            world.createBody(BoxShape(Vec3f(20f, 0.5f, 20f)), Vec3f(0f, -0.5f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val ball = world.createBody(SphereShape(0.5f), Vec3f(0f, 2f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)
            val other = world.createBody(SphereShape(0.5f), Vec3f(2f, 2f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)
            val mover = world.createBody(BoxShape(HALF), Vec3f(4f, 2f, 0f), Quat.IDENTITY, MotionType.KINEMATIC)
            val calls = listOf<Pair<String, suspend () -> Unit>>(
                "shapeCast with a box" to { world.shapeCast(BoxShape(HALF), ABOVE, BELOW, ignore = ball) },
                "shapeCast on one layer" to {
                    world.shapeCast(CapsuleShape(0.5f, 0.3f), ABOVE, BELOW, onlyLayer = CollisionLayer(0))
                },
                "raycast" to { world.raycast(ABOVE, DOWN, 10f) },
                "raycast that misses" to { world.raycast(ABOVE, UP, 10f, CollisionLayer(0)) },
                "overlapShape" to { world.overlapShape(BoxShape(HALF), Vec3f.ZERO) {} },
                "getLinearVelocity" to { world.getLinearVelocity(ball) },
                "setLinearVelocity" to { world.setLinearVelocity(ball, Vec3f.ZERO) },
                "setAngularVelocity" to { world.setAngularVelocity(ball, Vec3f.ZERO) },
                "addImpulse" to { world.addImpulse(ball, Vec3f.ZERO) },
                "moveKinematic" to { world.moveKinematic(mover, Vec3f(4f, 2f, 0f), Quat.IDENTITY, STEP) },
                "isActive" to { world.isActive(ball) },
                "setActive" to { world.setActive(ball, true) },
                "setContinuousCollision" to { world.setContinuousCollision(ball, false) },
                "applyBuoyancy" to { world.applyBuoyancy(ball, 3f, Buoyancy.Water, STEP) },
                "shiftOrigin" to { world.shiftOrigin(Vec3f.ZERO) },
                "a box body" to { world.destroyBody(world.createBody(BoxShape(HALF), ABOVE, Quat.IDENTITY, MotionType.STATIC)) },
                "a hull body" to { world.destroyBody(world.createBody(HULL, ABOVE, Quat.IDENTITY, MotionType.DYNAMIC)) },
                "a mesh body" to { world.destroyBody(world.createBody(MESH, ABOVE, Quat.IDENTITY, MotionType.STATIC)) },
                "a height field body" to {
                    world.destroyBody(world.createBody(TERRAIN, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC))
                },
                "a hinge" to {
                    world.destroyConstraint(world.createConstraint(HingeConstraint(ball, other, ABOVE, UP)))
                },
                "a distance constraint" to {
                    world.destroyConstraint(world.createConstraint(DistanceConstraint(ball, other, ABOVE, BELOW, maxDistance = 4f)))
                },
                "a ball socket" to {
                    world.destroyConstraint(world.createConstraint(BallSocketConstraint(ball, other, ABOVE, UP)))
                },
                "a world" to { createJoltPhysicsWorld().destroy() },
            )

            val leaks = calls.mapNotNull { (name, call) ->
                repeat(WARM_UP) { call() }
                val before = joltHeapBytesInUse()
                repeat(CALLS) { call() }
                val kept = joltHeapBytesInUse() - before
                if (kept > 0) "$name kept $kept bytes over $CALLS calls" else null
            }
            assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val WARM_UP = 10
        const val CALLS = 200
        const val STEP = 1f / 60f
        val HALF = Vec3f(0.5f, 0.5f, 0.5f)
        val ABOVE = Vec3f(0f, 5f, 0f)
        val BELOW = Vec3f(0f, 1f, 0f)
        val UP = Vec3f(0f, 1f, 0f)
        val DOWN = Vec3f(0f, -1f, 0f)
        val HULL = ConvexHullShape(floatArrayOf(-0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0f, 0f, 0.5f, 0f, 1f, 0f))
        val MESH = MeshShape(floatArrayOf(-1f, 0f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, -1f, 0f, 1f), intArrayOf(0, 1, 2, 0, 2, 3))
        val TERRAIN = HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f))
    }
}
