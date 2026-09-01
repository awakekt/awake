/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.Vec4
import io.github.awakelab.awake.core.math.times
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cascades exist so the near field gets small texels and the far field still gets covered, and
 * so neither of those changes while the camera turns. Each test below pins one of those three.
 */
class ShadowCascadesTest {

    @Test
    fun splitsCoverTheWholeRangeAndGrowWithDistance() {
        val splits = cascadeSplitDistances(near = 0.5f, far = 500f, count = 4)

        assertEquals(4, splits.size)
        assertEquals(500f, splits.last(), TOLERANCE, "The last cascade must reach the far plane.")
        val depths = splits.mapIndexed { index, far -> far - if (index == 0) 0.5f else splits[index - 1] }
        depths.zipWithNext { nearer, farther ->
            assertTrue(
                farther > nearer,
                "Slices must deepen with distance ($depths). Equal depths are the uniform split, " +
                    "which spends the near cascade -- the one being looked at -- on empty space.",
            )
        }
    }

    @Test
    fun lambdaChoosesBetweenUniformAndLogarithmic() {
        val uniform = cascadeSplitDistances(near = 1f, far = 100f, count = 2, lambda = 0f)
        val logarithmic = cascadeSplitDistances(near = 1f, far = 100f, count = 2, lambda = 1f)

        assertEquals(50.5f, uniform[0], TOLERANCE, "Uniform halves the range.")
        assertEquals(10f, logarithmic[0], TOLERANCE, "Logarithmic takes the geometric mean.")
    }

    @Test
    fun everyCascadeCoversItsOwnSliceOfTheFrustum() {
        val camera = Lens(
            eye = Vec3f(0f, 5f, 20f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 200f,
        )
        val splits = cascadeSplitDistances(camera.near, camera.far, count = 3)
        val boxes = cascadeShadowBoxes(camera, ASPECT, LIGHT, ClipSpace.Vulkan, splits)

        var sliceNear = camera.near
        boxes.forEachIndexed { index, box ->
            val slice = Lens(camera.eye, camera.center, camera.up, camera.fovYRadians, sliceNear, splits[index])
            sliceNear = splits[index]
            Frustum.corners(slice, ASPECT).forEach { corner ->
                val clip = Vec4(corner.x, corner.y, corner.z, 1f) * box.viewProjection
                assertTrue(
                    abs(clip.x) <= 1f + TOLERANCE && abs(clip.y) <= 1f + TOLERANCE &&
                        clip.z >= -TOLERANCE && clip.z <= 1f + TOLERANCE,
                    "Cascade $index does not contain its own slice: corner $corner lands at $clip. " +
                        "A caster there would be clipped out of the map that is supposed to shadow it.",
                )
            }
        }
    }

    @Test
    fun aNearerCascadeHasSmallerTexels() {
        val camera = Lens(
            eye = Vec3f(0f, 5f, 20f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 200f,
        )
        val boxes = cascadeShadowBoxes(camera, ASPECT, LIGHT, ClipSpace.Vulkan)

        val extents = boxes.map { 1f / it.projection.m00 }
        extents.zipWithNext { nearer, farther ->
            assertTrue(
                farther > nearer,
                "Each cascade should cover more world than the one before it ($extents) -- that " +
                    "is the whole mechanism: same pixels, less world, finer shadows up close.",
            )
        }
    }

    @Test
    fun turningTheCameraDoesNotResizeACascade() {
        val far = Lens(eye = Vec3f(0f, 5f, 20f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.5f, far = 200f)
        val turned = Lens(
            eye = Vec3f(0f, 5f, 20f),
            center = Vec3f(14f, 0f, 14f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 200f,
        )

        val before = cascadeShadowBoxes(far, ASPECT, LIGHT, ClipSpace.Vulkan)
        val after = cascadeShadowBoxes(turned, ASPECT, LIGHT, ClipSpace.Vulkan)

        before.zip(after).forEachIndexed { index, (a, b) ->
            assertEquals(
                a.projection.m00,
                b.projection.m00,
                TOLERANCE,
                "Cascade $index changed size when the camera turned. That is what makes every " +
                    "shadow edge in the frame crawl during a pan, and it is why the fit is a " +
                    "sphere rather than a tight box.",
            )
        }
    }


    @Test
    fun theNearCascadeResolvesCentimetres() {
        // The studio camera: 45 degrees, 16:9, seeing 100m.
        val camera = Lens(
            eye = Vec3f(0f, 5f, 14f),
            center = Vec3f(0f, 0.5f, 0f),
            fovYRadians = 0.785f,
            near = 0.1f,
            far = 100f,
        )

        val near = cascadeShadowBoxes(camera, WIDESCREEN, LIGHT, ClipSpace.Vulkan).first()
        val texelMetres = (2f / near.projection.m00) / DEFAULT_SHADOW_MAP_SIZE

        assertTrue(
            texelMetres < MAX_NEAR_TEXEL_METRES,
            "The near cascade resolves ${texelMetres * 100f}cm per texel, over the " +
                "${MAX_NEAR_TEXEL_METRES * 100f}cm this asserts. A shadow edge is a staircase of " +
                "these, so this number IS the blockiness in a screenshot -- and it grows with " +
                "anything that grows the near slice: a wider fit aspect, a farther shadow " +
                "distance, fewer cascades.",
        )
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val WIDESCREEN = 16f / 9f

        /**
         * 1.5cm per texel at the camera, against a measured 1.34.
         *
         * The fit this replaced measured 1.91cm for the same camera, because it fitted cascades
         * to a frustum three times wider than any real viewport. A shadow edge is a staircase of
         * exactly these texels, so this number IS the blockiness in a screenshot. The bound sits
         * between the two so a regression back toward the conservative aspect fails here rather
         * than merely looking worse on screen.
         *
         * Capping the shadow DISTANCE (rather than fitting out to the camera's far plane) would
         * take this to 0.55cm, and is what every engine does -- but it regressed a verified
         * scene when tried here, so it is not in yet. See the note in `docs/decisions`.
         */
        const val MAX_NEAR_TEXEL_METRES = 0.015f
        const val TOLERANCE = 0.01f
        val LIGHT = Vec3f(0.4f, 1f, 0.2f)
    }
}
