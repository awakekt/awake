// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.renderer

import io.github.ronjunevaldoz.awake.core.geometry.GpuDataShape

/**
 * What a `ParticleEmitter` contributes to `particle.wgsl`'s `Uniforms` -- everything after the
 * view-projection the renderer prepends.
 *
 * Billboards need a camera basis rather than a light: the vertex shader spins each quad to face
 * the camera from [ParticleExtraUniformLayout]'s right/up vectors, where every other instanced
 * format carries the scene light instead.
 *
 * `frameInfo.x` is the sprite atlas's frame count. `frameInfo.y` was the emitter-wide current
 * frame and is now a reserved pad -- frame cycling is per-particle, through the instance-rate
 * `inFrame` attribute, so particles sharing an emitter desync instead of stepping in lockstep.
 */
val ParticleExtraUniformLayout = UniformLayout(
    UniformField("cameraRight", GpuDataShape.Vec4),
    UniformField("cameraUp", GpuDataShape.Vec4),
    UniformField("frameInfo", GpuDataShape.Vec4),
)

/** [ParticleExtraUniformLayout] with the renderer's view-projection in front -- the whole block
 * `particle.wgsl` reads. */
val ParticleUniformLayout = UniformLayout(
    UniformFields.Mvp,
    *ParticleExtraUniformLayout.fields,
)

/** The view-projection + scene-light block every NON-particle instanced format reads
 * (`instanced.wgsl`, `skinned_instanced.wgsl`), and the primary lit pipeline's own uniform
 * block. Same three fields, one number. */
val InstancedUniformLayout = UniformLayout(
    UniformFields.Mvp,
    UniformFields.LightDirection,
    UniformFields.LightColor,
)
