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
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.syncTransforms
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The same simulation contract runs against JNI, JoltC and JoltPhysics.js through commonTest. */
class JoltPlane2DTest {
    private suspend fun world(gravity: Vec3f = Vec3f(), block: (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = gravity)
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun velocityWritesKeepThePlaneWhileAllowingTranslationAndSpin() = runTest {
        world { world ->
            val body = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 0f, PLANE_Z), Quat.IDENTITY,
                MotionType.DYNAMIC, degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            world.setLinearVelocity(body, Vec3f(2f, 3f, 10f))
            world.setAngularVelocity(body, Vec3f(4f, 5f, 1f))
            assertEquals(0f, world.getLinearVelocity(body).z, EPSILON)
            repeat(30) { world.step(STEP) }

            val pose = world.syncTransforms().single { it.handle == body }
            assertEquals(PLANE_Z, pose.position.z, EPSILON)
            assertTrue(pose.position.x > 0.5f && pose.position.y > 1f, "XY motion must remain free: $pose")
            assertEquals(0f, pose.rotation.x, EPSILON)
            assertEquals(0f, pose.rotation.y, EPSILON)
            assertTrue(abs(pose.rotation.z) > 0.1f, "rotation about Z must remain free: $pose")
        }
    }

    @Test
    fun impulsesAndGravityCannotMoveAnyConvexShapeOutOfItsPlane() = runTest {
        val shapes = listOf(
            BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), SphereShape(0.5f), CapsuleShape(0.5f, 0.5f),
            ConvexHullShape(floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0.5f)),
        )
        for (shape in shapes) {
            world(gravity = Vec3f(0f, -9.81f, 8f)) { world ->
                val body = world.createBody(
                    shape, Vec3f(0f, 0f, PLANE_Z), Quat.IDENTITY, MotionType.DYNAMIC,
                    degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
                )
                world.addImpulse(body, Vec3f(100f, 0f, 1000f))
                repeat(30) { world.step(STEP) }

                val pose = world.syncTransforms().single { it.handle == body }
                assertEquals(PLANE_Z, pose.position.z, EPSILON, "${shape::class}")
                assertEquals(0f, world.getLinearVelocity(body).z, EPSILON)
                assertTrue(pose.position.x > 0f && pose.position.y < -0.5f, "impulse and gravity must work in XY: $pose")
            }
        }
    }

    @Test
    fun contactsWithAnUnrestrictedBodyCannotPushOrTiltAPlanarBodyOutOfThePlane() = runTest {
        world { world ->
            val planar = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 0f, PLANE_Z), Quat.IDENTITY,
                MotionType.DYNAMIC, degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            val incoming = world.createBody(
                SphereShape(0.5f), Vec3f(0.4f, 0.3f, PLANE_Z + 2f), Quat.IDENTITY, MotionType.DYNAMIC,
            )
            world.setContactReporting(planar, true)
            world.setLinearVelocity(incoming, Vec3f(0f, 0f, -4f))
            var contacted = false
            repeat(60) {
                world.step(STEP)
                world.drainContacts { if (it.phase == ContactPhase.BEGAN) contacted = true }
                val pose = world.syncTransforms().single { it.handle == planar }
                assertEquals(PLANE_Z, pose.position.z, EPSILON)
                assertEquals(0f, pose.rotation.x, EPSILON)
                assertEquals(0f, pose.rotation.y, EPSILON)
            }
            assertTrue(contacted, "the off-plane body must actually collide with the planar body")
            assertTrue(world.getLinearVelocity(incoming).z > -3f, "the planar body must resist the incoming body")
        }
    }

    @Test
    fun moveKinematicRespectsThePlaneAndStillMovesInXY() = runTest {
        world { world ->
            val body = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 0f, PLANE_Z), Quat.IDENTITY,
                MotionType.KINEMATIC, degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            world.moveKinematic(body, Vec3f(1f, 2f, PLANE_Z + 10f), Quat.fromEuler(Vec3f(0.4f, 0.3f, 0.8f)), 1f)
            assertEquals(0f, world.getLinearVelocity(body).z, EPSILON)
            repeat(60) { world.step(STEP) }

            val pose = world.syncTransforms().single { it.handle == body }
            assertEquals(1f, pose.position.x, EPSILON)
            assertEquals(2f, pose.position.y, EPSILON)
            assertEquals(PLANE_Z, pose.position.z, EPSILON)
            assertEquals(0f, pose.rotation.x, EPSILON)
            assertEquals(0f, pose.rotation.y, EPSILON)
            assertTrue(abs(pose.rotation.z) > 0.1f)
        }
    }

    @Test
    fun choosingAPlaneDoesNotResetTheInitialPose() = runTest {
        world { world ->
            val rotation = Quat.fromEuler(Vec3f(0.2f, 0.3f, 0.4f))
            val body = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(1f, 2f, PLANE_Z), rotation,
                MotionType.DYNAMIC, degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            world.step(STEP)
            val pose = world.syncTransforms().single { it.handle == body }
            assertEquals(PLANE_Z, pose.position.z, EPSILON)
            assertEquals(rotation.x, pose.rotation.x, EPSILON)
            assertEquals(rotation.y, pose.rotation.y, EPSILON)
            assertEquals(rotation.z, pose.rotation.z, EPSILON)
            assertEquals(rotation.w, pose.rotation.w, EPSILON)
        }
    }

    @Test
    fun shiftingOriginMovesThePlaneWithoutUnlockingIt() = runTest {
        world { world ->
            val body = world.createBody(
                SphereShape(0.5f), Vec3f(0f, 0f, PLANE_Z), Quat.IDENTITY, MotionType.DYNAMIC,
                degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            world.shiftOrigin(Vec3f(10f, 4f, -3f))
            world.setLinearVelocity(body, Vec3f(1f, 0f, 5f))
            repeat(10) { world.step(STEP) }
            assertEquals(PLANE_Z - 3f, world.syncTransforms().single { it.handle == body }.position.z, EPSILON)
        }
    }

    @Test
    fun defaultAndExplicitAllBodiesStillMoveAndRotateIn3D() = runTest {
        world { world ->
            val shape = BoxShape(Vec3f(0.5f, 0.5f, 0.5f))
            val original = world.createBody(shape, Vec3f(), Quat.IDENTITY, MotionType.DYNAMIC)
            val explicit = world.createBody(shape, Vec3f(10f, 0f, 0f), Quat.IDENTITY, MotionType.DYNAMIC,
                degreesOfFreedom = DegreesOfFreedom.ALL)
            for (body in listOf(original, explicit)) {
                world.setLinearVelocity(body, Vec3f(0f, 0f, 2f))
                world.setAngularVelocity(body, Vec3f(1f, 0f, 0f))
            }
            repeat(30) { world.step(STEP) }
            for (pose in world.syncTransforms()) {
                assertTrue(pose.position.z > 0.5f, "$pose")
                assertTrue(abs(pose.rotation.x) > 0.1f, "$pose")
            }
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val EPSILON = 1e-4f
        const val PLANE_Z = 2f
    }
}
