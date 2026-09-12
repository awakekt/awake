/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.times
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.uniforms.DrawUniformPlan
import com.awakekt.awake.render.passes.uniforms.InstancedUniformLayout
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.directionalLightFloats
import com.awakekt.awake.render.passes.uniforms.drawUniformPlan
import com.awakekt.awake.render.passes.uniforms.gpuLitShadowUniforms
import com.awakekt.awake.render.passes.uniforms.litUniforms
import com.awakekt.awake.render.passes.uniforms.texturedUniforms
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedFields
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * The shared draw-preparation policy used by every backend.
 *
 * A backend still decides which native pipeline, buffer and binding to allocate. It must not
 * decide which shader ABI to write, or assemble a subtly different prefix of the same block.
 * Keeping this in `render:passes` makes the source packet the only input to that decision.
 */
fun RenderDrawCommand.uniformFloats(
    materialUniformFloatCount: Int,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    lightPayload: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
): FloatArray {
    val mvp = model * viewProjection
    return when (
        drawUniformPlan(
            format = mesh.format,
            materialUniformFloatCount = materialUniformFloatCount,
            hasShadowCascades = shadowCascades != null,
        )
    ) {
        DrawUniformPlan.LitShadow -> gpuLitShadowUniforms(
            transform = model,
            extraUniformFloats = extraUniformFloats,
            vertexAnimation = vertexAnimation,
            timeSeconds = timeSeconds,
            mvp = mvp,
            lightPayload = lightPayload,
            cascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED,
            cameraEye = cameraEye,
            fogColor = fogColor,
            fogDensity = fogDensity,
        )

        DrawUniformPlan.Skinned -> UniformWriter(SkinnedUniformLayout)
            .put(mvp.data, UniformFields.Mvp)
            .putPadded(SkinnedFields.JointPalette, extraUniformFloats)
            .build()

        DrawUniformPlan.TexturedPbr -> texturedUniforms(
            mvp = mvp,
            model = model,
            lightPayload = lightPayload,
            extraUniformFloats = extraUniformFloats,
            cameraEye = cameraEye,
            fogColor = fogColor,
            fogDensity = fogDensity,
        )

        DrawUniformPlan.Lit -> litUniforms(
            mvp = mvp,
            lightPayload = lightPayload,
            extraUniformFloats = extraUniformFloats,
            usePbrFactors = materialUniformFloatCount >= com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts.Lit.total,
        )
    }
}

/** Packs the common MVP/light block used by plain and skinned instanced variants. */
fun RenderDrawCommand.instancedUniformFloats(
    kind: InstancedDrawKind,
    viewProjection: Mat4,
    lightPayload: FloatArray,
    cameraEye: Vec3f = Vec3f(0f, 0f, 0f),
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    materialUniformFloatCount: Int = 0,
): FloatArray = when (kind) {
    InstancedDrawKind.Particle -> UniformWriter(ParticleUniformLayout)
        .put(viewProjection.data, UniformFields.Mvp)
        .put(extraUniformFloats, *ParticleExtraUniformLayout.fields)
        .build()

    InstancedDrawKind.Plain,
    InstancedDrawKind.Skinned,
    -> {
        if (shadowCascades != null || materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total) {
            gpuLitShadowUniforms(
                transform = Mat4(),
                extraUniformFloats = extraUniformFloats,
                vertexAnimation = vertexAnimation,
                timeSeconds = timeSeconds,
                mvp = viewProjection,
                lightPayload = lightPayload,
                cascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED,
                cameraEye = cameraEye,
                fogColor = fogColor,
                fogDensity = fogDensity,
            )
        } else {
            UniformWriter(InstancedUniformLayout)
                .put(viewProjection.data, UniformFields.Mvp)
                .put(
                    directionalLightFloats(lightPayload),
                    UniformFields.LightDirection,
                    UniformFields.LightColor,
                )
                .build()
        }
    }
}
