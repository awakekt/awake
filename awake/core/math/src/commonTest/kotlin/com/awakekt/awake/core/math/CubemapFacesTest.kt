/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CubemapFacesTest {

    @Test
    fun cubemapHasExactlySixDistinctFaces() {
        assertEquals(6, CubemapFaces.COUNT)
        assertEquals(6, CubemapFaces.Faces.size)

        // All 6 directions are orthogonal unit vectors
        val directions = CubemapFaces.Faces.map { it.direction }
        assertEquals(6, directions.distinct().size)

        // Directions and up vectors are mutually perpendicular
        CubemapFaces.Faces.forEach { face ->
            assertEquals(0f, face.direction.dot(face.up), 0.0001f)
            assertEquals(1f, face.direction.length3(), 0.0001f)
            assertEquals(1f, face.up.length3(), 0.0001f)
        }
    }

    @Test
    fun canonicalFaceDirectionsMatchVulkanWebGpuConventions() {
        // Face 0: +X
        assertEquals(Vec3f.RIGHT, CubemapFaces.orientation(0).direction)
        assertEquals(Vec3f.DOWN, CubemapFaces.orientation(0).up)

        // Face 1: -X
        assertEquals(Vec3f.LEFT, CubemapFaces.orientation(1).direction)
        assertEquals(Vec3f.DOWN, CubemapFaces.orientation(1).up)

        // Face 2: +Y
        assertEquals(Vec3f.UP, CubemapFaces.orientation(2).direction)
        assertEquals(Vec3f.FORWARD, CubemapFaces.orientation(2).up)

        // Face 3: -Y
        assertEquals(Vec3f.DOWN, CubemapFaces.orientation(3).direction)
        assertEquals(Vec3f.BACK, CubemapFaces.orientation(3).up)

        // Face 4: +Z
        assertEquals(Vec3f.BACK, CubemapFaces.orientation(4).direction)
        assertEquals(Vec3f.DOWN, CubemapFaces.orientation(4).up)

        // Face 5: -Z
        assertEquals(Vec3f.FORWARD, CubemapFaces.orientation(5).direction)
        assertEquals(Vec3f.DOWN, CubemapFaces.orientation(5).up)
    }

    @Test
    fun lensProducesSquareAspectAndNinetyDegreeFov() {
        val eye = Vec3f(5f, 10f, -3f)
        val lens = CubemapFaces.lens(eye = eye, faceIndex = 2, near = 0.1f, far = 50f)

        assertEquals(eye, lens.eye)
        assertEquals(eye + Vec3f.UP, lens.center)
        assertEquals(Vec3f.FORWARD, lens.up)
        assertEquals((PI / 2.0).toFloat(), lens.fovYRadians, 0.0001f)
        assertEquals(0.1f, lens.near)
        assertEquals(50f, lens.far)

        val vpVulkan = lens.viewProjectionMatrix(aspect = 1.0f, clipSpace = ClipSpace.Vulkan)
        val vpWebGpu = lens.viewProjectionMatrix(aspect = 1.0f, clipSpace = ClipSpace.WebGpu)

        // Vulkan inverts Y in clip space
        assertEquals(-vpVulkan.m11, vpWebGpu.m11, 0.0001f)
        assertTrue(vpVulkan.inverse() != null)
    }
}
