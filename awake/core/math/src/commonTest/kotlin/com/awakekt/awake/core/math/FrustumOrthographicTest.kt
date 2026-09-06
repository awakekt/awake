/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An orthographic camera sees a box, and everything that reads a frustum has to be told so.
 *
 * [Frustum.corners] derived a cone from `fovYRadians` whatever the lens said, so for an ortho
 * camera it described a volume the camera does not see -- and four features read it: culling, the
 * spatial index's frustum query, the debug overlay, and the shadow cascade fit. The cascade fit
 * is where it showed: a 5m-wide top-down view was fitted with a 480m cascade, whose texels are
 * coarse enough that a lit surface self-shadows.
 */
class FrustumOrthographicTest {

    @Test
    fun anOrthographicLensGivesABoxRatherThanACone() {
        val corners = Frustum.corners(orthographic(halfHeight = 5f), ASPECT)

        val nearHalfHeight = (corners[3] - corners[0]).length3() / 2f
        val farHalfHeight = (corners[7] - corners[4]).length3() / 2f
        assertEquals(5f, nearHalfHeight, TOLERANCE, "The near plane is the ortho half height.")
        assertEquals(
            5f,
            farHalfHeight,
            TOLERANCE,
            "The far plane is the SAME half height: an ortho view does not widen with distance, " +
                "and a fit that thinks it does covers a volume nothing is in.",
        )
    }

    @Test
    fun theFieldOfViewIsIgnoredWhileOrthographic() {
        val narrow = Frustum.corners(orthographic(halfHeight = 5f).apply { fovYRadians = 0.2f }, ASPECT)
        val wide = Frustum.corners(orthographic(halfHeight = 5f).apply { fovYRadians = 1.4f }, ASPECT)

        narrow.zip(wide).forEach { (a, b) ->
            assertTrue(
                (a - b).length3() < TOLERANCE,
                "Changing the field of view moved an ortho corner from $a to $b. The projection " +
                    "never reads that field, so anything fitted to it describes a different " +
                    "camera than the one rendering.",
            )
        }
    }

    @Test
    fun aPerspectiveLensStillWidensWithDistance() {
        val corners = Frustum.corners(
            Lens(
                eye = Vec3f(0f, 0f, 0f),
                center = Vec3f(0f, 0f, -1f),
                fovYRadians = 1f,
                near = 1f,
                far = 10f,
            ),
            ASPECT,
        )

        val nearHalfHeight = (corners[3] - corners[0]).length3() / 2f
        val farHalfHeight = (corners[7] - corners[4]).length3() / 2f
        assertTrue(
            abs(farHalfHeight / nearHalfHeight - 10f) < TOLERANCE,
            "A perspective frustum's far plane is far/near times the near one; got " +
                "$nearHalfHeight and $farHalfHeight.",
        )
    }

    private fun orthographic(halfHeight: Float) = Lens(
        eye = Vec3f(0f, 0f, 0f),
        center = Vec3f(0f, 0f, -1f),
        fovYRadians = 1f,
        near = 1f,
        far = 10f,
    ).apply {
        projection = Lens.Projection.Orthographic
        orthoHalfHeight = halfHeight
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val TOLERANCE = 0.001f
    }
}
