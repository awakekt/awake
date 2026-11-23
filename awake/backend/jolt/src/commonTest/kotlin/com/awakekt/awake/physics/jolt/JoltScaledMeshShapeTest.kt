/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsCapabilityException
import com.awakekt.awake.physics.ScaledShape
import com.awakekt.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoltScaledMeshShapeTest {
    @Test
    fun scaledMeshCollidesAtScaledBoundsAndSupportsDynamicBodies() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // Base quad spans x in [0, 1], z in [0, 1], at y = 1. Scaled by (4, 3, 2) -> x in [0, 4], z in [0, 2], y = 3.
            val floor = world.createBody(
                shape = ScaledShape(UNIT_PLATFORM, Vec3f(4f, 3f, 2f)),
                position = Vec3f.ZERO,
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )

            val hit = assertNotNull(world.raycast(Vec3f(3f, 10f, 1.5f), Vec3f(0f, -1f, 0f), 20f))
            assertEquals(floor, hit.handle)
            assertEquals(3f, hit.point.y, absoluteTolerance = 0.02f)

            // Outside the scaled X span [0, 4] must miss.
            assertNull(world.raycast(Vec3f(5f, 10f, 1.5f), Vec3f(0f, -1f, 0f), 20f))

            // A dynamic sphere dropped onto the scaled top surface must rest at y ~= 3.5, not fall through.
            val ball = world.createBody(
                shape = SphereShape(0.5f),
                position = Vec3f(2f, 6f, 1f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.DYNAMIC,
            )
            var y = 6f
            repeat(120) {
                world.step(STEP)
                world.forEachBodyTransform { handle, position, _ ->
                    if (handle == ball) y = position.y
                }
            }
            assertTrue(y in 3.3f..3.7f, "expected sphere to settle on scaled platform at y=3.5, got y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun mirroredScaledMeshKeepsSolidTopFace() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // Negative X scale mirrors [0, 1] to [-3, 0] while keeping y = 2 and z in [0, 2].
            val floor = world.createBody(
                shape = ScaledShape(UNIT_PLATFORM, Vec3f(-3f, 2f, 2f)),
                position = Vec3f.ZERO,
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )

            val hit = assertNotNull(world.raycast(Vec3f(-1.5f, 10f, 1f), Vec3f(0f, -1f, 0f), 20f))
            assertEquals(floor, hit.handle)
            assertEquals(2f, hit.point.y, absoluteTolerance = 0.02f)
            assertNull(world.raycast(Vec3f(1.5f, 10f, 1f), Vec3f(0f, -1f, 0f), 20f))

            val ball = world.createBody(
                shape = SphereShape(0.5f),
                position = Vec3f(-1.5f, 5f, 1f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.DYNAMIC,
            )
            var y = 5f
            repeat(120) {
                world.step(STEP)
                world.forEachBodyTransform { handle, position, _ ->
                    if (handle == ball) y = position.y
                }
            }
            assertTrue(y in 2.3f..2.7f, "expected sphere to rest on mirrored platform at y=2.5, got y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun manyScaledPlacementsReuseOneBaseMeshShapeCleanly() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        try {
            val handles = ArrayList<com.awakekt.awake.physics.BodyHandle>(PLACEMENT_COUNT)
            for (index in 0 until PLACEMENT_COUNT) {
                val shape = if (index % 5 == 0) {
                    UNIT_PLATFORM
                } else {
                    val scale = 1f + (index % 10) * 0.25f
                    ScaledShape(UNIT_PLATFORM, Vec3f(scale, 1f, scale))
                }
                handles += world.createBody(
                    shape = shape,
                    position = Vec3f(index * 10f, 0f, 0f),
                    rotation = Quat.IDENTITY,
                    motionType = MotionType.STATIC,
                )
            }

            val firstHit = assertNotNull(world.raycast(Vec3f(0.5f, 5f, 0.5f), Vec3f(0f, -1f, 0f), 10f))
            assertEquals(handles.first(), firstHit.handle)

            val lastX = (PLACEMENT_COUNT - 1) * 10f + 0.5f
            val lastHit = assertNotNull(world.raycast(Vec3f(lastX, 5f, 0.5f), Vec3f(0f, -1f, 0f), 10f))
            assertEquals(handles.last(), lastHit.handle)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun scaledMeshRejectsDynamicMotionAndSensorUsage() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        try {
            val scaled = ScaledShape(UNIT_PLATFORM, Vec3f(2f, 2f, 2f))
            assertFailsWith<PhysicsCapabilityException> {
                world.createBody(scaled, Vec3f.ZERO, Quat.IDENTITY, MotionType.DYNAMIC)
            }
            assertFailsWith<PhysicsCapabilityException> {
                world.createBody(scaled, Vec3f.ZERO, Quat.IDENTITY, MotionType.STATIC, sensor = true)
            }
            assertFailsWith<PhysicsCapabilityException> {
                world.shapeCast(scaled, Vec3f.ZERO, Vec3f(0f, -1f, 0f))
            }
        } finally {
            world.destroy()
        }
    }

    @Test
    fun scaledBoxWorksForBodiesAndShapeCasts() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f.ZERO)
        try {
            val wall = world.createBody(
                shape = ScaledShape(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(2f, 4f, 2f)),
                position = Vec3f(5f, 0f, 0f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )
            // Scaled half-extent X is 1.0, so wall front face sits at x = 4.0.
            val probe = ScaledShape(BoxShape(Vec3f(0.25f, 0.25f, 0.25f)), Vec3f(2f, 2f, 2f))
            val hit = assertNotNull(world.shapeCast(probe, Vec3f(0f, 0f, 0f), Vec3f(6f, 0f, 0f)))
            assertEquals(wall, hit.handle)
            // Probe half-extent X is 0.5 -> contacts at travel distance 3.5 out of 6.0.
            assertEquals(3.5f / 6f, hit.fraction, absoluteTolerance = 0.03f)
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val PLACEMENT_COUNT = 250

        // Counter-clockwise as seen from +Y so the top face is solid.
        val UNIT_PLATFORM = MeshShape(
            vertices = floatArrayOf(
                0f, 1f, 0f,
                0f, 1f, 1f,
                1f, 1f, 1f,
                1f, 1f, 0f,
            ),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        )
    }
}
