/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * Uniform fields for the unprojected procedural infinite grid shader.
 */
object InfiniteGridFields {
    /** Inverse view-projection matrix used to unproject NDC coordinates to world space. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** Camera eye position in world space. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** x = primaryStep (e.g. 1.0), y = subStep (e.g. 0.1), z = fadeDistance (e.g. 100.0), w = unused */
    val GridParams = UniformField("gridParams", GpuDataShape.Vec4)

    /** Primary grid line color and alpha. */
    val GridColor = UniformField("gridColor", GpuDataShape.Vec4)

    /** Secondary finer grid subdivision line color and alpha. */
    val SubGridColor = UniformField("subGridColor", GpuDataShape.Vec4)

    /** Major X-axis coordinate highlight color. */
    val AxisColorX = UniformField("axisColorX", GpuDataShape.Vec4)

    /** Major Z-axis coordinate highlight color. */
    val AxisColorZ = UniformField("axisColorZ", GpuDataShape.Vec4)
}

/** [InfiniteGridFields] as the layout [AslInfiniteGridShader] derives its uniform block from. */
val InfiniteGridUniformLayout = UniformLayout(
    InfiniteGridFields.InverseViewProjection,
    InfiniteGridFields.CameraEye,
    InfiniteGridFields.GridParams,
    InfiniteGridFields.GridColor,
    InfiniteGridFields.SubGridColor,
    InfiniteGridFields.AxisColorX,
    InfiniteGridFields.AxisColorZ,
)
