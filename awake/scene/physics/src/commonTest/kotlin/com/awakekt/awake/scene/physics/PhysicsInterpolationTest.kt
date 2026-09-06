/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BodyTransform
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.RecordingPhysicsWorld
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a body drawn between two fixed steps lands between the two poses it had.
 *
 * The scripted world here is the point: real physics would make "halfway" a thing to be derived
 * rather than known, and this has to compare against an answer arithmetic already gives.
 */
class PhysicsInterpolationTest {

    private fun sceneWithBody(): Triple<World, Transform, RecordingPhysicsWorld> {
        val world = World()
        val entity = world.create()
        val transform = Transform(position = Vec3f(0f, 0f, 0f))
        world.add(entity, transform)
        world.add(entity, PhysicsBody(shape = SphereShape(radius = 1f), motionType = MotionType.DYNAMIC))
        return Triple(world, transform, RecordingPhysicsWorld())
    }

    private fun RecordingPhysicsWorld.report(handle: BodyHandle, position: Vec3f, rotation: Quat = Quat.IDENTITY) {
        scriptedTransforms = listOf(BodyTransform(handle, position, rotation))
    }

    private fun assertNear(expected: Float, actual: Float, what: String) {
        assertTrue(abs(expected - actual) < 0.001f, "$what: expected $expected but was $actual")
    }

    @Test
    fun aBodyIsDrawnBetweenTheTwoPosesItHad() {
        val (world, transform, physics) = sceneWithBody()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val handle = physics.created.single()

        physics.report(handle, Vec3f(0f, 10f, 0f))
        system.update(world, STEP)
        physics.report(handle, Vec3f(0f, 20f, 0f))
        system.update(world, STEP)
        system.interpolate(world, 0.25f)

        // A quarter of the way from the pose two steps ago to the newest one. Not 20: drawing the
        // newest pose is what produces the stutter this exists to remove.
        assertNear(12.5f, transform.position.y, "the drawn height")
    }

    @Test
    fun alphaZeroDrawsTheOlderOfTheTwo() {
        val (world, transform, physics) = sceneWithBody()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val handle = physics.created.single()

        physics.report(handle, Vec3f(0f, 10f, 0f))
        system.update(world, STEP)
        physics.report(handle, Vec3f(0f, 20f, 0f))
        system.update(world, STEP)
        system.interpolate(world, 0f)

        // The trade this pattern makes, stated as a test: rendering is one step behind the
        // simulation. A change here means somebody swapped it for extrapolation.
        assertNear(10f, transform.position.y, "the drawn height at alpha zero")
    }

    @Test
    fun rotationIsBlendedAsAQuaternionRatherThanAsEulerAngles() {
        val (world, transform, physics) = sceneWithBody()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val handle = physics.created.single()

        // Either side of the wrap: a body spinning about Y from just under a full turn to just
        // past it. Averaging the Euler angles gives half a turn -- the body snapping right round,
        // once per revolution -- where the quaternions give the small step actually taken.
        val nearlyRound = (2 * PI - 0.2).toFloat()
        val justPast = 0.2f
        physics.report(handle, Vec3f(0f, 0f, 0f), Quat.fromEuler(Vec3f(0f, nearlyRound, 0f)))
        system.update(world, STEP)
        physics.report(handle, Vec3f(0f, 0f, 0f), Quat.fromEuler(Vec3f(0f, justPast, 0f)))
        system.update(world, STEP)
        system.interpolate(world, 0.5f)

        // Halfway between them is zero, give or take the sign the wrap is reported with.
        val drawn = transform.rotation.y
        val fromZero = minOf(abs(drawn), abs(abs(drawn) - (2 * PI).toFloat()))
        assertTrue(fromZero < 0.05f, "expected the blend to land near a full turn but it was $drawn")
    }

    @Test
    fun aBodyThatHasSettledIsStillDrawnWhereItStopped() {
        val (world, transform, physics) = sceneWithBody()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val handle = physics.created.single()
        physics.report(handle, Vec3f(0f, 7f, 0f))
        system.update(world, STEP)

        // A settled body stops being awake and a backend that enumerates only its active set stops
        // reporting it -- so this is what every frame after it comes to rest looks like.
        physics.scriptedTransforms = emptyList()
        system.update(world, STEP)
        system.interpolate(world, 0.5f)

        assertNear(7f, transform.position.y, "a settled body's drawn height")
    }

    @Test
    fun aSystemThatIsNeverInterpolatedBehavesAsItAlwaysDid() {
        val (world, transform, physics) = sceneWithBody()
        val system = PhysicsSystem(physics)
        system.update(world, STEP)
        val handle = physics.created.single()

        physics.report(handle, Vec3f(0f, 42f, 0f))
        system.update(world, STEP)

        // update still writes the newest pose, so a caller that does not interpolate is unaffected
        // by any of this -- which is what makes the change safe to land before anything wires it.
        assertEquals(42f, transform.position.y)
    }

    private companion object {
        const val STEP = 1f / 60f
    }
}
