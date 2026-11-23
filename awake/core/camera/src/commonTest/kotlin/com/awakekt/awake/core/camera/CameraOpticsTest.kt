/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraOpticsTest {

    @Test
    fun focalLengthRoundTripsWithFovY() {
        val lenses = listOf(18f, 24f, 35f, 50f, 85f, 135f)
        for (mm in lenses) {
            val fovY = CameraOptics.focalLengthMmToFovYRadians(mm)
            val roundTrip = CameraOptics.fovYRadiansToFocalLengthMm(fovY)
            assertEquals(mm, roundTrip, 1e-3f, "Round-trip for ${mm}mm")
        }
        // Standard 50mm on 24mm vertical sensor gives ~26.99 degrees vertical FOV.
        val fov50Deg = CameraOptics.focalLengthMmToFovYRadians(50f) * (180f / PI.toFloat())
        assertEquals(26.99f, fov50Deg, 0.1f)
    }

    @Test
    fun dollyZoomPreservesSubjectVerticalFraming() {
        val startFov = CameraOptics.focalLengthMmToFovYRadians(35f)
        val endFov = CameraOptics.focalLengthMmToFovYRadians(85f)
        val startDistance = 10f
        val endDistance = CameraOptics.dollyZoomDistance(startDistance, startFov, endFov)

        val startHalfHeight = startDistance * tan(startFov * 0.5f)
        val endHalfHeight = endDistance * tan(endFov * 0.5f)
        assertEquals(startHalfHeight, endHalfHeight, 1e-4f)
        assertTrue(endDistance > startDistance, "Zooming in to telephoto must dolly back")
    }

    @Test
    fun rolledUpAndBasisStayOrthonormalEvenWhenLookingStraightDown() {
        val forward = Vec3f()
        val right = Vec3f()
        val up = Vec3f()

        // Horizontal look with 30-degree Dutch tilt.
        CameraOptics.cameraBasis(
            eye = Vec3f(0f, 2f, 5f),
            center = Vec3f(0f, 2f, 0f),
            rollRadians = (30.0 * PI / 180.0).toFloat(),
            outForward = forward,
            outRight = right,
            outUp = up,
        )
        assertEquals(1f, forward.length3(), 1e-4f)
        assertEquals(1f, right.length3(), 1e-4f)
        assertEquals(1f, up.length3(), 1e-4f)
        assertTrue(abs(forward.dot(up)) < 1e-4f)
        assertTrue(abs(forward.dot(right)) < 1e-4f)
        assertTrue(abs(right.dot(up)) < 1e-4f)
        assertTrue(up.x > 0.4f, "Positive roll looking along -Z tilts up toward +X")

        // Straight-down bird's-eye look must not produce NaN or zero vectors.
        CameraOptics.cameraBasis(
            eye = Vec3f(0f, 20f, 0f),
            center = Vec3f(0f, 0f, 0f),
            rollRadians = 0.25f,
            outForward = forward,
            outRight = right,
            outUp = up,
        )
        assertEquals(1f, up.length3(), 1e-4f)
        assertEquals(1f, right.length3(), 1e-4f)
        assertTrue(abs(forward.dot(up)) < 1e-4f)
    }
}
