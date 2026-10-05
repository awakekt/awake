/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout

object ParticleExtraFields {
    val CameraRight = UniformField("cameraRight", GpuDataShape.Vec4)
    val CameraUp = UniformField("cameraUp", GpuDataShape.Vec4)
    val FrameInfo = UniformField("frameInfo", GpuDataShape.Vec4)
}

val ParticleUniformLayout = UniformLayout(
    UniformFields.Mvp,
    ParticleExtraFields.CameraRight,
    ParticleExtraFields.CameraUp,
    ParticleExtraFields.FrameInfo,
)

val ParticleExtraUniformLayout = UniformLayout(
    ParticleExtraFields.CameraRight,
    ParticleExtraFields.CameraUp,
    ParticleExtraFields.FrameInfo,
)

val InstancedUniformLayout = UniformLayout(
    UniformFields.Mvp,
    UniformFields.LightDirection,
    UniformFields.LightColor,
    UniformFields.Exposure,
)

/**
 * Writes one particle's instance matrix as the particle shader reads it: column 3 is its world
 * centre, column 0's length its size, and column 1 a world-space stretch vector that elongates the
 * quad along it. A plain particle's stretch is zero; anything else there, such as the size on the
 * diagonal [Mat4.setTranslationScale] leaves, stretches and mirrors it. Column 2's x is [rotation], the
 * quad's turn in its own plane in radians, counter-clockwise as the viewer sees it; a stretched
 * particle ignores it.
 */
fun Mat4.setParticleInstance(
    x: Float,
    y: Float,
    z: Float,
    size: Float,
    stretchX: Float = 0f,
    stretchY: Float = 0f,
    stretchZ: Float = 0f,
    rotation: Float = 0f,
): Mat4 {
    setTranslationScale(x, y, z, size)
    m01 = stretchX
    m11 = stretchY
    m21 = stretchZ
    m02 = rotation
    return this
}
