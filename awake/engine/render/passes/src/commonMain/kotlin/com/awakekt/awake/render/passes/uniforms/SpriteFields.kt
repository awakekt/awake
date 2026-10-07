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

/** Per-quad fields shared by atlas packing and the unlit shader. */
object SpriteFields {
    /** UV scale.xy and offset.zw; negative scales mirror within the selected cell. */
    val UvTransform = UniformField("uvTransform", GpuDataShape.Vec4)

    /** Straight-alpha RGBA multiplier. */
    val Tint = UniformField("tint", GpuDataShape.Vec4)
}

/** Per-sprite payload, excluding the camera matrix supplied by pass preparation. */
val SpriteExtraUniformLayout = UniformLayout(SpriteFields.UvTransform, SpriteFields.Tint)

/** Complete unlit atlas material ABI. */
val SpriteUniformLayout = UniformLayout(UniformFields.Mvp, SpriteFields.UvTransform, SpriteFields.Tint)
