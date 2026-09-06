/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CameraMathUtilsTest {

    @Test
    fun validateLensThrowsOnInvalidParameters() {
        CameraMathUtils.validateLensParameters(fovYDegrees = 60f, near = 0.1f, far = 1000f, aspect = 1.6f)

        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.validateLensParameters(fovYDegrees = 0f, near = 0.1f, far = 1000f)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.validateLensParameters(fovYDegrees = 180f, near = 0.1f, far = 1000f)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.validateLensParameters(fovYDegrees = 45f, near = 100f, far = 10f)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.validateLensParameters(fovYDegrees = 45f, near = 0f, far = 100f)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.validateLensParameters(fovYDegrees = 45f, near = 0.1f, far = 100f, aspect = 0f)
        }
    }

    @Test
    fun safeLookAtThrowsOnCoincidentCoordinates() {
        val samePoint = Vec3f(10f, 20f, 30f)
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.safeLookAt(eye = samePoint, center = samePoint)
        }
    }

    @Test
    fun safeLookAtThrowsOnNanCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            CameraMathUtils.safeLookAt(eye = Vec3f(Float.NaN, 0f, 0f), center = Vec3f.ZERO)
        }
    }

    @Test
    fun safeLookAtHealsCollinearVerticalLook() {
        val eye = Vec3f(0f, 0f, 0f)
        val center = Vec3f(0f, 10f, 0f)
        val mat = CameraMathUtils.safeLookAt(eye = eye, center = center, up = Vec3f.UP)

        assertTrue(mat.m00.isFinite() && mat.m11.isFinite() && mat.m22.isFinite())
        assertEquals(1f, mat.m33)
    }

    @Test
    fun forwardVectorClampsPitchAtSingularity() {
        val fwd = Vec3f()
        CameraMathUtils.forwardVector(yaw = 0f, pitch = 2.0f, out = fwd)
        assertTrue(fwd.y > 0.99f && fwd.y < 1.0f, "Y must be close to 1.0 but not 1.0, got ${fwd.y}")
        assertTrue(fwd.z < 0f, "Z must remain negative forward")

        CameraMathUtils.forwardVector(yaw = 0f, pitch = -2.0f, out = fwd)
        assertTrue(fwd.y < -0.99f && fwd.y > -1.0f, "Y must be close to -1.0, got ${fwd.y}")
    }

    @Test
    fun computeOrbitEyeCalculatesExpectedPosition() {
        val center = Vec3f(0f, 5f, 0f)
        val eye = Vec3f()

        CameraMathUtils.computeOrbitEye(center = center, yaw = 0f, pitch = 0f, distance = 10f, outEye = eye)
        assertEquals(0f, eye.x, 1e-4f)
        assertEquals(5f, eye.y, 1e-4f)
        assertEquals(10f, eye.z, 1e-4f)
    }

    @Test
    fun cameraPoseStateAndApplyZoomTest() {
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 50f)
        state.center.set(0f, 0f, 0f)
        state.recomputeEye()
        assertEquals(50f, state.eye.z, 1e-3f)

        val zoomed = CameraMathUtils.applyZoom(state, zoomDelta = 2.0f, rate = 0.1f, minDistance = 5f, maxDistance = 500f)
        assertTrue(zoomed)
        assertTrue(state.distance < 50f, "Zoom in must decrease distance")
        assertEquals(state.distance, state.eye.z, 1e-3f)

        val dragged = CameraMathUtils.applyOrbitDrag(state, dx = 50f, dy = 20f, sensitivity = 0.01f)
        assertTrue(dragged)
        assertEquals(0.5f, state.yaw, 1e-4f)
        assertEquals(-0.2f, state.pitch, 1e-4f)

        val panned = CameraMathUtils.applyHorizontalPan(state, strafeX = 10f, liftY = 5f, forwardZ = 20f, step = 1.0f)
        assertTrue(panned)
        assertEquals(5f, state.center.y, 1e-4f)

        val screenPanned = CameraMathUtils.applyScreenSpacePan(state, screenDx = 10f, screenDy = 20f, panScale = 0.01f)
        assertTrue(screenPanned)
    }

    @Test
    fun computeFreeFlyStepTranslatesAlongLookDirection() {
        val start = Vec3f(0f, 0f, 0f)
        val next = Vec3f()

        CameraMathUtils.computeFreeFlyStep(
            currentPos = start,
            yaw = 0f,
            pitch = 0f,
            moveInput = Vec3f(0f, 0f, 1f),
            speed = 10f,
            dt = 0.5f,
            outPos = next,
        )

        assertEquals(0f, next.x, 1e-4f)
        assertEquals(0f, next.y, 1e-4f)
        assertEquals(-5f, next.z, 1e-4f)
    }
}
