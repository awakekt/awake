/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals

class BillboardTest {
    private fun lens(eye: Vec3f) = Lens(eye = eye, center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

    /** Whatever its own rotation, a billboard keeps its place and size and turns its +Z to the eye. */
    @Test
    fun aBillboardKeepsPositionAndScaleAndFacesTheEye() {
        val world = Mat4().setEulerTRS(4f, 1f, -2f, 0.7f, 1.3f, 0.2f, 2f, 3f, 1f)

        val m = billboardMatrix(world, lens(eye = Vec3f(10f, 0f, 0f))).data

        assertEquals(listOf(4f, 1f, -2f), listOf(m[12], m[13], m[14]), "position kept")
        // The eye is along +X, so the mesh's +Z points along +X, its +X along -Z and +Y up.
        assertVector(listOf(0f, 0f, -2f), listOf(m[0], m[1], m[2]), "+X, scaled by 2")
        assertVector(listOf(0f, 3f, 0f), listOf(m[4], m[5], m[6]), "+Y, scaled by 3")
        assertVector(listOf(1f, 0f, 0f), listOf(m[8], m[9], m[10]), "+Z toward the eye")
    }

    /** Looking straight down has no up-based right vector, and still gives a valid turn. */
    @Test
    fun lookingStraightDownStillFacesTheEye() {
        val m = billboardMatrix(Mat4(), lens(eye = Vec3f(0f, 10f, 0f))).data

        assertVector(listOf(0f, 1f, 0f), listOf(m[8], m[9], m[10]), "+Z up toward the eye")
    }

    private fun assertVector(expected: List<Float>, actual: List<Float>, message: String) {
        expected.indices.forEach { assertEquals(expected[it], actual[it], 1e-5f, message) }
    }
}
