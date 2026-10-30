/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout

/** Per-draw fields of distance-field text placed in a scene, shared by its draw builder and its shader. */
object WorldTextFields {
    /** Straight-alpha RGBA multiplied into every glyph's own vertex colour. */
    val Tint = UniformField("tint", GpuDataShape.Vec4)

    /**
     * The glyph field: x is the distance field's spread in atlas texels, the rest is reserved for
     * decorations drawn from the same field (an outline's width, a shadow's softness) and is 0.
     */
    val Field = UniformField("field", GpuDataShape.Vec4)
}

/** A text draw's payload, excluding the camera matrix supplied by pass preparation. */
val WorldTextExtraUniformLayout = UniformLayout(WorldTextFields.Tint, WorldTextFields.Field)

/** Complete material ABI of distance-field text placed in a scene. */
val WorldTextUniformLayout = UniformLayout(UniformFields.Mvp, WorldTextFields.Tint, WorldTextFields.Field)
