/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CameraPoseTest {
    @Test
    fun yawZeroLooksAlongMinusZAndAPositiveYawTurnsTowardPlusX() {
        val out = Vec3f()
        assertSame(out, CameraPose.forward(0f, 0f, out))
        assertVec(0f, 0f, -1f, out)
        assertVec(1f, 0f, 0f, CameraPose.forward(HALF_PI, 0f, out))
        assertVec(0f, -1f, 0f, CameraPose.forward(0f, -HALF_PI, out), "a negative pitch looks down")
    }

    @Test
    fun theOrbitEyeSitsBehindThePivotAlongTheView() {
        val eye = CameraPose.orbitEye(Vec3f(1f, 2f, 3f), yaw = 0f, pitch = 0f, distance = 5f, out = Vec3f())
        assertVec(1f, 2f, 8f, eye)
        val above = CameraPose.orbitEye(ZERO, yaw = 0f, pitch = -HALF_PI, distance = 4f, out = Vec3f())
        assertVec(0f, 4f, 0f, above, "looking straight down puts the eye above")
        assertVec(0f, 0f, 0f, CameraPose.orbitEye(ZERO, 0f, 0f, 0f, Vec3f()), "a zero distance is allowed, not rejected")
    }

    @Test
    fun aLookTurnsByThePixelsMovedAndPitchStopsAtTheLimit() {
        assertEquals(0.5f, CameraPose.turnedYaw(0f, dx = 100f, sensitivity = 0.005f), TOLERANCE)
        assertEquals(-0.5f, CameraPose.turnedYaw(0f, dx = 100f, sensitivity = 0.005f, invert = true), TOLERANCE)
        assertEquals(-0.5f, CameraPose.turnedPitch(0f, dy = 100f, sensitivity = 0.005f), TOLERANCE, "dragging down looks down")
        assertEquals(-CameraPose.PITCH_LIMIT, CameraPose.turnedPitch(0f, dy = 10_000f, sensitivity = 0.005f))
        assertEquals((85.0 * PI / 180.0).toFloat(), CameraPose.PITCH_LIMIT)
    }

    @Test
    fun scrollStepsKeepPaceWithDistanceAndAPinchIsAFactor() {
        assertEquals(9.5f, CameraPose.scrolledDistance(10f, scrollDeltaY = 1f, sensitivity = 0.5f), TOLERANCE)
        assertEquals(8.5f, CameraPose.scrolledDistance(10f, scrollDeltaY = 1f, sensitivity = 0.5f, proportion = 0.1f), TOLERANCE)
        assertEquals(10f, CameraPose.pinchedDistance(15f, scale = 1.5f), TOLERANCE)
    }

    @Test
    fun aPanMovesAgainstTheDragAcrossAndWithItUpTheView() {
        val right = Vec3f()
        val up = Vec3f()
        CameraPose.viewAxes(eye = Vec3f(0f, 0f, 5f), center = ZERO, right = right, up = up, scratch = Vec3f())
        assertVec(1f, 0f, 0f, right, "facing -Z, right is +X")
        assertVec(0f, 1f, 0f, up)
        assertVec(-2f, 1f, 0f, CameraPose.panShift(right, up, dx = 20f, dy = 10f, scale = 0.1f, out = Vec3f()))
    }

    @Test
    fun flyingMovesAlongTheViewAcrossItAndUpAsAUnitStep() {
        val out = Vec3f()
        assertVec(0f, 0f, -1f, CameraPose.flyDirection(0f, 0f, ahead = 1f, across = 0f, rise = 0f, out = out))
        assertVec(1f, 0f, 0f, CameraPose.flyDirection(0f, 0f, ahead = 0f, across = 1f, rise = 0f, out = out))
        val diagonal = CameraPose.flyDirection(0f, 0f, ahead = 1f, across = 0f, rise = 1f, out = out)
        assertVec(0f, SQRT_HALF, -SQRT_HALF, diagonal)
        assertVec(0f, 0f, 0f, CameraPose.flyDirection(0f, 0f, 0f, 0f, 0f, out), "nothing held, no move")
    }

    private fun assertVec(x: Float, y: Float, z: Float, actual: Vec3f, message: String? = null) {
        assertEquals(x, actual.x, TOLERANCE, message)
        assertEquals(y, actual.y, TOLERANCE, message)
        assertEquals(z, actual.z, TOLERANCE, message)
    }

    private companion object {
        const val TOLERANCE = 1e-5f
        val ZERO get() = Vec3f(0f, 0f, 0f)
        const val HALF_PI = (PI / 2).toFloat()
        const val SQRT_HALF = 0.70710677f
    }
}
