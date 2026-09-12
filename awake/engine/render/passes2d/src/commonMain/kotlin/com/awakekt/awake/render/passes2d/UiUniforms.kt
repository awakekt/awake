/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * The shared UI uniform ABI. Both backends keep the same eight-float block: the glyph shader's
 * `fontInfo` is a vec4 so its two live values and two padding lanes have an explicit declaration.
 * Quad and texture shaders only read [ScreenToNdc], but using the same buffer capacity keeps their
 * resize and bind-group paths identical.
 */
object UiUniformLayouts {
    val ScreenToNdc = UniformField("screenToNdc", GpuDataShape.Vec4)
    val FontInfo = UniformField("fontInfo", GpuDataShape.Vec4)
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
