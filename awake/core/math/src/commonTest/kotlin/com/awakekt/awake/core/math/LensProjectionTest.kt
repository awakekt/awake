/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH = 800f
private const val HEIGHT = 600f
private const val TOLERANCE = 1e-2f

class CameraProjectionTest {

    private val camera = Lens(
        eye = Vec3f(0f, 0f, 6f),
        center = Vec3f(0f, 0f, 0f),
        fovYRadians = 1f,
        near = 0.1f,
        far = 100f,
    )
    private val viewProjection = camera.viewProjectionMatrix(WIDTH / HEIGHT, ClipSpace.WebGpu)

    private val vulkanViewProjection = camera.viewProjectionMatrix(WIDTH / HEIGHT, ClipSpace.Vulkan)

    @Test
    fun theTargetProjectsToTheCentreAndScreenYIsDown() {
        val centre = assertNotNull(
            camera.projectToViewport(
                Vec3f(0f, 0f, 0f),
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertEquals(WIDTH / 2f, centre.x, TOLERANCE)
        assertEquals(HEIGHT / 2f, centre.y, TOLERANCE)

        val above = assertNotNull(
            camera.projectToViewport(
                Vec3f(0f, 1f, 0f),
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertTrue(above.y < HEIGHT / 2f, "world +Y must project above the centre, was ${above.y}")
        val right = assertNotNull(
            camera.projectToViewport(
                Vec3f(1f, 0f, 0f),
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertTrue(
            right.x > WIDTH / 2f,
            "world +X must project right of the centre, was ${right.x}",
        )
    }

    @Test
    fun vulkanProjectionUsesThePositiveHeightViewportConvention() {
        val above = assertNotNull(
            camera.projectToViewport(
                Vec3f(0f, 1f, 0f),
                vulkanViewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.Vulkan,
            ),
        )
        assertTrue(
            above.y < HEIGHT / 2f,
            "world +Y must project above the centre in Vulkan, was ${above.y}",
        )

        val topRay = assertNotNull(
            camera.rayThroughViewport(
                WIDTH / 2f,
                1f,
                vulkanViewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.Vulkan,
            ),
        )
        assertTrue(
            topRay.direction.y > 0f,
            "a Vulkan top-edge ray must travel toward world +Y, was ${topRay.direction.y}",
        )
    }

    @Test
    fun vulkanRayThroughAnObjectsPixelHitsItsBox() {
        val box = Aabb(Vec3f(-0.5f, 0.5f, -0.5f), Vec3f(0.5f, 1.5f, 0.5f))
        val above = assertNotNull(
            camera.projectToViewport(
                Vec3f(0f, 1f, 0f),
                vulkanViewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.Vulkan,
            ),
        )
        val ray = assertNotNull(
            camera.rayThroughViewport(
                above.x,
                above.y,
                vulkanViewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.Vulkan,
            ),
        )
        assertNotNull(ray.intersectAabb(box), "the Vulkan ray through the box's pixel must hit it")
    }

    /** Projecting it anyway mirrors it onto the visible side, where it would respond to clicks. */
    @Test
    fun aPointBehindTheCameraDoesNotProject() {
        assertNull(
            camera.projectToViewport(
                Vec3f(0f, 0f, 10f),
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
    }

    /** The round trip is the real contract: a pixel becomes a ray, and a point on that ray
     * projects back to the same pixel. */
    @Test
    fun aRayThroughAPixelProjectsBackToThatPixel() {
        val pixel = Vec2(520f, 200f)
        val ray = assertNotNull(
            camera.rayThroughViewport(
                pixel.x,
                pixel.y,
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        val pointOnRay = ray.pointAt(5f)

        val projected =
            assertNotNull(
                camera.projectToViewport(
                    pointOnRay,
                    viewProjection,
                    WIDTH,
                    HEIGHT,
                    ClipSpace.WebGpu,
                ),
            )
        assertEquals(pixel.x, projected.x, TOLERANCE)
        assertEquals(pixel.y, projected.y, TOLERANCE)
    }

    @Test
    fun theCentrePixelAimsAtTheCameraTarget() {
        val ray = assertNotNull(
            camera.rayThroughViewport(
                WIDTH / 2f,
                HEIGHT / 2f,
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertEquals(0f, ray.direction.x, TOLERANCE)
        assertEquals(0f, ray.direction.y, TOLERANCE)
        assertEquals(-1f, ray.direction.z, TOLERANCE, "the centre pixel must look along -Z")
    }

    /** What picking is for: the ray through an object's pixel must hit that object's box. */
    @Test
    fun aRayThroughAnObjectsPixelHitsItsBox() {
        val box = Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))
        val centre = assertNotNull(
            camera.projectToViewport(
                Vec3f(0f, 0f, 0f),
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        val ray = assertNotNull(
            camera.rayThroughViewport(
                centre.x,
                centre.y,
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertNotNull(ray.intersectAabb(box), "the ray through the box's own pixel must hit it")

        val corner = assertNotNull(
            camera.rayThroughViewport(
                2f,
                2f,
                viewProjection,
                WIDTH,
                HEIGHT,
                ClipSpace.WebGpu,
            ),
        )
        assertNull(corner.intersectAabb(box), "a ray through the far corner must miss")
    }
}
