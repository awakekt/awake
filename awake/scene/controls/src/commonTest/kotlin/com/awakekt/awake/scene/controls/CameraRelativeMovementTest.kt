/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the defect that made the camera lose its subject: the movement basis was built from
 * an un-normalized `center - eye`, so walking speed silently scaled with the camera's distance
 * to its target. The player then outran the smoothed follow camera and left the frame.
 */
class CameraRelativeMovementTest {
    @Test
    fun forwardSpeedIsIndependentOfCameraDistance() {
        val near = distanceTravelledForward(cameraDistance = 2f)
        val far = distanceTravelledForward(cameraDistance = 50f)

        assertEquals(near, far, ABSOLUTE_TOLERANCE)
        // speed * delta, i.e. the basis really is unit length rather than merely consistent.
        assertEquals(SPEED * DELTA, far, ABSOLUTE_TOLERANCE)
    }

    @Test
    fun strafeAndForwardMoveAtTheSameSpeed() {
        val forward = distanceTravelledForward(cameraDistance = 10f)
        val strafe = travel(cameraDistance = 10f) { moveX = 1f }.let { abs(it.x) }

        assertEquals(forward, strafe, ABSOLUTE_TOLERANCE)
    }

    @Test
    fun movementFollowsWhereTheCameraLooks() {
        // Camera parked on +X looking back toward the origin, so "forward" is -X, not -Z.
        val moved = travel(cameraDistance = 10f, eye = Vec3f(10f, 0f, 0f)) { moveZ = 1f }

        assertTrue(moved.x < -ABSOLUTE_TOLERANCE, "expected to move along -X, got $moved")
        assertEquals(0f, moved.z, ABSOLUTE_TOLERANCE)
    }

    private fun distanceTravelledForward(cameraDistance: Float): Float =
        abs(travel(cameraDistance) { moveZ = 1f }.z)

    /** Runs one movement step and returns how far the subject moved. */
    private fun travel(
        cameraDistance: Float,
        eye: Vec3f = Vec3f(0f, 0f, cameraDistance),
        intent: MovementControl.() -> Unit,
    ): Vec3f {
        val world = World()

        val cameraEntity = world.create()
        world.add(
            cameraEntity,
            Camera(
                Lens.perspective(eye = eye, center = Vec3f.ZERO),
            ),
        )
        world.add(cameraEntity, ActiveCamera())

        val subject = world.create()
        world.add(subject, Transform())
        world.add(subject, MovementControl().apply(intent))

        MatrixRelativeMovementSystem(speed = SPEED).update(world, DELTA)

        return world.get(subject, Transform::class)!!.position
    }

    private companion object {
        const val SPEED = 5f
        const val DELTA = 0.1f
        const val ABSOLUTE_TOLERANCE = 0.0001f
    }
}
