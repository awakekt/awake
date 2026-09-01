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
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.PhysicsCapabilityException
import io.github.awakelab.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Overlap queries and the sweep exclusion, against a real Jolt world.
 *
 * The two things a closest-hit cast cannot do. An overlap answers "what is in here right now",
 * which a sweep cannot because it stops at the first thing it would be blocked by; and an
 * exclusion answers "what would I hit if that body were not there", which filtering a closest hit
 * afterwards cannot, because the excluded body hides everything behind it.
 */
class JoltOverlapQueryTest {

    private suspend fun world(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    private fun PhysicsWorld.box(
        at: Vec3f,
        half: Float = 0.5f,
        layer: CollisionLayer = CollisionLayers.World,
    ): BodyHandle = createBody(
        BoxShape(Vec3f(half, half, half)),
        at,
        Quat.IDENTITY,
        MotionType.STATIC,
        layer,
    )

    private fun PhysicsWorld.overlapping(at: Vec3f, radius: Float, onlyLayer: CollisionLayer? = null) =
        buildList { overlapShape(SphereShape(radius), at, onlyLayer) { add(it) } }

    @Test
    fun anOverlapFindsEveryBodyInsideItAndNotJustTheNearest()  = runTest {
        world { world ->
            val near = world.box(at = Vec3f(1f, 0f, 0f))
            val far = world.box(at = Vec3f(-1f, 0f, 0f))
            world.box(at = Vec3f(20f, 0f, 0f))

            val found = world.overlapping(Vec3f(0f, 0f, 0f), radius = 2f)

            // Three bodies, two inside. A cast would have reported one of them and stopped, which
            // is exactly why an explosion cannot be built out of casts.
            assertEquals(setOf(near, far), found.toSet(), "found $found")
        }
    }

    @Test
    fun anOverlapWithNothingInItReportsNothing()  = runTest {
        world { world ->
            world.box(at = Vec3f(20f, 0f, 0f))

            assertEquals(emptyList(), world.overlapping(Vec3f(0f, 0f, 0f), radius = 1f))
        }
    }

    @Test
    fun aBodyJustOutsideTheVolumeIsNotInIt()  = runTest {
        world { world ->
            // Box half-extent 0.5 centred at x = 2, so its near face is at 1.5. A sphere of radius
            // 1.4 stops short of it; the same query at 1.6 does not.
            world.box(at = Vec3f(2f, 0f, 0f))

            assertEquals(emptyList(), world.overlapping(Vec3f(0f, 0f, 0f), radius = 1.4f))
            assertEquals(1, world.overlapping(Vec3f(0f, 0f, 0f), radius = 1.6f).size)
        }
    }

    @Test
    fun anOverlapCanBeRestrictedToOneLayer()  = runTest {
        world { world ->
            val level = world.box(at = Vec3f(0.5f, 0f, 0f), layer = CollisionLayers.World)
            world.box(at = Vec3f(-0.5f, 0f, 0f), layer = CollisionLayers.Moving)

            val found = world.overlapping(Vec3f(0f, 0f, 0f), radius = 2f, onlyLayer = CollisionLayers.World)

            assertEquals(listOf(level), found, "found $found")
        }
    }

    @Test
    fun aSurfaceCannotBeOverlappedWith()  = runTest {
        world { world ->
            // A heightfield has no inside, so "what is inside this" has no answer for one. The
            // same rule shapeCast carries, and better as a throw than as an empty result.
            assertFailsWith<PhysicsCapabilityException> {
                world.overlapShape(
                    HeightFieldShape(FloatArray(16), 4, Vec3f(1f, 1f, 1f)),
                    Vec3f(0f, 0f, 0f),
                ) { }
            }
        }
    }

    @Test
    fun aSweepIgnoringABodyReportsTheNextOneBehindIt()  = runTest {
        world { world ->
            // The character's own body, at the character's own position: the case that makes an
            // inner body impossible without this. It is nearer than the wall in every sweep.
            val self = world.box(at = Vec3f(0f, 0f, 0f), half = 0.4f)
            val wall = world.box(at = Vec3f(5f, 0f, 0f), half = 1f)

            val blind = world.shapeCast(SphereShape(0.3f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f))
            val seeing = world.shapeCast(
                SphereShape(0.3f),
                Vec3f(0f, 0f, 0f),
                Vec3f(10f, 0f, 0f),
                ignore = self,
            )

            assertEquals(self, blind?.handle, "without the exclusion the sweep must hit self first")
            // Not null, and not self: filtering the closest hit afterwards would have produced
            // nothing here, because self is all a closest-hit cast ever reports.
            assertEquals(wall, seeing?.handle, "the sweep did not see past the ignored body")
            assertTrue(seeing!!.fraction > 0f, "fraction was ${seeing.fraction}")
        }
    }

    @Test
    fun aSweepIsNotStoppedByASensor()  = runTest {
        world { world ->
            val trigger = world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(3f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )
            val wall = world.box(at = Vec3f(6f, 0f, 0f), half = 1f)

            val hit = world.shapeCast(SphereShape(0.3f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f))

            // A sensor is not solid, so it is not what a sweep is stopped by. Reporting it turns
            // every trigger volume into an invisible wall the player walks into.
            assertTrue(hit?.handle != trigger, "the sweep was blocked by a trigger volume")
            assertEquals(wall, hit?.handle, "the sweep did not reach the wall behind the trigger")
        }
    }

    @Test
    fun aSweepThroughASensorWithNothingBehindItHitsNothing()  = runTest {
        world { world ->
            world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(3f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )

            assertEquals(
                null,
                world.shapeCast(SphereShape(0.3f), Vec3f(0f, 0f, 0f), Vec3f(10f, 0f, 0f)),
            )
        }
    }

    @Test
    fun aRayIsNotStoppedByASensorEither()  = runTest {
        world { world ->
            world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(3f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )
            val wall = world.box(at = Vec3f(6f, 0f, 0f), half = 1f)

            val hit = world.raycast(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f), maxDistance = 20f)

            assertEquals(wall, hit?.handle, "the ray stopped at the trigger volume")
        }
    }

    @Test
    fun anOverlapStillFindsSensors()  = runTest {
        world { world ->
            val trigger = world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )

            // The other half of the rule: a sensor is invisible to "what blocks me" and is exactly
            // what "what is in here" is asking about.
            assertEquals(listOf(trigger), world.overlapping(Vec3f(0f, 0f, 0f), radius = 0.5f))
        }
    }

    @Test
    fun ignoringABodyThatIsNotInTheWayChangesNothing()  = runTest {
        world { world ->
            val wall = world.box(at = Vec3f(5f, 0f, 0f), half = 1f)
            val elsewhere = world.box(at = Vec3f(0f, 20f, 0f))

            val hit = world.shapeCast(
                SphereShape(0.3f),
                Vec3f(0f, 0f, 0f),
                Vec3f(10f, 0f, 0f),
                ignore = elsewhere,
            )

            assertEquals(wall, hit?.handle)
        }
    }

    @Test
    fun ignoringTheOnlyBodyInTheWayReportsNoHit()  = runTest {
        world { world ->
            val wall = world.box(at = Vec3f(5f, 0f, 0f), half = 1f)

            val hit = world.shapeCast(
                SphereShape(0.3f),
                Vec3f(0f, 0f, 0f),
                Vec3f(10f, 0f, 0f),
                ignore = wall,
            )

            assertEquals(null, hit, "an excluded body must not be reported as a hit")
        }
    }
}
