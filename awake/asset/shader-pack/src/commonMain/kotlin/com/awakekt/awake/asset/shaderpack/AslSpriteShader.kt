/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/** Unlit straight-alpha atlas quad: authored RGBA passes through without scene lighting. */
val SpriteShader = shader("sprite") {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(SpriteUniformLayout)
    val matrix = u.value("mvp")
    val atlas = u.value("uvTransform")
    val tint = u.value("tint")
    val image by texture2d(group = group, binding = 1)
    val imageSampler by sampler(group = group, binding = 2)
    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    vertex {
        val ins = inputsFrom(VertexFormat.PositionUv)
        out.position set (matrix * vec4(ins.input(VertexSemantic.Position), 1f.lit))
        uv set (ins.input(VertexSemantic.Uv) * vec2(atlas.x, atlas.y) + vec2(atlas.z, atlas.w))
    }
    fragment {
        colorOutput(textureSample(image, imageSampler, uv) * tint)
    }
}
