/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fwidth
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * A glyph with its outline and shadow in one draw (#1061), through `VertexFormats2D.DecoratedGlyph`: the fill from the
 * atlas's distance field as [UiGlyphShader] draws it, the outline and the shadow from the font's effect field, whose
 * tiles share the atlas page. Group 0 is [UiGlyphShader]'s: the UI uniforms, whose `fontInfo.w` carries the effect
 * field's range, the atlas and its sampler.
 */
val UiDecoratedGlyphShader: AslShaderDefinition = shader("ui_decorated_glyph") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val screenToNdc by uniforms.field(GpuDataShape.Vec4)
    val fontInfo by uniforms.field(GpuDataShape.Vec4)

    val fontAtlas by texture2d(group = 0, binding = 1)
    val fontSampler by sampler(group = 0, binding = 2)

    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    val uvRect by out.varying(GpuDataShape.Vec4, location = 1)
    val fieldUv by out.varying(GpuDataShape.Vec2, location = 2)
    val fieldRect by out.varying(GpuDataShape.Vec4, location = 3)
    val color by out.varying(GpuDataShape.Vec4, location = 4)
    val outlineColor by out.varying(GpuDataShape.Vec4, location = 5)
    val shadowColor by out.varying(GpuDataShape.Vec4, location = 6)
    val effect by out.varying(GpuDataShape.Vec4, location = 7)

    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inUv by input(GpuDataShape.Vec2, location = 1)
        val inUvRect by input(GpuDataShape.Vec4, location = 2)
        val inFieldUv by input(GpuDataShape.Vec2, location = 3)
        val inFieldRect by input(GpuDataShape.Vec4, location = 4)
        val inColor by input(GpuDataShape.Vec4, location = 5)
        val inOutlineColor by input(GpuDataShape.Vec4, location = 6)
        val inShadowColor by input(GpuDataShape.Vec4, location = 7)
        val inEffect by input(GpuDataShape.Vec4, location = 8)
        val inTransform by input(GpuDataShape.Vec4, location = 9)

        val scale = inTransform.xy
        val pivot = inTransform.zw
        val scaledPos = pivot + (inPos - pivot) * scale
        out.position set vec4(scaledPos * screenToNdc.xy + screenToNdc.zw, 0f.lit, 1f.lit)
        uv set inUv
        uvRect set inUvRect
        fieldUv set inFieldUv
        fieldRect set inFieldRect
        color set inColor
        outlineColor set inOutlineColor
        shadowColor set inShadowColor
        effect set inEffect
    }

    fragment {
        val decorated = decoratedGlyph(
            DecoratedGlyphInputs(fontAtlas, fontSampler, fontInfo, uv, uvRect, fieldUv, fieldRect, color, outlineColor, shadowColor, effect),
        )
        colorOutput(decorated)
    }
}

/**
 * What [decoratedGlyph] reads: the atlas and its sampler, the font's `fontInfo` (y the atlas field's range, w the
 * effect field's), and one fragment's interpolated glyph. [uv] and [fieldUv] run past [uvRect] and [fieldRect] over a
 * quad grown by the decoration's reach; [effect] is the outline width, the shadow blur and the shadow offset, all in
 * field uv.
 */
internal class DecoratedGlyphInputs(
    val atlas: AslExpr,
    val sampler: AslExpr,
    val fontInfo: AslExpr,
    val uv: AslExpr,
    val uvRect: AslExpr,
    val fieldUv: AslExpr,
    val fieldRect: AslExpr,
    val color: AslExpr,
    val outlineColor: AslExpr,
    val shadowColor: AslExpr,
    val effect: AslExpr,
)

/**
 * One decorated glyph fragment, straight alpha: shadow, then outline over it, then fill over both. The reusable core
 * of [UiDecoratedGlyphShader], so a scene text pass (#1062) draws the same decoration from the same inputs.
 *
 * - **Fill** is [UiGlyphShader]'s distance-field coverage shaped by [textCoverageRule], so a decoration never changes
 *   the weight of the text it surrounds.
 * - **Outline** covers where the effect field reads the outline's width or less outside the edge, with a one-pixel
 *   ramp, and everywhere the fill covers, so the two meet with no seam. Where the outline fits inside the atlas field's
 *   own reach, at large sizes, that finer field draws it too.
 * - **Shadow** is the effect field's edge moved by the offset, ramped by smoothstep across the blur either side of it,
 *   or across a pixel for a hard shadow.
 *
 * The fill covers nothing outside its own rect, and every sample is clamped to its tile, half a texel in, and every derivative is taken before any of them, in uniform
 * control flow. `UiRasterizer`'s decorated glyph is the CPU copy of this block.
 */
internal fun AslBlockBuilder.decoratedGlyph(inputs: DecoratedGlyphInputs): AslExpr = with(inputs) {
    val atlasSizeU = let("atlasSizeU", textureDimensions(atlas))
    val atlasSize = let("atlasSize", vec2(toF32(atlasSizeU.x), toF32(atlasSizeU.y)))
    val halfTexel = let("halfTexel", vec2(0.5f.lit, 0.5f.lit) / atlasSize)
    val screenTexSize = let("screenTexSize", vec2(1f.lit, 1f.lit) / fwidth(uv))
    val fieldScreenTexSize = let("fieldScreenTexSize", vec2(1f.lit, 1f.lit) / fwidth(fieldUv))

    val fill = let("fill", textureSample(atlas, sampler, clamp(uv, uvRect.xy + halfTexel, uvRect.zw - halfTexel)))
    val unitRange = let("unitRange", vec2(fontInfo.y) / atlasSize)
    val screenPxRange = let("screenPxRange", max(0.5f.lit * dot(unitRange, screenTexSize), 1f.lit))
    val signedDistance = let("signedDistance", max(min(fill.r, fill.g), min(max(fill.r, fill.g), fill.b)))
    // Outside its rect the quad has grown past the glyph: a clamped sample there repeats the rect's edge texels,
    // which still read a little coverage, and would smear it across the decoration.
    val inRect = let(
        "inRect",
        (uv.x ge uvRect.x) and (uv.x le uvRect.z) and (uv.y ge uvRect.y) and (uv.y le uvRect.w),
    )
    val coverage = let(
        "coverage",
        select(0f.lit, clamp(screenPxRange * (signedDistance - 0.5f.lit) + 0.5f.lit, 0f.lit, 1f.lit), inRect),
    )
    val ruled = textCoverageRule(coverage, color)

    // Screen pixels per unit of field value, and field values per unit of field uv across.
    val fieldPxRange = let("fieldPxRange", max(0.5f.lit * dot(vec2(fontInfo.w) / atlasSize, fieldScreenTexSize), MIN_RANGE.lit))
    val toField = let("toField", atlasSize.x / max(fontInfo.w, MIN_RANGE.lit))
    val fieldLow = let("fieldLow", fieldRect.xy + halfTexel)
    val fieldHigh = let("fieldHigh", fieldRect.zw - halfTexel)
    val texel = let("texel", vec2(1f.lit, 1f.lit) / atlasSize)
    val field = let("field", fieldSample(this@decoratedGlyph, "fieldAt", clamp(fieldUv, fieldLow, fieldHigh), atlasSize, texel) - 0.5f.lit)
    val outlineWidth = let("outlineWidth", effect.x * toField)
    val coarseRing = let("coarseRing", clamp(fieldPxRange * (field + outlineWidth) + 0.5f.lit, 0f.lit, 1f.lit))
    // The atlas's own true distance, its alpha, is exact within its reach of the edge, half its screen range: where a
    // thin outline at a large size fits inside that, it draws the ring the coarse field blunts in a narrow notch.
    val outlinePx = let("outlinePx", outlineWidth * fieldPxRange)
    // A hair of slack, so a reach the CPU raster finds exactly equal is equal here too, through fwidth's rounding.
    val fineReaches = let("fineReaches", inRect and (outlinePx le 0.5f.lit * screenPxRange - 0.5f.lit + REACH_SLACK.lit))
    val fineRing = let(
        "fineRing",
        select(0f.lit, clamp(screenPxRange * (fill.a - 0.5f.lit) + outlinePx + 0.5f.lit, 0f.lit, 1f.lit), fineReaches),
    )
    val ring = let("ring", max(coarseRing, fineRing))
    val outline = let("outline", select(0f.lit, max(ring, coverage), outlineWidth gt 0f.lit))
    val shadowField = let(
        "shadowField",
        fieldSample(this@decoratedGlyph, "shadowAt", clamp(fieldUv - effect.zw, fieldLow, fieldHigh), atlasSize, texel) - 0.5f.lit,
    )
    val softness = let("softness", max(effect.y * toField, 0.5f.lit / fieldPxRange))
    val shadow = let("shadow", smoothstep(-softness, softness, shadowField))

    val shadowAlpha = let("shadowAlpha", shadowColor.a * shadow)
    val outlineAlpha = let("outlineAlpha", outlineColor.a * outline)
    val fillAlpha = let("fillAlpha", color.a * ruled)
    val underAlpha = let("underAlpha", outlineAlpha + shadowAlpha * (1f.lit - outlineAlpha))
    val underRgb = let(
        "underRgb",
        outlineColor.rgb * outlineAlpha + shadowColor.rgb * (shadowAlpha * (1f.lit - outlineAlpha)),
    )
    val alpha = let("alpha", fillAlpha + underAlpha * (1f.lit - fillAlpha))
    val rgb = let("rgb", color.rgb * fillAlpha + underRgb * (1f.lit - fillAlpha))
    vec4(rgb / max(alpha, MIN_ALPHA.lit), alpha)
}

/**
 * The effect field's alpha at [uv], interpolated between the four nearest texel centres in float. A sampler filters an
 * 8-bit texture to 8 bits on some hardware, lavapipe among it, and one level of the field's 16-texel spread is a fifth
 * of a pixel at 48 px: filtered there, an outline's edge stepped in bands the CPU raster does not draw. Each tap lands
 * on a texel centre, which a linear sampler returns exactly.
 */
private fun DecoratedGlyphInputs.fieldSample(
    block: AslBlockBuilder,
    name: String,
    uv: AslExpr,
    atlasSize: AslExpr,
    texel: AslExpr,
): AslExpr = with(block) {
    val position = let("${name}Texel", uv * atlasSize - vec2(0.5f.lit, 0.5f.lit))
    val base = let("${name}Base", floor(position))
    val weight = let("${name}Weight", position - base)
    val corner = let("${name}Corner", (base + vec2(0.5f.lit, 0.5f.lit)) * texel)
    val topLeft = textureSample(atlas, sampler, corner).a
    val topRight = textureSample(atlas, sampler, corner + vec2(texel.x, 0f.lit)).a
    val bottomLeft = textureSample(atlas, sampler, corner + vec2(0f.lit, texel.y)).a
    val bottomRight = textureSample(atlas, sampler, corner + texel).a
    let(name, mix(mix(topLeft, topRight, weight.x), mix(bottomLeft, bottomRight, weight.x), weight.y))
}

/** Guards the divisions by a range a font without an effect field leaves at 0. */
private const val MIN_RANGE = 1e-3f

/** How far past the atlas field's reach an outline may be and still take its fine ring: the hair of haze it can add. */
internal const val REACH_SLACK = 1e-3f

/** Guards un-premultiplying a pixel nothing covers. */
private const val MIN_ALPHA = 1e-6f
