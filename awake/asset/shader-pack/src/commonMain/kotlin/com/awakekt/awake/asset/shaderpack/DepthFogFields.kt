/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Uniform field declarations for fullscreen depth fog post-processing pass. */
object DepthFogFields {
    /** Inverse view-projection matrix reconstructing world positions from screen depth. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** World-space camera eye position. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec3)

    /** Fog color and maximum opacity vector. */
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)
}

/** Uniform layout specification for depth fog post-processing. */
val DepthFogUniformLayout = UniformLayout(
    DepthFogFields.InverseViewProjection,
    DepthFogFields.CameraEye,
    DepthFogFields.FogColor,
)
