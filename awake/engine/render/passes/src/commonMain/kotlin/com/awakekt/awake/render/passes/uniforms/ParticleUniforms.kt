/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

object ParticleFields {
    val ViewProjection = UniformField("viewProjection", GpuDataShape.Mat4)
    val CameraRight = UniformField("cameraRight", GpuDataShape.Vec4)
    val CameraUp = UniformField("cameraUp", GpuDataShape.Vec4)
}

object ParticleExtraFields {
    val CameraRight = UniformField("cameraRight", GpuDataShape.Vec4)
    val CameraUp = UniformField("cameraUp", GpuDataShape.Vec4)
}

val ParticleUniformLayout = UniformLayout(
    ParticleFields.ViewProjection,
    ParticleFields.CameraRight,
    ParticleFields.CameraUp,
)

val ParticleExtraUniformLayout = UniformLayout(
    ParticleExtraFields.CameraRight,
    ParticleExtraFields.CameraUp,
)
