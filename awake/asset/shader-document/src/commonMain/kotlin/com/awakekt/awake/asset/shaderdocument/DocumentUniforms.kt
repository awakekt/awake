/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** The bind group every document's resources are in: the material slot, as for any content feature. */
internal val MATERIAL_GROUP: Int = BindingLayout.Standard.slot(BindingSemantic.Material)

/** The uniform block's binding. */
internal const val UNIFORM_BINDING = 0

/** The sampler's binding, shared by every texture. */
internal const val SAMPLER_BINDING = 1

/** The first texture's binding; the others follow it in declaration order. */
internal const val FIRST_TEXTURE_BINDING = 2

/** How many textures a document can bind: the fixed slots `t0` to `t3`. */
internal const val TEXTURE_SLOTS = 4

/**
 * The uniform block one compiled document reads, as fields the WGSL and the per-frame writer share.
 *
 * Every field is a `vec4` or a `mat4`, and parameters are one `array<vec4f, N>`. `UniformLayout` packs
 * fields back to back while WGSL aligns them, and the two agree only when every field fills whole
 * 16-byte rows.
 *
 * The fields are made once per document and a [layout] once per attach: the writer checks fields by
 * identity, while an engine registers one pipeline per layout instance, so the same document can be
 * attached twice.
 */
internal class DocumentUniformFields(parameterCount: Int) {
    val viewProjection = UniformField("viewProjection", GpuDataShape.Mat4)
    val inverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val model = UniformField("model", GpuDataShape.Mat4)
    val cameraPosition = UniformField("cameraPosition", GpuDataShape.Vec4)
    val sunDirection = UniformField("sunDirection", GpuDataShape.Vec4)

    /** Seconds since the effect started, and since the previous frame. */
    val clock = UniformField("clock", GpuDataShape.Vec4)

    /** Width, height, 1 / width, 1 / height, in pixels. */
    val viewport = UniformField("viewport", GpuDataShape.Vec4)
    val params: UniformField? = if (parameterCount > 0) UniformField("params", GpuDataShape.Vec4, parameterCount) else null

    private val all = listOfNotNull(viewProjection, inverseViewProjection, model, cameraPosition, sunDirection, clock, viewport, params)

    // Once per attach, never per frame: the copy the spread makes is not worth a second constructor.
    @Suppress("SpreadOperator")
    fun layout(): UniformLayout = UniformLayout(*all.toTypedArray())
}
