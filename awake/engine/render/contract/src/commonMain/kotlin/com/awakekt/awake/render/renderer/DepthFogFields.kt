/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * The full-screen depth-fog block, in the order the shader reads it.
 *
 * [CameraEye] and [InverseViewProjection] are what turn a sampled depth back into a world
 * position: the fog distance is that position's distance from the eye, the same distance
 * `lit_shadow`'s per-fragment fog measures. The difference is where the position comes from --
 * an interpolated vertex there, the scene-depth target here -- which is why this fogs content
 * whose shader knows nothing about fog.
 */
object DepthFogFields {
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** RGB is the fog colour; A is its density per world unit -- `lit_shadow`'s own packing. */
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)
}

/** [DepthFogFields] as the block `AslDepthFogShader` derives its uniform struct from. */
val DepthFogUniformLayout = UniformLayout(
    DepthFogFields.InverseViewProjection,
    DepthFogFields.CameraEye,
    DepthFogFields.FogColor,
)
