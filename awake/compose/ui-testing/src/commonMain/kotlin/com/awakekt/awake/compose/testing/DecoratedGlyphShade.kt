/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Guards the divisions by a range a font without an effect field leaves at 0, as the shader's `MIN_RANGE`. */
private const val MIN_RANGE = 1e-3f

/** The shader's `REACH_SLACK`. */
private const val REACH_SLACK = 1e-3f

/** Guards un-premultiplying a pixel nothing covers, as the shader's `MIN_ALPHA`. */
private const val MIN_ALPHA = 1e-6f

/**
 * The CPU copy of `decoratedGlyph` in `AslDecoratedGlyphShader` (#1061): one decorated glyph's straight-alpha colour at
 * a pixel centre, fill over outline over shadow, sampled from [font]'s atlas through [sample] (bilinear, clamp to
 * edge, as the GPU's sampler). Built once a glyph; [shade] then runs once a pixel and allocates nothing.
 */
internal class DecoratedGlyphShade(
    private val glyph: UiDrawPrimitive.Glyph,
    private val decoration: GlyphDecoration,
    font: UiFont,
    private val sample: (u: Float, v: Float, channel: Int) -> Float,
) {
    private val halfTexelU = 0.5f / font.atlasWidth
    private val halfTexelV = 0.5f / font.atlasHeight
    private val uvPerPxU = (glyph.u1 - glyph.u0) / glyph.w
    private val uvPerPxV = (glyph.v1 - glyph.v0) / glyph.h
    private val fieldPerPxU = (decoration.fieldU1 - decoration.fieldU0) / decoration.fieldW
    private val fieldPerPxV = (decoration.fieldV1 - decoration.fieldV0) / decoration.fieldH
    private val screenPxRange = distanceFieldScreenPxRange(
        font.distanceFieldRangePx,
        font.atlasWidth,
        font.atlasHeight,
        glyph.u1 - glyph.u0,
        glyph.v1 - glyph.v0,
        glyph.w,
        glyph.h,
    )
    private val fieldPxRange = max(
        0.5f * (
            font.effectFieldRangePx / font.atlasWidth / abs(fieldPerPxU) +
                font.effectFieldRangePx / font.atlasHeight / abs(fieldPerPxV)
            ),
        MIN_RANGE,
    )
    private val toField = font.atlasWidth / max(font.effectFieldRangePx, MIN_RANGE)
    private val outlineWidth = if (decoration.outlineColor.isTransparent()) 0f else decoration.outlineWidth * fieldPerPxU * toField
    private val softness = max(decoration.shadowBlur * fieldPerPxU * toField, 0.5f / fieldPxRange)
    private val shadowU = decoration.shadowOffsetX * fieldPerPxU
    private val shadowV = decoration.shadowOffsetY * fieldPerPxV
    private val rule = TextCoverageRule(glyph.color)

    /** The colour at pixel centre ([x], [y]) into [out] as r, g, b, a in 0..1, straight alpha. */
    fun shade(x: Float, y: Float, out: FloatArray) {
        val rawU = glyph.u0 + (x - glyph.x) * uvPerPxU
        val rawV = glyph.v0 + (y - glyph.y) * uvPerPxV
        val u = clampTo(rawU, glyph.u0, glyph.u1, halfTexelU)
        val v = clampTo(rawV, glyph.v0, glyph.v1, halfTexelV)
        val inRect = rawU in min(glyph.u0, glyph.u1)..max(glyph.u0, glyph.u1) && rawV in min(glyph.v0, glyph.v1)..max(glyph.v0, glyph.v1)
        val outlinePx = outlineWidth * fieldPxRange
        val fineRing = if (inRect && outlinePx <= 0.5f * screenPxRange - 0.5f + REACH_SLACK) {
            (screenPxRange * (sample(u, v, ALPHA) - 0.5f) + outlinePx + 0.5f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val fieldU = decoration.fieldU0 + (x - glyph.x - decoration.fieldOffsetX) * fieldPerPxU
        val fieldV = decoration.fieldV0 + (y - glyph.y - decoration.fieldOffsetY) * fieldPerPxV
        val field = fieldAt(fieldU, fieldV)
        // Across the last half texel of the rect, where the atlas sample is clamped, the fill hands over to the field.
        val handOver = max(pastFill(rawU, glyph.u0, glyph.u1, halfTexelU), pastFill(rawV, glyph.v0, glyph.v1, halfTexelV))
        val atlasCoverage = distanceFieldCoverage(sample(u, v, 0), sample(u, v, 1), sample(u, v, 2), screenPxRange)
        val fieldCoverage = (fieldPxRange * field + 0.5f).coerceIn(0f, 1f)
        val coverage = atlasCoverage + (fieldCoverage - atlasCoverage) * handOver
        val ring = max((fieldPxRange * (field + outlineWidth) + 0.5f).coerceIn(0f, 1f), fineRing)
        val outline = if (outlineWidth > 0f) max(ring, coverage) else 0f
        val shadow = smoothstep(-softness, softness, fieldAt(fieldU - shadowU, fieldV - shadowV))

        val shadowColor = decoration.shadowColor
        val outlineColor = decoration.outlineColor
        val shadowAlpha = shadowColor.a * shadow
        val outlineAlpha = outlineColor.a * outline
        val fillAlpha = glyph.color.a * rule.alpha(coverage)
        val underAlpha = outlineAlpha + shadowAlpha * (1f - outlineAlpha)
        val alpha = fillAlpha + underAlpha * (1f - fillAlpha)
        val shadowShare = shadowAlpha * (1f - outlineAlpha)
        val divisor = max(alpha, MIN_ALPHA)
        out[0] = (glyph.color.r * fillAlpha + (outlineColor.r * outlineAlpha + shadowColor.r * shadowShare) * (1f - fillAlpha)) / divisor
        out[1] = (glyph.color.g * fillAlpha + (outlineColor.g * outlineAlpha + shadowColor.g * shadowShare) * (1f - fillAlpha)) / divisor
        out[2] = (glyph.color.b * fillAlpha + (outlineColor.b * outlineAlpha + shadowColor.b * shadowShare) * (1f - fillAlpha)) / divisor
        out[3] = alpha
    }

    /** How far [value] lies past [a]..[b] inset by [inset], in units of [inset], held to 0..1. */
    private fun pastFill(value: Float, a: Float, b: Float, inset: Float): Float =
        (max(min(a, b) + inset - value, value - (max(a, b) - inset)) / inset).coerceIn(0f, 1f)

    /** The effect field less its 0.5 edge at ([u], [v]), clamped half a texel inside the glyph's tile. */
    private fun fieldAt(u: Float, v: Float): Float =
        sample(
            clampTo(u, decoration.fieldU0, decoration.fieldU1, halfTexelU),
            clampTo(v, decoration.fieldV0, decoration.fieldV1, halfTexelV),
            ALPHA,
        ) - 0.5f

    private fun clampTo(value: Float, a: Float, b: Float, inset: Float): Float =
        value.coerceIn(min(a, b) + inset, max(a, b) - inset)

    private fun smoothstep(low: Float, high: Float, x: Float): Float {
        val t = ((x - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        const val ALPHA = 3
    }
}
