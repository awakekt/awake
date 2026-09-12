/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

object DepthFogFields {
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec3)
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)
}

val DepthFogUniformLayout = UniformLayout(
    DepthFogFields.InverseViewProjection,
    DepthFogFields.CameraEye,
    DepthFogFields.FogColor,
)
