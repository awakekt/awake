/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cascade set that was handed only matrices still has to describe itself.
 *
 * The shader sizes both its bias and its normal offset in TEXELS, which it gets from a cascade's
 * world extent. A set built the short way -- one matrix, no extent, which is what a single-box
 * light produces -- reported an extent of zero, and zero extent means zero bias: every lit
 * surface self-shadows. The values have to come off the matrix rather than out of a default.
 */
class ShadowCascadeUniformsTest {

    @Test
    fun aSetBuiltFromMatricesAloneStillReportsItsScale() {
        val boxes = cascadeShadowBoxes(CAMERA, ASPECT, LIGHT, ClipSpace.Vulkan)

        val derived = ShadowCascadeUniforms(
            boxes.map { it.viewProjection },
            FloatArray(boxes.size) { Float.MAX_VALUE },
        )

        boxes.forEachIndexed { index, box ->
            assertEquals(
                2f / abs(box.projection.m00),
                derived.worldExtents[index],
                2f / abs(box.projection.m00) * TOLERANCE,
                "Cascade $index reported the wrong world width, so every texel-sized quantity in " +
                    "the shader is wrong by the same factor.",
            )
            assertEquals(
                abs(box.projection.m22),
                derived.depthScales[index],
                abs(box.projection.m22) * TOLERANCE,
                "Cascade $index reported the wrong depth scale, so its bias lands in the wrong " +
                    "units entirely.",
            )
        }
    }

    @Test
    fun everyCascadeHasANonZeroExtent() {
        val boxes = cascadeShadowBoxes(CAMERA, ASPECT, LIGHT, ClipSpace.Vulkan)

        val derived = ShadowCascadeUniforms(
            boxes.map { it.viewProjection },
            FloatArray(boxes.size) { Float.MAX_VALUE },
        )

        assertTrue(
            derived.worldExtents.all { it > 0f } && derived.depthScales.all { it > 0f },
            "Extents ${derived.worldExtents.toList()} and scales ${derived.depthScales.toList()}: " +
                "a zero disables bias and normal offset together, which is total self-shadowing.",
        )
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val TOLERANCE = 0.01f
        val LIGHT = Vec3f(0.4f, 1f, 0.2f)
        val CAMERA = Lens(
            eye = Vec3f(0f, 5f, 20f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 200f,
        )
    }
}
