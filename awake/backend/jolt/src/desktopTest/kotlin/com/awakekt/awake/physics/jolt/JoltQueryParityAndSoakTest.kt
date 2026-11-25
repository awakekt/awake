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
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.ScaledShape
import com.awakekt.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral parity and high-iteration native-lifetime stress tests for [JoltPhysicsWorld]'s
 * zero-wrapper narrow-phase query path (`shapeCast`, `raycast`, `overlapShape`).
 */
class JoltQueryParityAndSoakTest {

    private data class StackedScene(
        val self: BodyHandle,
        val sensorNear: BodyHandle,
        val sensorFar: BodyHandle,
        val crate: BodyHandle,
        val wall: BodyHandle,
    )

    private fun buildStackedScene(world: PhysicsWorld): StackedScene = StackedScene(
        self = world.createBody(
            CapsuleShape(halfHeight = 0.5f, radius = 0.3f),
            Vec3f(0f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.KINEMATIC,
            layer = CollisionLayers.Moving,
        ),
        sensorNear = world.createBody(
            BoxShape(Vec3f(0.4f, 2f, 2f)),
            Vec3f(1.2f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            layer = CollisionLayers.World,
            sensor = true,
        ),
        sensorFar = world.createBody(
            BoxShape(Vec3f(0.4f, 2f, 2f)),
            Vec3f(2.2f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            layer = CollisionLayers.Moving,
            sensor = true,
        ),
        crate = world.createBody(
            BoxShape(Vec3f(0.5f, 1f, 1f)),
            Vec3f(4f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
            layer = CollisionLayers.Moving,
        ),
        wall = world.createBody(
            BoxShape(Vec3f(0.5f, 2f, 2f)),
            Vec3f(7f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            layer = CollisionLayers.World,
        ),
    )

    @Test
    fun stackedBodiesSensorsAndIgnoreMatchExpectedOrder() = runTest {
        withWorld { world ->
            val scene = buildStackedScene(world)
            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val from = Vec3f(0f, 0f, 0f)
            val to = Vec3f(10f, 0f, 0f)

            // Without ignore, both casts stop on self.
            assertEquals(scene.self, assertNotNull(world.shapeCast(capsule, from, to)).handle)
            assertEquals(scene.self, assertNotNull(world.raycast(from, Vec3f(1f, 0f, 0f), 10f)).handle)

            // Ignoring self skips both sensors and stops on the nearer moving crate.
            val sweepAny = assertNotNull(world.shapeCast(capsule, from, to, ignore = scene.self))
            assertEquals(scene.crate, sweepAny.handle)
            assertTrue(abs(sweepAny.fraction - 0.32f) < 0.02f, "expected crate hit ~0.32, got ${sweepAny.fraction}")
            assertTrue(sweepAny.normal.x < -0.99f, "expected outward -x normal, got ${sweepAny.normal}")

            val rayAny = assertNotNull(world.raycast(from, Vec3f(1f, 0f, 0f), 10f, ignore = scene.self))
            assertEquals(scene.crate, rayAny.handle)
            assertTrue(abs(rayAny.distance - 3.5f) < 0.02f, "expected crate distance ~3.5, got ${rayAny.distance}")
        }
    }

    @Test
    fun layerFiltersAndOverlapsWithSensorsMatchExpectedResults() = runTest {
        withWorld { world ->
            val scene = buildStackedScene(world)
            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val from = Vec3f(0f, 0f, 0f)
            val to = Vec3f(10f, 0f, 0f)

            // Restricting to the World layer skips crate and hits wall at x = 6.5.
            val sweepWorld = assertNotNull(
                world.shapeCast(capsule, from, to, onlyLayer = CollisionLayers.World, ignore = scene.self),
            )
            assertEquals(scene.wall, sweepWorld.handle)
            assertTrue(abs(sweepWorld.fraction - 0.62f) < 0.02f, "expected wall hit ~0.62, got ${sweepWorld.fraction}")

            val rayWorld = assertNotNull(
                world.raycast(from, Vec3f(1f, 0f, 0f), 10f, onlyLayer = CollisionLayers.World, ignore = scene.self),
            )
            assertEquals(scene.wall, rayWorld.handle)
            assertTrue(abs(rayWorld.distance - 6.5f) < 0.02f, "expected wall distance ~6.5, got ${rayWorld.distance}")

            // Ignoring the wall on the World layer skips the only solid World body -> null.
            assertNull(world.shapeCast(capsule, from, to, onlyLayer = CollisionLayers.World, ignore = scene.wall))
            assertNull(world.raycast(from, Vec3f(1f, 0f, 0f), 10f, onlyLayer = CollisionLayers.World, ignore = scene.wall))

            // Overlap queries still report sensors (and respect layer filtering).
            val allOverlaps = buildSet {
                world.overlapShape(BoxShape(Vec3f(3f, 2f, 2f)), Vec3f(1.5f, 0f, 0f)) { add(it) }
            }
            assertTrue(scene.sensorNear in allOverlaps && scene.sensorFar in allOverlaps, "overlaps must include sensors")
            assertTrue(scene.self in allOverlaps && scene.crate in allOverlaps, "overlaps must include solid bodies")

            val worldOverlaps = buildSet {
                world.overlapShape(
                    BoxShape(Vec3f(3f, 2f, 2f)),
                    Vec3f(1.5f, 0f, 0f),
                    onlyLayer = CollisionLayers.World,
                ) { add(it) }
            }
            assertEquals(setOf(scene.sensorNear), worldOverlaps)
        }
    }

    @Test
    fun destroyedSensorIdReusedAsSolidIsNoLongerSkipped() = runTest {
        withWorld { world ->
            val wall = world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 2f)),
                Vec3f(8f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val sensor = world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 2f)),
                Vec3f(3f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )

            // Prime both sensor-only and ignore+sensor filters while the sensor is alive.
            val sphere = SphereShape(0.3f)
            assertEquals(wall, assertNotNull(world.shapeCast(sphere, Vec3f.ZERO, Vec3f(10f, 0f, 0f))).handle)
            assertEquals(wall, assertNotNull(world.raycast(Vec3f.ZERO, Vec3f(1f, 0f, 0f), 10f)).handle)
            assertNull(world.shapeCast(sphere, Vec3f.ZERO, Vec3f(10f, 0f, 0f), ignore = wall))

            // Destroy the sensor and immediately create a solid body at the same spot.
            world.destroyBody(sensor)
            val replacement = world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 2f)),
                Vec3f(3f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = false,
            )

            assertEquals(
                replacement,
                assertNotNull(world.shapeCast(sphere, Vec3f.ZERO, Vec3f(10f, 0f, 0f))).handle,
                "solid body created after sensor destruction must block shapeCast",
            )
            assertEquals(
                replacement,
                assertNotNull(world.shapeCast(sphere, Vec3f.ZERO, Vec3f(10f, 0f, 0f), ignore = wall)).handle,
                "solid body created after sensor destruction must block ignoring shapeCast",
            )
            assertEquals(
                replacement,
                assertNotNull(world.raycast(Vec3f.ZERO, Vec3f(1f, 0f, 0f), 10f)).handle,
                "solid body created after sensor destruction must block raycast",
            )
        }
    }

    @Test
    fun moreThanSixtyFourSensorsStillSkipSensorsAndIgnoredBody() = runTest {
        withWorld { world ->
            val self = world.createBody(
                SphereShape(0.3f),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )
            // Exceed MAX_FILTERED_SENSORS (64) to exercise the native-filter overflow fallback.
            val sensors = mutableListOf<BodyHandle>()
            repeat(72) { index ->
                sensors += world.createBody(
                    BoxShape(Vec3f(0.15f, 2f, 2f)),
                    Vec3f(1f + index * 0.05f, 0f, 0f),
                    Quat.IDENTITY,
                    MotionType.STATIC,
                    sensor = true,
                )
            }
            val wall = world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 2f)),
                Vec3f(8f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )

            val sweepHit = assertNotNull(
                world.shapeCast(SphereShape(0.25f), Vec3f.ZERO, Vec3f(10f, 0f, 0f), ignore = self),
            )
            assertEquals(wall, sweepHit.handle, "sweep must skip self and all 72 sensors")
            assertTrue(sweepHit.normal.x < -0.99f)

            val rayHit = assertNotNull(
                world.raycast(Vec3f.ZERO, Vec3f(1f, 0f, 0f), 10f, ignore = self),
            )
            assertEquals(wall, rayHit.handle, "ray must skip self and all 72 sensors")

            // Dropping back below the cap must seamlessly re-enable native sensor filtering.
            sensors.take(20).forEach { world.destroyBody(it) }
            assertEquals(
                wall,
                assertNotNull(world.shapeCast(SphereShape(0.25f), Vec3f.ZERO, Vec3f(10f, 0f, 0f), ignore = self)).handle,
            )
            assertEquals(
                wall,
                assertNotNull(world.raycast(Vec3f.ZERO, Vec3f(1f, 0f, 0f), 10f, ignore = self)).handle,
            )
        }
    }

    @Test
    fun scaledAndConvexHullQueryShapesWorkWithZeroWrapperPath() = runTest {
        withWorld { world ->
            val wall = world.createBody(
                BoxShape(Vec3f(0.5f, 3f, 3f)),
                Vec3f(5f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val scaledSphere = ScaledShape(SphereShape(0.25f), Vec3f(2f, 2f, 2f))
            val hull = ConvexHullShape(
                floatArrayOf(
                    -0.5f, -0.5f, -0.5f,
                    0.5f, -0.5f, -0.5f,
                    0.5f, 0.5f, -0.5f,
                    -0.5f, 0.5f, -0.5f,
                    -0.5f, -0.5f, 0.5f,
                    0.5f, -0.5f, 0.5f,
                    0.5f, 0.5f, 0.5f,
                    -0.5f, 0.5f, 0.5f,
                ),
            )

            // Both ScaledShape and ConvexHullShape back onto ShapeRefC, so targetVa() must dereference them.
            val scaledHit = assertNotNull(world.shapeCast(scaledSphere, Vec3f.ZERO, Vec3f(10f, 0f, 0f)))
            assertEquals(wall, scaledHit.handle)
            assertTrue(abs(scaledHit.fraction - 0.4f) < 0.02f, "scaled sphere radius 0.5 stops at 4.0, got ${scaledHit.fraction}")

            val hullHit = assertNotNull(world.shapeCast(hull, Vec3f.ZERO, Vec3f(10f, 0f, 0f)))
            assertEquals(wall, hullHit.handle)
            assertTrue(abs(hullHit.fraction - 0.4f) < 0.02f, "unit cube hull half-extent 0.5 stops at 4.0, got ${hullHit.fraction}")
        }
    }

    @Test
    fun fifteenThousandRepeatedQueriesRemainConsistentAndLeakFree() = runTest {
        withWorld { world ->
            val self = world.createBody(
                CapsuleShape(halfHeight = 0.5f, radius = 0.3f),
                Vec3f(0f, 1f, 0f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )
            val sensor = world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 2f)),
                Vec3f(1f, 1f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )
            val wall = world.createBody(
                BoxShape(Vec3f(0.25f, 2f, 5f)),
                Vec3f(2f, 2f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            world.step(1f / 60f)

            val capsule = CapsuleShape(halfHeight = 0.5f, radius = 0.3f)
            val probe = SphereShape(1.5f)
            val from = Vec3f(0f, 1f, 0f)
            val to = Vec3f(3f, 1f, 0f)
            val missFrom = Vec3f(0f, 10f, 0f)
            val missTo = Vec3f(3f, 10f, 0f)
            val rayDir = Vec3f(1f, 0f, 0f)

            repeat(SOAK_ITERATIONS) { iteration ->
                val sweepHit = assertNotNull(world.shapeCast(capsule, from, to, ignore = self))
                assertEquals(wall, sweepHit.handle)
                assertTrue(sweepHit.fraction in 0.4f..0.6f, "iteration=$iteration fraction=${sweepHit.fraction}")
                assertTrue(sweepHit.normal.x < -0.99f, "iteration=$iteration normal=${sweepHit.normal}")

                assertNull(world.shapeCast(capsule, missFrom, missTo, ignore = self))

                val rayHit = assertNotNull(world.raycast(from, rayDir, maxDistance = 5f, ignore = self))
                assertEquals(wall, rayHit.handle)
                assertTrue(abs(rayHit.distance - 1.75f) < 0.02f, "iteration=$iteration distance=${rayHit.distance}")

                assertNull(world.raycast(missFrom, rayDir, maxDistance = 5f))

                if (iteration % 50 == 0) {
                    var seenSelf = false
                    var seenSensor = false
                    var nestedSawWall = false
                    world.overlapShape(probe, from) { hit ->
                        if (hit == self) seenSelf = true
                        if (hit == sensor) {
                            seenSensor = true
                            // Exercise re-entrant overlap + temporary collector close().
                            world.overlapShape(SphereShape(1f), Vec3f(2f, 1f, 0f)) { nested ->
                                if (nested == wall) nestedSawWall = true
                            }
                        }
                    }
                    assertTrue(seenSelf && seenSensor && nestedSawWall, "iteration=$iteration overlap mismatch")
                }
            }
        }
    }

    private suspend fun withWorld(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(
            gravity = Vec3f(0f, 0f, 0f),
            layers = CollisionLayers.Default,
        )
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val SOAK_ITERATIONS = 15_000
    }
}
