/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.fullScreenTriangleCorner
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.abs
import io.github.awakelab.awake.asset.shaderdsl.and
import io.github.awakelab.awake.asset.shaderdsl.clamp
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.r
import io.github.awakelab.awake.asset.shaderdsl.g
import io.github.awakelab.awake.asset.shaderdsl.b
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.min
import io.github.awakelab.awake.asset.shaderdsl.fwidth
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.pow
import io.github.awakelab.awake.asset.shaderdsl.smoothstep
import io.github.awakelab.awake.asset.shaderdsl.step
import io.github.awakelab.awake.asset.shaderdsl.F32
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.select
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.texture2d
import io.github.awakelab.awake.asset.shaderdsl.textureSample
import io.github.awakelab.awake.asset.shaderdsl.textureDimensions
import io.github.awakelab.awake.asset.shaderdsl.toF32
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec3
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.xy
import io.github.awakelab.awake.asset.shaderdsl.zw
import io.github.awakelab.awake.asset.shaderdsl.gt
import io.github.awakelab.awake.asset.shaderdsl.lt
import io.github.awakelab.awake.asset.shaderdsl.eq
import io.github.awakelab.awake.core.geometry.GpuDataShape

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
        val alpha = let("alpha", sampled.a * color.a)
        val rgb = let("rgb", sampled.rgb * color.a)
        colorOutput(vec4(rgb, alpha))
    }
}

/** ASL definition for the UI font atlas shader, including coverage and MTSDF paths. */
val UiGlyphShader: AslShaderDefinition = shader("ui_glyph") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)
    val fontInfo by uniforms.field(GpuDataShape.Vec2)

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
        val glyphAlpha = variable("glyphAlpha", atlas.a)
        iff(fontInfo.x lt 0.5f.lit) {
            assign(glyphAlpha, atlas.a)
        }
        val atlasSizeU = let("atlasSizeU", textureDimensions(fontAtlas))
        val atlasSize = let(
            "atlasSize",
            vec2(toF32(atlasSizeU.x), toF32(atlasSizeU.y)),
        )
        val unitRange = let("unitRange", vec2(fontInfo.y) / atlasSize)
        val screenTexSize = let("screenTexSize", vec2(1f.lit, 1f.lit) / fwidth(uv))
        val screenPxRange = let("screenPxRange", max(0.5f.lit * dot(unitRange, screenTexSize), 1f.lit))
        val signedDistance = let(
            "signedDistance",
            max(min(atlas.r, atlas.g), min(max(atlas.r, atlas.g), atlas.b)),
        )
        val coverage = let(
            "coverage",
            clamp(screenPxRange * (signedDistance - 0.5f.lit) + 0.5f.lit, 0f.lit, 1f.lit),
        )
        val correctedCoverage = let(
            "correctedCoverage",
            pow(coverage, 1f.lit / 1.45f.lit),
        )
        iff(fontInfo.x gt 0.5f.lit) {
            assign(glyphAlpha, correctedCoverage)
        }
        colorOutput(vec4(color.rgb, color.a * glyphAlpha))
    }
}

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
