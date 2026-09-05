/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridTest {

    @Test
    fun intersectGroundPlaneDirectlyBelow() {
        val origin = Vec3f(0f, 10f, 0f)
        val dir = Vec3f(0f, -1f, 0f)
        val hit = Grid.intersectGroundPlane(origin, dir, y = 0f)
        assertNotNull(hit)
        assertEquals(0f, hit.x, 1e-4f)
        assertEquals(0f, hit.y, 1e-4f)
        assertEquals(0f, hit.z, 1e-4f)
    }

    @Test
    fun intersectGroundPlaneAtAngle() {
        val origin = Vec3f(0f, 5f, 0f)
        val dir = Vec3f(1f, -1f, 2f).normalized()
        val hit = Grid.intersectGroundPlane(origin, dir, y = 0f)
        assertNotNull(hit)
        assertEquals(0f, hit.y, 1e-4f)
        assertEquals(5f, hit.x, 1e-4f)
        assertEquals(10f, hit.z, 1e-4f)
    }

    @Test
    fun intersectGroundPlaneParallelReturnsNull() {
        val origin = Vec3f(0f, 5f, 0f)
        val dir = Vec3f(1f, 0f, 0f)
        val hit = Grid.intersectGroundPlane(origin, dir, y = 0f)
        assertNull(hit)
    }

    @Test
    fun intersectGroundPlanePointingAwayReturnsNull() {
        val origin = Vec3f(0f, 5f, 0f)
        val dir = Vec3f(0f, 1f, 0f)
        val hit = Grid.intersectGroundPlane(origin, dir, y = 0f)
        assertNull(hit)
    }

    @Test
    fun snapToGridSnapsCorrectly() {
        assertEquals(0f, Grid.snapToGrid(0.2f, 1.0f))
        assertEquals(1.0f, Grid.snapToGrid(0.8f, 1.0f))
        assertEquals(-2.0f, Grid.snapToGrid(-1.9f, 1.0f))
        assertEquals(0.5f, Grid.snapToGrid(0.48f, 0.5f))
    }

    @Test
    fun generateGridLinesProducesSymmetricGrid() {
        val lines = Grid.generateGridLines(extent = 10f, step = 1f, center = Vec3f.ZERO, y = 0f)
        assertTrue(lines.isNotEmpty())
        // Count for extent 10 step 1: 10 in each direction = 21 lines parallel to X, 21 parallel to Z = 42
        assertEquals(42, lines.size)
        // Check extent bounds
        val firstX = lines.first()
        assertEquals(-10f, firstX.first.x)
        assertEquals(10f, firstX.second.x)
    }
}
