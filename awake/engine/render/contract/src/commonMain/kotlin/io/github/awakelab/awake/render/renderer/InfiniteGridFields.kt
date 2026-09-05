/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.geometry.GpuDataShape

/**
 * Uniform fields for the unprojected procedural infinite grid shader.
 */
object InfiniteGridFields {
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)
    /** x = primaryStep (e.g. 1.0), y = subStep (e.g. 0.1), z = fadeDistance (e.g. 100.0), w = unused */
    val GridParams = UniformField("gridParams", GpuDataShape.Vec4)
    val GridColor = UniformField("gridColor", GpuDataShape.Vec4)
    val SubGridColor = UniformField("subGridColor", GpuDataShape.Vec4)
    val AxisColorX = UniformField("axisColorX", GpuDataShape.Vec4)
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
