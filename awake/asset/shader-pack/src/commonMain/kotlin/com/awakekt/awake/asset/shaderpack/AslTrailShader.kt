/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.TrailUniformLayout
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * A trail: a strip of point pairs, unlit, in each vertex's own straight-alpha colour times the
 * texture it samples (u along the strip, v across it) times the draw's tint. A trail with no texture
 * samples a white one, so its colour is its vertices'.
 */
val TrailShader = shader("trail") {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(TrailUniformLayout)
    val matrix = u.value("mvp")
    val tint = u.value("tint")
    val image by texture2d(group = group, binding = 1)
    val imageSampler by sampler(group = group, binding = 2)
    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec4, location = 0)
    val uv by out.varying(GpuDataShape.Vec2, location = 1)
    vertex {
        val ins = inputsFrom(VertexFormat.PositionRgbaUv)
        out.position set (matrix * vec4(ins.input(VertexSemantic.Position), 1f.lit))
        color set ins.input(VertexSemantic.Color)
        uv set ins.input(VertexSemantic.Uv)
    }
    fragment {
        colorOutput(color * textureSample(image, imageSampler, uv) * tint)
    }
}
