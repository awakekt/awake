/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.render.font.UiFontSamplingInfo
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * The shared UI uniform ABI. Both backends keep the same eight-float block: the glyph shader's
 * `fontInfo` is a vec4 so each of its four lanes has an explicit declaration.
 * Quad and texture shaders only read [ScreenToNdc], but using the same buffer capacity keeps their
 * resize and bind-group paths identical.
 */
object UiUniformLayouts {
    /** Screen-to-NDC orthographic projection transformation parameters. */
    val ScreenToNdc = UniformField("screenToNdc", GpuDataShape.Vec4)

    /** Font atlas sampling parameters (atlas dimensions, gamma, distance range). */
    val FontInfo = UniformField("fontInfo", GpuDataShape.Vec4)

    /** The complete uniform buffer layout combining [ScreenToNdc] and [FontInfo]. */
    val Buffer = UniformLayout(ScreenToNdc, FontInfo)
}

/** Padded scalar block used by the target-composite shader's `u32` mode field. */
val UiTargetCompositeUniformLayout = UniformLayout(
    UniformField("mode", GpuDataShape.Vec4),
)

/** Packs the complete UI block from typed values; callers never repeat component offsets. */
fun uiUniformFloats(screenToNdc: Vec4, fontInfo: Vec4 = Vec4(0f, 0f, 0f, 0f)): FloatArray =
    UniformWriter(UiUniformLayouts.Buffer)
        .put(UiUniformLayouts.ScreenToNdc, screenToNdc.x, screenToNdc.y, screenToNdc.z, screenToNdc.w)
        .put(UiUniformLayouts.FontInfo, fontInfo.x, fontInfo.y, fontInfo.z, fontInfo.w)
        .build()

/**
 * The glyph shader's `fontInfo` for one font: x is 1 for a distance-field atlas, 0 for coverage alpha
 * and 2 for colour; y is the distance-field range in atlas texels; z is 1 when coverage alpha is exact
 * pixel area ([UiFontSamplingInfo.linearCoverage]), which the shader then shapes with the text coverage
 * rule, as it shapes every distance field's coverage; w is the effect field's range in atlas texels, which the
 * decorated glyph shader draws outlines and shadows from (#1061). The shader reads the atlas size from the texture
 * itself, so two fonts differ only in these lanes and share one pipeline.
 */
fun uiFontInfo(info: UiFontSamplingInfo): Vec4 =
    Vec4(
        when (info.mode) {
            UiFontSamplingMode.CoverageAlpha -> 0f
            UiFontSamplingMode.DistanceField -> 1f
            UiFontSamplingMode.ColorRgba -> 2f
        },
        info.distanceFieldRangePx,
        if (info.linearCoverage) 1f else 0f,
        info.effectFieldRangePx,
    )
