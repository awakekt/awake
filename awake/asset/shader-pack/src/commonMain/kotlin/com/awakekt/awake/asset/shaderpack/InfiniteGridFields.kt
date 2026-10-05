/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Uniform field declarations for infinite background reference grid rendering. */
object InfiniteGridFields {
    /** Grid sizing parameters: `(gridScale, lineThickness, fadeDistance, padding)`. */
    val GridParams = UniformField("gridParams", GpuDataShape.Vec4)

    /** Base grid line color and opacity. */
    val GridColor = UniformField("gridColor", GpuDataShape.Vec4)

    /** Highlight color for the X axis (Z = 0 line). */
    val AxisColorX = UniformField("axisColorX", GpuDataShape.Vec4)

    /** Highlight color for the Z axis (X = 0 line). */
    val AxisColorZ = UniformField("axisColorZ", GpuDataShape.Vec4)

    /** World-space camera position for distance fading. */
    val CameraPos = UniformField("cameraPos", GpuDataShape.Vec4)
}

/** Uniform layout specification for infinite ground grid rendering. */
val InfiniteGridUniformLayout = UniformLayout(
    InfiniteGridFields.GridParams,
    InfiniteGridFields.GridColor,
    InfiniteGridFields.AxisColorX,
    InfiniteGridFields.AxisColorZ,
    InfiniteGridFields.CameraPos,
)
