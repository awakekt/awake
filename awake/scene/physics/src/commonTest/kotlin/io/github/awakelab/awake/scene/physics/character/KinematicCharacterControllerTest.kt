/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.character

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.Buoyancy
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.Constraint
import io.github.awakelab.awake.physics.ConstraintHandle
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.RaycastHit
import io.github.awakelab.awake.physics.ShapeCastHit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The controller's own arithmetic, against sweeps this test decides the answers to.
 *
 * A stub world rather than Jolt on purpose: these assert what the controller does with a hit,
 * which is the part that is Awake's rather than the backend's, and they run on every target
 * without a native library. Whether Jolt reports the hit correctly is [JoltShapeCastTest]'s job.
 */
class KinematicCharacterControllerTest {

    /** A plane the character can stand on, angled by giving it a normal. */
    private class Surface(val normal: Vec3f, val fraction: Float, val handle: BodyHandle)

    private class StubWorld : PhysicsWorld {
        /** Consulted per sweep; return `null` for a clear path. */
        override val layers: CollisionLayers = CollisionLayers.Default

        var onSweep: (from: Vec3f, to: Vec3f) -> Surface? = { _, _ -> null }
        val sweeps = mutableListOf<Pair<Vec3f, Vec3f>>()

        /** Which shape each sweep used, so a test can tell the crouched capsule from the standing one. */
        val sweptShapes = mutableListOf<PhysicsShape>()

        override fun shapeCast(
            shape: PhysicsShape,
            from: Vec3f,
            to: Vec3f,
            onlyLayer: CollisionLayer?,
            ignore: BodyHandle?,
        ): ShapeCastHit? {
            sweeps += Vec3f(from.x, from.y, from.z) to Vec3f(to.x, to.y, to.z)
            sweptShapes += shape
            val surface = onSweep(from, to) ?: return null
            return ShapeCastHit(surface.handle, Vec3f(0f, 0f, 0f), surface.normal, surface.fraction)
        }

        override fun createBody(
            shape: PhysicsShape,
            position: Vec3f,
            rotation: Quat,
            motionType: MotionType,
            layer: CollisionLayer,
            sensor: Boolean,
        ): BodyHandle = error("not needed for this test")

        override fun destroyBody(handle: BodyHandle) = error("not needed for this test")
        override fun step(deltaTime: Float) = error("not needed for this test")
        override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) = error("not needed")
        override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) = error("not needed")

        /** What the body underfoot is doing; the default is a world that holds still. */
        var velocities: Map<BodyHandle, Vec3f> = emptyMap()

        override fun getLinearVelocity(handle: BodyHandle): Vec3f =
            velocities[handle] ?: Vec3f(0f, 0f, 0f)
        override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = error("not needed")
        override fun moveKinematic(
            handle: BodyHandle,
            position: Vec3f,
            rotation: Quat,
            deltaTime: Float,
        ) = error("not needed for this test")

        override fun shiftOrigin(offset: Vec3f) = error("not needed for this test")
        override fun forEachBodyTransform(
            action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
        ) = Unit
        override fun raycast(
            origin: Vec3f,
            direction: Vec3f,
            maxDistance: Float,
            onlyLayer: CollisionLayer?,
        ): RaycastHit? = null
        override fun overlapShape(
            shape: PhysicsShape,
            position: Vec3f,
            onlyLayer: CollisionLayer?,
            onOverlap: (BodyHandle) -> Unit,
        ) = Unit

        override fun setActive(handle: BodyHandle, active: Boolean) = Unit

        override fun isActive(handle: BodyHandle): Boolean = false

        override fun createConstraint(constraint: Constraint): ConstraintHandle =
            ConstraintHandle(0)

        override fun destroyConstraint(handle: ConstraintHandle) = Unit

        override fun applyBuoyancy(
            handle: BodyHandle,
            surfaceY: Float,
            buoyancy: Buoyancy,
            deltaTime: Float,
        ) = Unit

        override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) = Unit
        override fun setContactReporting(handle: BodyHandle, enabled: Boolean) = Unit

        override fun drainContacts(action: (ContactEvent) -> Unit) = Unit
        override fun destroy() = Unit
    }

    private val config = CharacterConfig(
        shape = CapsuleShape(halfHeight = 0.9f, radius = 0.3f),
        skinWidth = 0.02f,
    )

    private fun controller(world: PhysicsWorld) =
        KinematicCharacterController(world, config, Vec3f(0f, 0f, 0f))

    /** Ground is the last sweep of a move; anything before it is the motion itself. */
    private fun groundOnly(normal: Vec3f, handle: BodyHandle = BodyHandle(7L)): StubWorld =
        StubWorld().apply {
            onSweep = { from, to -> if (to.y < from.y) Surface(normal, 0f, handle) else null }
        }

    @Test
    fun anUnobstructedMoveArrivesExactlyWhereItWasSent() {
        val world = StubWorld()
        val character = controller(world)

        character.move(Vec3f(3f, 0f, 4f))

        assertEquals(3f, character.position.x)
        assertEquals(4f, character.position.z)
    }

    @Test
    fun aMoveStraightIntoAWallStopsAtTheWall() {
        val world = StubWorld()
        // A wall facing -x, met a quarter of the way along the move.
        world.onSweep = { from, to ->
            if (to.x > from.x) Surface(Vec3f(-1f, 0f, 0f), 0.25f, BodyHandle(1L)) else null
        }
        val character = controller(world)

        character.move(Vec3f(4f, 0f, 0f))

        // A quarter of 4 is 1, less the skin width holding it off the surface.
        assertTrue(abs(character.position.x - (1f - config.skinWidth)) < 1e-4f, "x=${character.position.x}")
    }

    @Test
    fun aMoveAtAnAngleToAWallKeepsSlidingAlongIt() {
        val world = StubWorld()
        // Wall faces -x and is hit immediately, so all remaining motion must turn along z.
        world.onSweep = { from, to ->
            if (to.x > from.x + 1e-6f) Surface(Vec3f(-1f, 0f, 0f), 0f, BodyHandle(1L)) else null
        }
        val character = controller(world)

        character.move(Vec3f(3f, 0f, 3f))

        // The x component is absorbed by the wall; the z component survives in full. Stopping
        // dead here instead is what makes a character feel stuck on walls.
        assertTrue(character.position.x < config.skinWidth * 2f, "x=${character.position.x}")
        assertTrue(abs(character.position.z - 3f) < 1e-3f, "z=${character.position.z}")
    }

    @Test
    fun slidingNeverAddsSpeed() {
        val world = StubWorld()
        world.onSweep = { from, to ->
            if (to.x > from.x + 1e-6f) Surface(Vec3f(-1f, 0f, 0f), 0f, BodyHandle(1L)) else null
        }
        val character = controller(world)

        character.move(Vec3f(3f, 0f, 3f))

        // Projection removes motion, so the distance travelled cannot exceed what was asked for.
        // A sign error here turns a wall into a slingshot.
        val travelled = character.position.length3()
        assertTrue(travelled <= Vec3f(3f, 0f, 3f).length3() + 1e-3f, "travelled $travelled")
    }

    @Test
    fun flatGroundUnderfootCountsAsGrounded() {
        val world = groundOnly(Vec3f(0f, 1f, 0f))
        val character = controller(world)

        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(character.isGrounded)
        assertEquals(BodyHandle(7L), character.groundBody)
        assertEquals(1f, character.groundNormal.y)
    }

    @Test
    fun aSlopeSteeperThanTheLimitIsNotGround() {
        // Normal 30 degrees from horizontal: a 60-degree slope, well past the 45-degree limit.
        val world = groundOnly(Vec3f(0.866f, 0.5f, 0f))
        val character = controller(world)

        character.move(Vec3f(0f, 0f, 0f))

        assertFalse(character.isGrounded, "a 60-degree face is a wall, not a floor")
        assertNull(character.groundBody)
    }

    @Test
    fun aSlopeWithinTheLimitIsGround() {
        // Normal 15 degrees off vertical: a 15-degree slope, inside the 45-degree limit.
        val world = groundOnly(Vec3f(0.2588f, 0.9659f, 0f))
        val character = controller(world)

        character.move(Vec3f(0f, 0f, 0f))

        assertTrue(character.isGrounded, "a 15-degree slope is walkable")
    }

    @Test
    fun nothingUnderfootIsNotGrounded() {
        val character = controller(StubWorld())

        character.move(Vec3f(0f, -1f, 0f))

        assertFalse(character.isGrounded)
        assertNull(character.groundBody)
    }

    @Test
    fun aWedgeCannotSpinTheSlideLoopForever() {
        val world = StubWorld()
        // Every sweep is blocked by a surface facing back the way the character came, which is
        // the shape that has no solution. The iteration cap is what makes this terminate.
        var sweepCount = 0
        world.onSweep = { _, _ ->
            sweepCount++
            Surface(Vec3f(-1f, 0f, 0f), 0f, BodyHandle(1L))
        }
        val character = controller(world)

        character.move(Vec3f(1f, 0f, 1f))

        // maxSlideIterations sweeps for the move, plus one ground probe.
        assertTrue(sweepCount <= config.maxSlideIterations + 1, "ran $sweepCount sweeps")
    }

    @Test
    fun teleportDoesNotSweep() {
        val world = StubWorld()
        val character = controller(world)

        character.teleport(Vec3f(50f, 10f, -3f))

        assertEquals(0, world.sweeps.size, "teleport is for spawning, and must not collide")
        assertEquals(50f, character.position.x)
        assertFalse(character.isGrounded)
    }

    // --- stepping ---------------------------------------------------------------------------

    /**
     * A world with a kerb of [riser] height across the character's path.
     *
     * Sweeps are answered by direction: upward is clear, downward finds the floor, and horizontal
     * is blocked only while the character is still below the kerb's top. That is the whole of what
     * a step probe has to reason about.
     */
    private fun kerbWorld(riser: Float, floorNormal: Vec3f = Vec3f(0f, 1f, 0f)): StubWorld =
        StubWorld().apply {
            onSweep = { from, to ->
                when {
                    to.y > from.y + 1e-6f -> null
                    to.y < from.y - 1e-6f -> Surface(floorNormal, 0f, BodyHandle(9L))
                    from.y >= riser -> null
                    else -> Surface(Vec3f(-1f, 0f, 0f), 0f, BodyHandle(2L))
                }
            }
        }

    /** Puts the controller in the grounded state a step-up requires, without asserting anything. */
    private fun KinematicCharacterController.settleOnGround() = move(Vec3f(0f, 0f, 0f))

    @Test
    fun aGroundedCharacterWalksOverAKerbInsteadOfIntoIt() {
        val world = kerbWorld(riser = 0.2f)
        val character = controller(world)
        character.settleOnGround()
        assertTrue(character.isGrounded, "precondition: the character starts on the ground")

        character.move(Vec3f(1f, 0f, 0f))

        // Without step-up the wall is hit at fraction 0 and x stays at the skin width.
        assertTrue(character.position.x > 0.5f, "expected to clear the kerb, x=${character.position.x}")
    }

    @Test
    fun anAirborneCharacterDoesNotStepUp() {
        val world = kerbWorld(riser = 0.2f)
        val character = controller(world)
        // No settle: never grounded, so this is a jump into the side of the kerb. Stepping here
        // would be climbing a wall in mid-air.
        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(character.position.x < 0.1f, "expected to be stopped by the kerb, x=${character.position.x}")
    }

    @Test
    fun aStepIsRefusedWhenThereIsNothingWalkableToLandOn() {
        // The far side is a 60-degree face, too steep to stand on, so the step must be abandoned
        // and the character left sliding against the wall instead of perched on it.
        val world = kerbWorld(riser = 0.2f, floorNormal = Vec3f(0.866f, 0.5f, 0f))
        val character = controller(world)
        character.move(Vec3f(0f, 0f, 0f))

        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(character.position.x < 0.1f, "expected no step onto a steep face, x=${character.position.x}")
    }

    @Test
    fun aFailedStepLeavesThePositionWhereSlidingPutIt() {
        val world = kerbWorld(riser = 0.2f, floorNormal = Vec3f(0.866f, 0.5f, 0f))
        val character = controller(world)
        character.move(Vec3f(0f, 0f, 0f))

        character.move(Vec3f(1f, 0f, 0f))

        // The rise must be undone, not half-applied: a character left at riser height is floating.
        assertTrue(abs(character.position.y) < 0.05f, "expected no leftover rise, y=${character.position.y}")
    }

    @Test
    fun walkingOffALedgeFollowsTheGroundDownInsteadOfFalling() {
        val world = StubWorld()
        // Flat ahead, and the floor is 0.2 below -- further than the ground probe reaches but
        // within the step-down distance.
        world.onSweep = { from, to ->
            when {
                to.y < from.y - 1e-6f -> {
                    val drop = from.y - to.y
                    if (drop >= 0.2f) Surface(Vec3f(0f, 1f, 0f), 0.2f / drop, BodyHandle(3L)) else null
                }
                else -> null
            }
        }
        val character = controller(world)
        character.move(Vec3f(0f, 0f, 0f))
        // The ground probe alone cannot reach it, so the character starts the move airborne...
        assertFalse(character.isGrounded)

        // ...which means step-down must not fire, because it only follows ground already left.
        character.move(Vec3f(1f, 0f, 0f))
        assertFalse(character.isGrounded, "a character that was not grounded must not be snapped down")
    }

    @Test
    fun aCharacterAlreadyOnTheGroundIsSnappedToItAfterMoving() {
        val world = StubWorld()
        var probeReach = 1f
        world.onSweep = { from, to ->
            if (to.y < from.y - 1e-6f) {
                val drop = from.y - to.y
                if (drop >= probeReach) Surface(Vec3f(0f, 1f, 0f), probeReach / drop, BodyHandle(4L)) else null
            } else {
                null
            }
        }
        val character = controller(world)
        probeReach = 0f
        character.move(Vec3f(0f, 0f, 0f))
        assertTrue(character.isGrounded, "precondition: grounded before the move")

        // Now the floor is out of the ground probe's reach but inside the step-down distance.
        probeReach = 0.2f
        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(character.isGrounded, "expected the character to follow the ground down")
        assertEquals(BodyHandle(4L), character.groundBody)
    }

    @Test
    fun everyBodyTheMoveIsStoppedByIsReported() {
        val world = StubWorld()
        world.onSweep = { from, to ->
            if (to.x > from.x + 1e-6f) Surface(Vec3f(-1f, 0f, 0f), 0f, BodyHandle(42L)) else null
        }
        val character = controller(world)
        val contacts = mutableListOf<Pair<BodyHandle, Float>>()
        character.onContact = { body, normal -> contacts += body to normal.x }

        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(contacts.isNotEmpty(), "walking into a body must report it")
        assertEquals(BodyHandle(42L), contacts.first().first)
        // The normal points back at the character, which is what a caller negates to push away.
        assertEquals(-1f, contacts.first().second)
    }

    @Test
    fun groundUnderfootIsNotReportedAsAContact() {
        val world = groundOnly(Vec3f(0f, 1f, 0f))
        val character = controller(world)
        var contacts = 0
        character.onContact = { _, _ -> contacts++ }

        character.move(Vec3f(0f, 0f, 0f))

        // The ground probe is a query, not a collision. Reporting it would have a character
        // shoving the floor it stands on once per fixed step.
        assertEquals(0, contacts, "standing still must not report contacts")
    }

    @Test
    fun teleportClearsGroundStateSoAFallingCharacterDoesNotArriveStanding() {
        val world = groundOnly(Vec3f(0f, 1f, 0f))
        val character = controller(world)
        character.move(Vec3f(0f, 0f, 0f))
        assertTrue(character.isGrounded, "precondition: grounded before the teleport")

        character.teleport(Vec3f(0f, 50f, 0f))

        // A respawn drops the character somewhere new; carrying the old ground over would leave a
        // caller convinced it can jump in mid-air, and stop it accumulating gravity.
        assertFalse(character.isGrounded)
        assertNull(character.groundBody)
        assertEquals(50f, character.position.y)
    }

    @Test
    fun aCharacterOnAMovingPlatformIsToldHowFastItIsMoving() {
        val platform = BodyHandle(11L)
        val world = groundOnly(Vec3f(0f, 1f, 0f), handle = platform)
        world.velocities = mapOf(platform to Vec3f(2f, 0f, -3f))
        val character = controller(world)

        character.move(Vec3f(0f, 0f, 0f))

        assertEquals(platform, character.groundBody)
        assertEquals(2f, character.groundVelocity.x)
        assertEquals(-3f, character.groundVelocity.z)
    }

    @Test
    fun theGroundVelocityIsNotAppliedByTheController() {
        val platform = BodyHandle(11L)
        val world = groundOnly(Vec3f(0f, 1f, 0f), handle = platform)
        world.velocities = mapOf(platform to Vec3f(50f, 0f, 0f))
        val character = controller(world)

        character.move(Vec3f(0f, 0f, 0f))

        // Reported, never added. A controller that carried the character itself would break the
        // guarantee that a move only ever does what it was asked, which is what makes the caller
        // able to decide whether walking against a lift should win.
        assertEquals(0f, character.position.x)
    }

    @Test
    fun steppingOffAPlatformClearsItsVelocity() {
        val platform = BodyHandle(11L)
        val world = groundOnly(Vec3f(0f, 1f, 0f), handle = platform)
        world.velocities = mapOf(platform to Vec3f(5f, 0f, 0f))
        val character = controller(world)
        character.move(Vec3f(0f, 0f, 0f))
        assertEquals(5f, character.groundVelocity.x)

        // Now there is nothing underfoot at all.
        world.onSweep = { _, _ -> null }
        character.move(Vec3f(0f, 0f, 0f))

        // Stale velocity here would carry a character along a lift it stepped off, forever.
        assertFalse(character.isGrounded)
        assertEquals(0f, character.groundVelocity.x)
    }

    @Test
    fun standingOnSomethingStaticReportsNoGroundVelocity() {
        val character = controller(groundOnly(Vec3f(0f, 1f, 0f)))

        character.move(Vec3f(0f, 0f, 0f))

        assertTrue(character.isGrounded)
        assertEquals(0f, character.groundVelocity.length3())
    }

    // --- crouch -------------------------------------------------------------------------------

    private val crouchConfig = CharacterConfig(
        shape = CapsuleShape(halfHeight = 0.9f, radius = 0.3f),
        crouchHalfHeight = 0.4f,
        skinWidth = 0.02f,
    )

    private fun crouchingController(world: PhysicsWorld) =
        KinematicCharacterController(world, crouchConfig, Vec3f(0f, 0f, 0f))

    @Test
    fun crouchingShrinksTheSweptCapsuleAndKeepsTheFeetPlanted() {
        val world = StubWorld()
        val character = crouchingController(world)

        character.crouch()
        character.move(Vec3f(1f, 0f, 0f))

        assertTrue(character.isCrouching)
        // Feet stay put, so the centre drops by the half-height lost: 0.9 - 0.4.
        assertTrue(abs(character.position.y + 0.5f) < 1e-4f, "y=${character.position.y}")
        // And what gets swept is the shorter capsule, not the standing one.
        val swept = world.sweptShapes.filterIsInstance<CapsuleShape>().last()
        assertEquals(0.4f, swept.halfHeight)
    }

    @Test
    fun standingUpUnderACeilingIsRefused() {
        val world = StubWorld()
        val character = crouchingController(world)
        character.crouch()
        // Anything above is a ceiling: the headroom sweep goes up, so this blocks it.
        world.onSweep = { from, to -> if (to.y > from.y) Surface(Vec3f(0f, -1f, 0f), 0f, BodyHandle(4L)) else null }

        val stood = character.standUp()

        // Standing anyway would put the capsule inside the ceiling, which a swept character cannot
        // get out of -- so the answer has to be no, and it has to be reported.
        assertFalse(stood)
        assertTrue(character.isCrouching)
    }

    @Test
    fun standingUpWithHeadroomRestoresTheFullHeight() {
        val world = StubWorld()
        val character = crouchingController(world)
        character.crouch()
        val crouchedY = character.position.y

        val stood = character.standUp()

        assertTrue(stood)
        assertFalse(character.isCrouching)
        assertTrue(abs(character.position.y - (crouchedY + 0.5f)) < 1e-4f, "y=${character.position.y}")
        character.move(Vec3f(1f, 0f, 0f))
        assertEquals(0.9f, world.sweptShapes.filterIsInstance<CapsuleShape>().last().halfHeight)
    }

    @Test
    fun aCharacterThatCannotCrouchIgnoresTheRequest() {
        // No crouchHalfHeight configured: the call is a no-op rather than an error, so a caller
        // can bind a key without knowing whether this character supports it.
        val character = controller(StubWorld())

        character.crouch()

        assertFalse(character.isCrouching)
        assertEquals(0f, character.position.y)
    }

    @Test
    fun standingUpWhenAlreadyStandingSucceedsWithoutSweeping() {
        val world = StubWorld()
        val character = crouchingController(world)

        assertTrue(character.standUp())
        assertEquals(0, world.sweeps.size, "no headroom check is needed to stay standing")
    }
}
