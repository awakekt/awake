/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dpdx
import com.awakekt.awake.asset.shaderdsl.dpdy
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.WorldTextUniformLayout
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Distance-field glyph quads placed in a scene, unlit, in straight alpha.
 *
 * The edge's width, in field units, is how far the field changes across one screen pixel, so the edge
 * stays one pixel wide however far the text is and however steeply it is seen:
 * - **Measured:** the length of the screen-space gradient of the field's true signed distance (the
 *   atlas's alpha). It points across the glyph's edge, so at a grazing angle the edge widens only
 *   along the squeezed axis.
 * - **Bounded by the footprint:** a pixel spans between the narrowest and widest of its two screen
 *   axes in atlas texels, from the uv derivatives, and the field changes by `1 / field.x` a texel; the
 *   measured width is kept within those, so it stays right where the field is flat or saturated.
 * - **At most one field unit:** once a pixel spans more than the field's range, as it does far away,
 *   the mip-filtered field is read as the pixel's coverage, which keeps thin strokes instead of
 *   thinning them away.
 *
 * The coverage is the multi-channel median through that ramp, which keeps corners sharp.
 *
 * `worldTextEdgeWidth` and `worldTextCoverage` in `awake:worldtext` are the CPU copy, held to this
 * fragment by `WorldTextShaderTwinTest`, which supplies `atlas`, `atlasSizeU`, `trueGradient`,
 * `uvAcross` and `uvDown` by name: rename them there too.
 */
val WorldTextShader = shader("world_text") {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(WorldTextUniformLayout)
    val matrix = u.value("mvp")
    val tint = u.value("tint")
    val field = u.value("field")
    val atlasImage by texture2d(group = group, binding = 1)
    val atlasSampler by sampler(group = group, binding = 2)
    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    val color by out.varying(GpuDataShape.Vec4, location = 1)
    vertex {
        val ins = inputsFrom(VertexFormat.PositionUvColor)
        out.position set (matrix * vec4(ins.input(VertexSemantic.Position), 1f.lit))
        uv set ins.input(VertexSemantic.Uv)
        color set ins.input(VertexSemantic.Color)
    }
    fragment {
        val atlas = let("atlas", textureSample(atlasImage, atlasSampler, uv))
        val atlasSizeU = let("atlasSizeU", textureDimensions(atlasImage))
        val atlasSize = vec2(toF32(atlasSizeU.x), toF32(atlasSizeU.y))
        // Outside any branch, so the derivatives are taken in uniform control flow.
        val trueGradient = let("trueGradient", vec2(dpdx(atlas.a), dpdy(atlas.a)))
        val uvAcross = let("uvAcross", dpdx(uv))
        val uvDown = let("uvDown", dpdy(uv))
        val texelsAcross = length(uvAcross * atlasSize)
        val texelsDown = length(uvDown * atlasSize)
        val perTexel = 1f.lit / field.x
        val narrowest = min(texelsAcross, texelsDown) * perTexel
        val widest = max(texelsAcross, texelsDown) * perTexel
        val edgeWidth = let(
            "edgeWidth",
            clamp(min(clamp(length(trueGradient), narrowest, widest), 1f.lit), MIN_EDGE_WIDTH.lit, 1f.lit),
        )
        val signedDistance = let("signedDistance", max(min(atlas.r, atlas.g), min(max(atlas.r, atlas.g), atlas.b)))
        val coverage = let("coverage", clamp((signedDistance - 0.5f.lit) / edgeWidth + 0.5f.lit, 0f.lit, 1f.lit))
        colorOutput(vec4(color.rgb * tint.xyz, color.a * tint.w * coverage))
    }
}

/** The narrowest edge, in field units a pixel, so a field that does not change across a pixel stays finite. */
private const val MIN_EDGE_WIDTH = 1e-4f
