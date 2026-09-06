/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals

class ScalarMathTest {

    @Test
    fun floatLerpComputesLinearInterpolation() {
        assertEquals(10f, lerp(10f, 20f, 0f))
        assertEquals(20f, lerp(10f, 20f, 1f))
        assertEquals(15f, lerp(10f, 20f, 0.5f))
        assertEquals(5f, lerp(10f, 20f, -0.5f))
        assertEquals(25f, lerp(10f, 20f, 1.5f))
    }

    @Test
    fun doubleLerpComputesLinearInterpolation() {
        assertEquals(10.0, lerp(10.0, 20.0, 0.0))
        assertEquals(20.0, lerp(10.0, 20.0, 1.0))
        assertEquals(15.0, lerp(10.0, 20.0, 0.5))
    }

    @Test
    fun floatClampConstrainsToBounds() {
        assertEquals(10f, clamp(5f, 10f, 20f))
        assertEquals(15f, clamp(15f, 10f, 20f))
        assertEquals(20f, clamp(25f, 10f, 20f))
    }

    @Test
    fun doubleClampConstrainsToBounds() {
        assertEquals(10.0, clamp(5.0, 10.0, 20.0))
        assertEquals(15.0, clamp(15.0, 10.0, 20.0))
        assertEquals(20.0, clamp(25.0, 10.0, 20.0))
    }

    @Test
    fun rayIntersectGroundPlaneHitsExpectedCoordinate() {
        // Ray pointing downward from (0, 10, 0) in direction (0, -1, 0)
        val ray = Ray(Vec3f(0f, 10f, 0f), Vec3f(0f, -1f, 0f))
        val hit = ray.intersectGroundPlane(groundY = 0f)
        assertEquals(0f, hit.x)
        assertEquals(0f, hit.y)
        assertEquals(0f, hit.z)
    }

    @Test
    fun rayIntersectGroundPlaneUsesFallbackWhenPointingAway() {
        // Ray pointing upward from (0, 10, 0) in direction (0, 1, 0)
        val ray = Ray(Vec3f(0f, 10f, 0f), Vec3f(0f, 1f, 0f))
        val hit = ray.intersectGroundPlane(groundY = 0f, fallbackDistance = 25f)
        assertEquals(0f, hit.x)
        assertEquals(35f, hit.y) // origin.y (10) + dir.y (1) * 25
        assertEquals(0f, hit.z)
    }
}
