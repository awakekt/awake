/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Maximum number of flare elements drawn in one sun-flare pass. */
const val MAX_SUN_FLARE_ELEMENTS: Int = 8

/** Uniform fields of the sun-flare post-geometry overlay pass (#1537). */
object SunFlareFields {
    /** `xy` = sun position in NDC, `z` = combined intensity/visibility scalar, `w` = viewport aspect ratio. */
    val SunScreen = UniformField("sunScreen", GpuDataShape.Vec4)

    /** `x` = active element count, `y` = 1 if textured else 0, `z` = depth sample radius in UV, `w` = unused. */
    val FlareParams = UniformField("flareParams", GpuDataShape.Vec4)

    /** Per element: `x` = axis position, `y` = NDC radius, `z` = ring flag (1 = ring, 0 = disc), `w` = intensity. */
    val ElementGeometry = UniformField("elementGeometry", GpuDataShape.Vec4, MAX_SUN_FLARE_ELEMENTS)

    /** Per element: `rgb` = tint colour, `a` = reserved. */
    val ElementColor = UniformField("elementColor", GpuDataShape.Vec4, MAX_SUN_FLARE_ELEMENTS)
}

/** Uniform layout of [sunFlareShader]. */
val SunFlareUniformLayout = UniformLayout(
    SunFlareFields.SunScreen,
    SunFlareFields.FlareParams,
    SunFlareFields.ElementGeometry,
    SunFlareFields.ElementColor,
)
