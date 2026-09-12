/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

object InfiniteGridFields {
    val GridParams = UniformField("gridParams", GpuDataShape.Vec4)
    val GridColor = UniformField("gridColor", GpuDataShape.Vec4)
    val AxisColorX = UniformField("axisColorX", GpuDataShape.Vec4)
    val AxisColorZ = UniformField("axisColorZ", GpuDataShape.Vec4)
    val CameraPos = UniformField("cameraPos", GpuDataShape.Vec4)
}

val InfiniteGridUniformLayout = UniformLayout(
    InfiniteGridFields.GridParams,
    InfiniteGridFields.GridColor,
    InfiniteGridFields.AxisColorX,
    InfiniteGridFields.AxisColorZ,
    InfiniteGridFields.CameraPos,
)
