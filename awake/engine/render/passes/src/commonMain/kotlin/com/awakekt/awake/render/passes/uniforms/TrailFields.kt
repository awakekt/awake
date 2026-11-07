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

/** Per-draw fields of a trail: a strip of point pairs drawn unlit in its vertices' own colours. */
object TrailFields {
    /** Straight-alpha RGBA multiplied into every vertex's own colour and the texture it samples. */
    val Tint = UniformField("tint", GpuDataShape.Vec4)
}

/** A trail draw's payload, excluding the camera matrix supplied by pass preparation. */
val TrailExtraUniformLayout = UniformLayout(TrailFields.Tint)

/** Complete material ABI of a trail. */
val TrailUniformLayout = UniformLayout(UniformFields.Mvp, TrailFields.Tint)
