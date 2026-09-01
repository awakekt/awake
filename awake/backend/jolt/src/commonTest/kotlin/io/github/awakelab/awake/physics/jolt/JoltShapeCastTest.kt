/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.ConvexHullShape
import io.github.awakelab.awake.physics.MeshShape
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.syncTransforms
import io.github.awakelab.awake.physics.PhysicsCapabilityException
import io.github.awakelab.awake.physics.SphereShape
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one query the kinematic character controller is built on, against a real Jolt world.
 *
 * These assert geometry rather than "something was returned": a sweep that reports the wrong
 * fraction walks a character into a wall, and one with an inverted normal slides it into the
 * surface instead of along it -- both look like tuning problems and neither is.
 */
class JoltShapeCastTest {

    private suspend fun world(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    /** A wall 1 unit thick centred at x = +5, so its near face is at x = 4.5. */
    private fun PhysicsWorld.addWall() = createBody(
        BoxShape(Vec3f(0.5f, 5f, 5f)),
        Vec3f(5f, 0f, 0f),
        Quat.IDENTITY,
        MotionType.STATIC,
    )

    @Test
    fun sweepingASphereIntoAWallReportsWhereAlongTheSweepItStopped()  = runTest {
        world { world ->
            val wall = world.addWall()

            val hit = assertNotNull(
                world.shapeCast(SphereShape(0.5f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )

            assertEquals(wall, hit.handle)
            // The sphere's surface touches x = 4.5 when its centre is at 4.0, which is 0.4 of the
            // way along a 10-unit sweep.
            assertTrue(abs(hit.fraction - 0.4f) < 0.01f, "expected fraction ~0.4, got ${hit.fraction}")
            assertTrue(abs(hit.point.x - 4.5f) < 0.01f, "expected contact at x=4.5, got ${hit.point.x}")
        }
    }

    @Test
    fun theNormalPointsBackOutOfTheSurfaceTheSweepHit()  = runTest {
        world { world ->
            world.addWall()

            val hit = assertNotNull(
                world.shapeCast(SphereShape(0.5f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )

            // Sweeping along +x into a wall's near face, the surface faces back along -x. Getting
            // this backwards is the difference between sliding along a wall and burrowing into it.
            assertTrue(hit.normal.x < -0.99f, "expected the normal to face -x, got ${hit.normal}")
            assertTrue(abs(hit.normal.y) < 0.01f && abs(hit.normal.z) < 0.01f, "got ${hit.normal}")
        }
    }

    @Test
    fun aCapsuleIsSweptByItsFullWidthNotItsCentreLine()  = runTest {
        world { world ->
            world.addWall()

            // Cylinder half-height 0.9, radius 0.3: a character capsule. Swept along x, only the
            // radius matters, so it stops 0.3 short of the wall -- at centre x = 4.2.
            val hit = assertNotNull(
                world.shapeCast(CapsuleShape(halfHeight = 0.9f, radius = 0.3f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )

            assertTrue(abs(hit.fraction - 0.42f) < 0.01f, "expected fraction ~0.42, got ${hit.fraction}")
        }
    }

    @Test
    fun aSweepThatHitsNothingReturnsNull()  = runTest {
        world { world ->
            world.addWall()

            // Parallel to the wall, well clear of it.
            assertNull(world.shapeCast(SphereShape(0.5f), Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 10f)))
        }
    }

    @Test
    fun sweepingAHeightfieldIsRefusedRatherThanFailingInsideTheBackend()  = runTest {
        world { world ->
            val terrain = HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f))

            assertFailsWith<PhysicsCapabilityException> {
                world.shapeCast(terrain, Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f))
            }
        }
    }

    @Test
    fun aCapsuleCanBeADynamicBodyToo()  = runTest {
        world { world ->
            val handle = world.createBody(
                CapsuleShape(halfHeight = 0.9f, radius = 0.3f),
                Vec3f(0f, 2f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            assertEquals(1, world.syncTransforms().count { it.handle == handle })
        }
    }

    // --- collision layers ---------------------------------------------------------------------

    @Test
    fun aSweepRestrictedToOneLayerIgnoresEverythingElse()  = runTest {
        world { world ->
            // A crate sitting closer than the wall, in the moving layer. This is the showcase's
            // own problem: the camera must stop at walls and ignore the boxes around the player.
            world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(2f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
            val wall = world.addWall()

            val unfiltered = assertNotNull(
                world.shapeCast(SphereShape(0.2f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )
            val worldOnly = assertNotNull(
                world.shapeCast(
                    SphereShape(0.2f),
                    Vec3f(0f, 0f, 0f),
                    Vec3f(10f, 0f, 0f),
                    onlyLayer = CollisionLayers.World,
                ),
            )

            assertTrue(unfiltered.fraction < worldOnly.fraction, "the crate is nearer than the wall")
            assertEquals(wall, worldOnly.handle, "restricted to the world layer, only the wall counts")
        }
    }

    @Test
    fun aRaycastRestrictedToOneLayerIgnoresEverythingElse()  = runTest {
        world { world ->
            world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(2f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
            val wall = world.addWall()

            val hit = assertNotNull(
                world.raycast(
                    Vec3f(0f, 0f, 0f),
                    Vec3f(1f, 0f, 0f),
                    maxDistance = 10f,
                    onlyLayer = CollisionLayers.World,
                ),
            )

            assertEquals(wall, hit.handle)
        }
    }

    @Test
    fun bodiesInLayersThatDoNotCollidePassThroughEachOther()  = runTest {
        // Debris that falls through the player but still lands on the ground: the reason a
        // matrix exists at all rather than a single static/moving split.
        val player = CollisionLayer(1)
        val debris = CollisionLayer(2)
        val layers = CollisionLayers(count = 3, movingLayers = setOf(player, debris)) { a, b ->
            !(a == player && b == debris || a == debris && b == player)
        }
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f), layers = layers)
        try {
            world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                layer = player,
            )
            val falling = world.createBody(
                SphereShape(0.25f),
                Vec3f(0f, 3f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
                layer = debris,
            )
            world.setLinearVelocity(falling, Vec3f(0f, -5f, 0f))

            repeat(60) { world.step(1f / 60f) }

            // Straight through: a body stopped at y=1 would mean the matrix never reached Jolt.
            val y = world.syncTransforms().single { it.handle == falling }.position.y
            assertTrue(y < -1f, "expected debris to pass through the player layer, stopped at y=$y")
        } finally {
            world.destroy()
        }
    }

    // --- mesh and hull ------------------------------------------------------------------------

    /** A unit quad in the xz plane at y = 0, as two triangles. */
    private fun quadMesh(): MeshShape = MeshShape(
        vertices = floatArrayOf(
            -5f, 0f, -5f,
            5f, 0f, -5f,
            5f, 0f, 5f,
            -5f, 0f, 5f,
        ),
        // Counter-clockwise seen from above, so the solid side faces up. Reversed, a body falls
        // straight through: Jolt's mesh collision is one-sided.
        indices = intArrayOf(0, 2, 1, 0, 3, 2),
    )

    @Test
    fun aBoxLandsOnATriangleMesh()  = runTest {
        val world = createJoltPhysicsWorld()
        try {
            world.createBody(quadMesh(), Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val box = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(0f, 5f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            var resting = Float.NaN
            repeat(240) {
                world.step(1f / 60f)
                world.forEachBodyTransform { handle, position, _ ->
                    if (handle == box) resting = position.y
                }
            }

            // Rests on the surface at y=0, so its centre sits at its own half-height.
            assertTrue(abs(resting - 0.5f) < 0.1f, "expected to rest at ~0.5, got $resting")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aMeshCannotBeDynamic()  = runTest {
        world { world ->
            // A surface has no inside, so it has no mass and nothing to simulate.
            assertFailsWith<PhysicsCapabilityException> {
                world.createBody(quadMesh(), Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)
            }
        }
    }

    @Test
    fun aMeshCannotBeSwept()  = runTest {
        world { world ->
            assertFailsWith<PhysicsCapabilityException> {
                world.shapeCast(quadMesh(), Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f))
            }
        }
    }

    @Test
    fun aConvexHullCollidesAsTheShapeAroundItsPoints()  = runTest {
        world { world ->
            // A hull of the eight corners of a 1x1x1 box is that box.
            val corners = mutableListOf<Float>()
            for (x in listOf(-0.5f, 0.5f)) {
                for (y in listOf(-0.5f, 0.5f)) {
                    for (z in listOf(-0.5f, 0.5f)) {
                        corners += listOf(x, y, z)
                    }
                }
            }
            val hull = ConvexHullShape(corners.toFloatArray())
            val body = world.createBody(hull, Vec3f(5f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)

            val hit = assertNotNull(
                world.shapeCast(SphereShape(0.2f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )

            assertEquals(body, hit.handle)
            // The hull's near face is at x = 4.5, and the sphere stops its own radius short.
            assertTrue(abs(hit.fraction - 0.43f) < 0.02f, "expected fraction ~0.43, got ${hit.fraction}")
        }
    }

    @Test
    fun aConvexHullCanBeDynamic()  = runTest {
        world { world ->
            val tetra = ConvexHullShape(
                floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            )

            val body = world.createBody(tetra, Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)

            // The difference from a mesh: a hull encloses volume, so it has mass and moves.
            world.setLinearVelocity(body, Vec3f(1f, 0f, 0f))
            assertTrue(world.getLinearVelocity(body).x > 0.5f)
        }
    }
}
