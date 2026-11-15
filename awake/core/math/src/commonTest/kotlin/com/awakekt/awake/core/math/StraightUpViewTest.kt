/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A view looking straight up or down with the default +Y up has a usable basis: its frustum has
 * six planes and rejects what it cannot see, and its view matrix is a rotation (#1621).
 */
class StraightUpViewTest {
    private fun straight(direction: Float) = Lens(
        eye = Vec3f(-2.2f, 19_997.5f, -1.3f),
        center = Vec3f(-2.2f, 19_997.5f + direction, -1.3f),
        fovYRadians = 1f,
        near = 0.1f,
        far = 20f,
    ).apply {
        projection = Lens.Projection.Orthographic
        orthoHalfHeight = 1.2f
    }

    @Test
    fun itsFrustumHasSixPlanesAndRejectsWhatItCannotSee() {
        for (direction in listOf(1f, -1f)) {
            val planes = Frustum.planes(straight(direction), 1f)
            assertEquals(PLANES, planes.size, "looking ${if (direction > 0) "up" else "down"}")
            val town = Aabb(Vec3f(-50f, 90f, -50f), Vec3f(50f, 110f, 50f))
            assertFalse(planes.intersects(town), "a town 19,900 units away is outside")
            val inFront = Aabb(Vec3f(-2.5f, 19_997.5f + direction * 5f - 0.5f, -1.5f), Vec3f(-1.9f, 19_997.5f + direction * 5f + 0.5f, -1.1f))
            assertTrue(planes.intersects(inFront), "what is in front of it is inside")
        }
    }

    @Test
    fun itsViewMatrixIsARotation() {
        val view = Mat4.setLookAt(Vec3f.ZERO, Vec3f(0f, 1f, 0f), Vec3f(0f, 1f, 0f))
        val rows = listOf(
            Vec3f(view.m00, view.m01, view.m02),
            Vec3f(view.m10, view.m11, view.m12),
            Vec3f(view.m20, view.m21, view.m22),
        )
        for (row in rows) assertTrue(abs(row.length3() - 1f) < TOLERANCE, "unit row $row")
        assertTrue(abs(rows[0].dot(rows[1])) < TOLERANCE && abs(rows[1].dot(rows[2])) < TOLERANCE, "orthogonal rows $rows")
    }

    @Test
    fun aUsableUpIsKept() {
        val up = Vec3f(0f, 1f, 0f)
        assertTrue(lookAtUp(Vec3f(0f, 0f, -1f), up) === up, "a level view keeps its own up, and allocates nothing")
    }

    private companion object {
        const val PLANES = 6
        const val TOLERANCE = 1e-5f
    }
}
