/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.fwidth
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.or
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * ASL definition for 2D UI Quad rendering with dynamic FMA screen-to-NDC transformation.
 */
val UiQuadShader: AslShaderDefinition = shader("ui_quad") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec4, location = 0)

    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inColor by input(GpuDataShape.Vec4, location = 1)
        val inTransform by input(GpuDataShape.Vec4, location = 2)

        val scale = inTransform.xy
        val pivot = inTransform.zw
        val scaledPos = pivot + (inPos - pivot) * scale
        val ndc = scaledPos * screenToNdc.xy + screenToNdc.zw
        out.position set vec4(ndc, 0f.lit, 1f.lit)
        color set inColor
    }

    fragment {
        colorOutput(color)
    }
}

/**
 * ASL definition for 2D UI Texture Quad rendering.
 */
val UiTextureShader: AslShaderDefinition = shader("ui_texture") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)

    val previewTexture by texture2d(group = 0, binding = 1)
    val previewSampler by sampler(group = 0, binding = 2)

    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    val color by out.varying(GpuDataShape.Vec4, location = 1)

    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inUv by input(GpuDataShape.Vec2, location = 1)
        val inColor by input(GpuDataShape.Vec4, location = 2)
        val inTransform by input(GpuDataShape.Vec4, location = 3)

        val scale = inTransform.xy
        val pivot = inTransform.zw
        val scaledPos = pivot + (inPos - pivot) * scale
        val ndc = scaledPos * screenToNdc.xy + screenToNdc.zw
        out.position set vec4(ndc, 0f.lit, 1f.lit)
        uv set inUv
        color set inColor
    }

    fragment {
        val sampled = let("sampled", textureSample(previewTexture, previewSampler, uv))
        // The vertex colour is the draw's tint and alpha: white leaves the texel as it is.
        val alpha = let("alpha", sampled.a * color.a)
        val rgb = let("rgb", sampled.rgb * color.rgb * color.a)
        colorOutput(vec4(rgb, alpha))
    }
}

/** ASL definition for the UI font atlas shader, including coverage and MTSDF paths. */
val UiGlyphShader: AslShaderDefinition = shader("ui_glyph") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)
    // Keep the font metadata in a vec4 so the shared UI UBO has an explicit 16-byte field.
    // x is the atlas kind, y the distance-field range and z the exact-coverage flag (`uiFontInfo`); w is padding.
    val fontInfo by uniforms.field(GpuDataShape.Vec4)

    val fontAtlas by texture2d(group = 0, binding = 1)
    val fontSampler by sampler(group = 0, binding = 2)

    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    val color by out.varying(GpuDataShape.Vec4, location = 1)

    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inUv by input(GpuDataShape.Vec2, location = 1)
        val inColor by input(GpuDataShape.Vec4, location = 2)
        val inTransform by input(GpuDataShape.Vec4, location = 3)

        val scale = inTransform.xy
        val pivot = inTransform.zw
        val scaledPos = pivot + (inPos - pivot) * scale
        val ndc = scaledPos * screenToNdc.xy + screenToNdc.zw
        out.position set vec4(ndc, 0f.lit, 1f.lit)
        uv set inUv
        color set inColor
    }

    fragment {
        val atlas = let("atlas", textureSample(fontAtlas, fontSampler, uv))
        val atlasSizeU = let("atlasSizeU", textureDimensions(fontAtlas))
        val atlasSize = let(
            "atlasSize",
            vec2(toF32(atlasSizeU.x), toF32(atlasSizeU.y)),
        )
        // Distance-field coverage: a one-pixel ramp across the field's 0.5 edge, which is the area of the
        // pixel the outline covers for a straight edge. Computed for every font, outside any branch, so the
        // derivative is taken in uniform control flow. `UiRasterizer.distanceFieldScreenPxRange` and
        // `distanceFieldCoverage` are the CPU copy of this block, checked against it by
        // DistanceFieldGlyphShaderTwinTest, which supplies `atlas`, `atlasSizeU` and `screenTexSize` by name:
        // rename them there too.
        val unitRange = let("unitRange", vec2(fontInfo.y) / atlasSize)
        val screenTexSize = let("screenTexSize", vec2(1f.lit, 1f.lit) / fwidth(uv))
        val screenPxRange = let("screenPxRange", max(0.5f.lit * dot(unitRange, screenTexSize), 1f.lit))
        val signedDistance = let(
            "signedDistance",
            max(min(atlas.r, atlas.g), min(max(atlas.r, atlas.g), atlas.b)),
        )
        val fieldCoverage = let(
            "fieldCoverage",
            clamp(screenPxRange * (signedDistance - 0.5f.lit) + 0.5f.lit, 0f.lit, 1f.lit),
        )
        val distanceField = let("distanceField", fontInfo.x gt 0.5f.lit and (fontInfo.x lt 1.5f.lit))
        val coverage = variable("coverage", atlas.a)
        iff(distanceField) {
            assign(coverage, fieldCoverage)
        }
        // The text coverage rule, one for every glyph whose coverage is the area of the pixel the outline
        // covers: a distance field's ramp above, and exact-size pixel glyphs (fontInfo.z = 1). It is Skia's
        // mask gamma with Chromium's Linux constants. The ground is not known here, so it is taken to be the
        // text's inverse, `1 - textLuma`; the coverage gets a contrast boost that fades out as that ground
        // darkens; and the two are mixed in light linearised with gamma 1.2. The alpha is what gives that
        // mix when the blend unit mixes in the target's own encoding. Mid-grey text, whose assumed ground is
        // itself, takes the boosted coverage. `UiRasterizer.textCoverageAlpha` is the CPU copy of this block,
        // held to it by TextCoverageRuleTwinTest; the rule and its constants are documented in the UI guide's
        // "Draw crisp text".
        val textLinear = let(
            "textLinear",
            dot(
                pow(
                    max(color.rgb, vec3(0f.lit, 0f.lit, 0f.lit)),
                    vec3(TEXT_GAMMA.lit, TEXT_GAMMA.lit, TEXT_GAMMA.lit),
                ),
                vec3(0.2126f.lit, 0.7152f.lit, 0.0722f.lit),
            ),
        )
        val textLuma = let("textLuma", pow(textLinear, 1f.lit / TEXT_GAMMA.lit))
        val groundLuma = let("groundLuma", 1f.lit - textLuma)
        val groundLinear = let("groundLinear", pow(groundLuma, TEXT_GAMMA.lit))
        val contrasted = let(
            "contrasted",
            coverage + (1f.lit - coverage) * TEXT_CONTRAST.lit * groundLinear * coverage,
        )
        val mixed = let(
            "mixed",
            pow(
                clamp(textLinear * contrasted + groundLinear * (1f.lit - contrasted), 0f.lit, 1f.lit),
                1f.lit / TEXT_GAMMA.lit,
            ),
        )
        // |textLuma - groundLuma| < TEXT_SAME_LUMA, with groundLuma = 1 - textLuma.
        val sameLuma = let(
            "sameLuma",
            (textLuma gt (0.5f - TEXT_SAME_LUMA / 2f).lit) and (textLuma lt (0.5f + TEXT_SAME_LUMA / 2f).lit),
        )
        val lumaSpan = let("lumaSpan", select(textLuma - groundLuma, 1f.lit, sameLuma))
        val ruled = let(
            "ruled",
            select(clamp((mixed - groundLuma) / lumaSpan, 0f.lit, 1f.lit), contrasted, sameLuma),
        )
        val glyphAlpha = variable("glyphAlpha", atlas.a)
        iff(distanceField or (fontInfo.x lt 0.5f.lit and (fontInfo.z gt 0.5f.lit))) {
            assign(glyphAlpha, ruled)
        }
        val outColor = variable("outColor", vec4(color.rgb, color.a * glyphAlpha))
        iff(fontInfo.x gt 1.5f.lit) {
            assign(outColor, vec4(atlas.rgb, atlas.a * color.a))
        }
        colorOutput(outColor)
    }
}

/** The gamma the text coverage rule mixes text and ground in: Skia's text gamma on Linux, as Chromium sets it. */
private const val TEXT_GAMMA = 1.2f

/** The text coverage rule's contrast boost over a white ground: Skia's text contrast on Linux, as Chromium sets it. */
private const val TEXT_CONTRAST = 0.2f

/** How close the text's luma and its assumed ground's may be before the rule takes the boosted coverage as is. */
private const val TEXT_SAME_LUMA = 1f / 256f

/** ASL definition for the full-target alpha composite pass. */
val UiTargetCompositeShader: AslShaderDefinition = shader("ui_target_composite") {
    val composite = uniformBlock("CompositeUniforms", group = 0, binding = 4)
    val compositeMode by composite.fieldU32()
    val sourceTexture by texture2d(group = 0, binding = 0)
    val sourceSampler by sampler(group = 0, binding = 1)
    val destinationTexture by texture2d(group = 0, binding = 2)
    val destinationSampler by sampler(group = 0, binding = 3)

    vertex {
        returnPosition(vec4(fullScreenTriangleCorner(), 0f.lit, 1f.lit))
    }

    fragment {
        val position = position()
        val destinationSizeU = let("destinationSizeU", textureDimensions(destinationTexture))
        val destinationSize = let(
            "destinationSize",
            vec2(toF32(destinationSizeU.x), toF32(destinationSizeU.y)),
        )
        val uv = let("uv", position.xy / destinationSize)
        val source = let("source", textureSample(sourceTexture, sourceSampler, uv))
        val destination = let("destination", textureSample(destinationTexture, destinationSampler, uv))
        val sourceColour = let("sourceColour", select(vec3(0f.lit), source.rgb / source.a, source.a gt 0f.lit))
        val destinationColour = let(
            "destinationColour",
            select(vec3(0f.lit), destination.rgb / destination.a, destination.a gt 0f.lit),
        )
        val alpha = let("alpha", source.a + destination.a * (1f.lit - source.a))
        val blend = variable("blend", sourceColour)
        iff(compositeMode eq 1u.lit) {
            assign(blend, sourceColour + destinationColour - sourceColour * destinationColour)
        }
        iff(compositeMode eq 2u.lit) {
            val low = let("low", 2f.lit * sourceColour * destinationColour)
            val high = let(
                "high",
                1f.lit - 2f.lit * (vec3(1f.lit) - sourceColour) * (vec3(1f.lit) - destinationColour),
            )
            assign(blend, mix(low, high, step(vec3(0.5f.lit), destinationColour)))
        }
        val rgb = let(
            "rgb",
            (1f.lit - source.a) * destination.rgb +
                (1f.lit - destination.a) * source.rgb +
                source.a * destination.a * blend,
        )
        colorOutput(vec4(rgb, alpha))
    }
}

/** ASL definition for the rounded UI quad, including scale-stable derivative AA. */
val UiRoundedQuadShader: AslShaderDefinition = shader("ui_rounded_quad") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)

    val out = varyings("VertexOutput")
    val localPos by out.varying(GpuDataShape.Vec2, location = 0)
    val halfSize by out.varying(GpuDataShape.Vec2, location = 1)
    val radius by out.varying(GpuDataShape.Float, location = 2)
    val smoothing by out.varying(GpuDataShape.Float, location = 3)
    val color by out.varying(GpuDataShape.Vec4, location = 4)

    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inLocalPos by input(GpuDataShape.Vec2, location = 1)
        val inHalfSize by input(GpuDataShape.Vec2, location = 2)
        val inRadius by input(GpuDataShape.Float, location = 3)
        val inSmoothing by input(GpuDataShape.Float, location = 4)
        val inColor by input(GpuDataShape.Vec4, location = 5)
        val inTransform by input(GpuDataShape.Vec4, location = 6)

        val scale = inTransform.xy
        val pivot = inTransform.zw
        val scaledPos = pivot + (inPos - pivot) * scale
        val ndc = scaledPos * screenToNdc.xy + screenToNdc.zw
        out.position set vec4(ndc, 0f.lit, 1f.lit)
        localPos set inLocalPos
        halfSize set inHalfSize
        radius set inRadius
        smoothing set inSmoothing
        color set inColor
    }

    fragment {
        val q = let("q", abs(localPos) - halfSize + radius)
        val pExp = let("pExp", 2f.lit + 4f.lit * smoothing)
        val roundedDistance = let(
            "roundedDistance",
            (pow(pow(q.x, pExp) + pow(q.y, pExp), 1f.lit / pExp) - radius),
        )
        val boxDistance = let(
            "boxDistance",
            length(max(q, vec2(0f.lit, 0f.lit))) + min(max(q.x, q.y), 0f.lit) - radius,
        )
        val distance = variable("distance", boxDistance)
        val curvedCorner = (smoothing gt 0f.lit) and (q.x gt 0f.lit) and (q.y gt 0f.lit)
        iff(curvedCorner) { assign(distance, roundedDistance) }
        val aa = let("aa", max(fwidth(distance), 0.5f.lit))
        val alpha = let("alpha", 1f.lit - smoothstep(-aa, aa, distance))
        colorOutput(vec4(color.rgb, color.a * alpha))
    }
}
