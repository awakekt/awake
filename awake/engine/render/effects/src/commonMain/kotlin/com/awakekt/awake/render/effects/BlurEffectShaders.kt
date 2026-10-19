/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.fwidth
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexAttribute
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/**
 * Vertex format for dual-filter downsample/upsample passes (stride = 10 floats):
 * - position (vec2)
 * - clampRect (vec4: minX, minY, maxX, maxY)
 * - blurPass (vec4: offset, isUpsample, unused, unused)
 */
val BlurPassVertexFormat: VertexFormat = VertexFormat(
    listOf(
        VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
        VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec4, location = 1),
        VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 2),
    ),
)

/**
 * Vertex format for frosted glass composite passes (stride = 16 floats):
 * - pos (vec2)
 * - localPos (vec2)
 * - halfSize (vec2)
 * - radius (float)
 * - smoothing (float)
 * - tint (vec4)
 * - glass (vec4: coverage, saturation, noise, levelScale)
 */
val BlurCompositeVertexFormat: VertexFormat = VertexFormat(
    listOf(
        VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
        VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 1),
        VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec2, location = 2),
        VertexAttribute(VertexSemantic.Custom, GpuDataShape.Float, location = 3),
        VertexAttribute(VertexSemantic.Custom, GpuDataShape.Float, location = 4),
        VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 5),
        VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 6),
    ),
)

/**
 * Shaders and specifications for dual-filter (Kawase) blur passes and composite.
 */
object BlurEffectShaders {
    const val BLUR_PASS_SHADER_ID: String = "image_effect_blur_pass"
    const val BLUR_COMPOSITE_SHADER_ID: String = "image_effect_blur_composite"

    val ScreenToNdcField: UniformField = UniformField("screenToNdc", GpuDataShape.Vec4)
    val CompositeUniformLayout: UniformLayout = UniformLayout(ScreenToNdcField)

    /**
     * ASL shader for dual-filter downsampling and upsampling.
     */
    val BlurPassShader: AslShaderDefinition = shader(BLUR_PASS_SHADER_ID) {
        val source by texture2d(group = 0, binding = 0)
        val sourceSampler by sampler(group = 0, binding = 1)

        val out = varyings("VertexOutput")
        val clampRect by out.varying(GpuDataShape.Vec4, location = 0)
        val blurPass by out.varying(GpuDataShape.Vec4, location = 1)

        vertex {
            val inCorner by input(GpuDataShape.Vec2, location = 0)
            val inClamp by input(GpuDataShape.Vec4, location = 1)
            val inPass by input(GpuDataShape.Vec4, location = 2)
            out.position set vec4(inCorner, 0f.lit, 1f.lit)
            clampRect set inClamp
            blurPass set inPass
        }

        fragment {
            val texel = let("texel", position().xy)
            val sourceSizeU = let("sourceSizeU", textureDimensions(source))
            val sourceSize = let("sourceSize", vec2(toF32(sourceSizeU.x), toF32(sourceSizeU.y)))
            val upsample = let("upsample", blurPass.y gt 0.5f.lit)
            val offset = let("offset", blurPass.x)
            val centre = let("centre", select(texel * 2f.lit, texel * 0.5f.lit, upsample))
            val low = let("low", clampRect.xy + 0.5f.lit)
            val high = let("high", clampRect.zw - 0.5f.lit)
            val centreWeight = let("centreWeight", select(0.5f.lit, 0f.lit, upsample))
            val diagonalWeight = let("diagonalWeight", select(0.125f.lit, 2f.lit / 12f.lit, upsample))
            val axisWeight = let("axisWeight", select(0f.lit, 1f.lit / 12f.lit, upsample))
            val sum = variable("sum", vec4(0f.lit))

            fun tap(dx: AslExpr, dy: AslExpr, weight: AslExpr) {
                val at = clamp(centre + vec2(dx, dy), low, high) / sourceSize
                assign(sum, sum + textureSampleLevel(source, sourceSampler, at, 0f.lit) * weight)
            }

            val wide = let("wide", offset * 2f.lit)
            tap(0f.lit, 0f.lit, centreWeight)
            tap(-offset, -offset, diagonalWeight)
            tap(offset, -offset, diagonalWeight)
            tap(-offset, offset, diagonalWeight)
            tap(offset, offset, diagonalWeight)
            tap(wide, 0f.lit, axisWeight)
            tap(-wide, 0f.lit, axisWeight)
            tap(0f.lit, wide, axisWeight)
            tap(0f.lit, -wide, axisWeight)
            colorOutput(sum)
        }
    }

    /**
     * ASL shader for frosted glass composite.
     */
    val BlurCompositeShader: AslShaderDefinition = shader(BLUR_COMPOSITE_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val screenToNdc by uniforms.field(GpuDataShape.Vec4)
        val blurred by texture2d(group = 0, binding = 1)
        val blurredSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val localPos by out.varying(GpuDataShape.Vec2, location = 0)
        val halfSize by out.varying(GpuDataShape.Vec2, location = 1)
        val radius by out.varying(GpuDataShape.Float, location = 2)
        val smoothing by out.varying(GpuDataShape.Float, location = 3)
        val tint by out.varying(GpuDataShape.Vec4, location = 4)
        val glass by out.varying(GpuDataShape.Vec4, location = 5)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inLocalPos by input(GpuDataShape.Vec2, location = 1)
            val inHalfSize by input(GpuDataShape.Vec2, location = 2)
            val inRadius by input(GpuDataShape.Float, location = 3)
            val inSmoothing by input(GpuDataShape.Float, location = 4)
            val inTint by input(GpuDataShape.Vec4, location = 5)
            val inGlass by input(GpuDataShape.Vec4, location = 6)

            out.position set vec4(inPos * screenToNdc.xy + screenToNdc.zw, 0f.lit, 1f.lit)
            localPos set inLocalPos
            halfSize set inHalfSize
            radius set inRadius
            smoothing set inSmoothing
            tint set inTint
            glass set inGlass
        }

        fragment {
            val q = let("q", abs(localPos) - halfSize + radius)
            val pExp = let("pExp", 2f.lit + 4f.lit * smoothing)
            val roundedDistance = let("roundedDistance", pow(pow(q.x, pExp) + pow(q.y, pExp), 1f.lit / pExp) - radius)
            val boxDistance = let("boxDistance", length(max(q, vec2(0f.lit, 0f.lit))) + min(max(q.x, q.y), 0f.lit) - radius)
            val distance = variable("distance", boxDistance)
            iff((smoothing gt 0f.lit) and (q.x gt 0f.lit) and (q.y gt 0f.lit)) { assign(distance, roundedDistance) }
            val aa = let("aa", max(fwidth(distance), 0.5f.lit))
            val coverage = let("coverage", (1f.lit - smoothstep(-aa, aa, distance)) * glass.x)

            val pixel = let("pixel", position().xy)
            val levelSizeU = let("levelSizeU", textureDimensions(blurred))
            val levelSize = let("levelSize", vec2(toF32(levelSizeU.x), toF32(levelSizeU.y)))
            val backdrop = let("backdrop", textureSampleLevel(blurred, blurredSampler, pixel * glass.w / levelSize, 0f.lit).rgb)
            val luma = let("luma", dot(backdrop, vec3(0.2126f.lit, 0.7152f.lit, 0.0722f.lit)))
            val saturated = let("saturated", mix(vec3(luma), backdrop, glass.y))
            val tinted = let("tinted", mix(saturated, tint.rgb, tint.a))
            val grain = let("grain", fract(sin(dot(pixel, vec2(12.9898f.lit, 78.233f.lit))) * 43758.547f.lit) - 0.5f.lit)
            val rgb = let("rgb", clamp(tinted + vec3(grain * glass.z * 0.1f.lit), vec3(0f.lit), vec3(1f.lit)))
            colorOutput(vec4(rgb, coverage))
        }
    }

    val BlurPassSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = BLUR_PASS_SHADER_ID,
            wgslSource = BlurPassShader.emitWgsl(),
            groupBindings = BlurPassShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = BlurPassVertexFormat,
            uniformLayout = null,
        )
    }

    val BlurCompositeSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = BLUR_COMPOSITE_SHADER_ID,
            wgslSource = BlurCompositeShader.emitWgsl(),
            groupBindings = BlurCompositeShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = BlurCompositeVertexFormat,
            uniformLayout = CompositeUniformLayout,
        )
    }
}
