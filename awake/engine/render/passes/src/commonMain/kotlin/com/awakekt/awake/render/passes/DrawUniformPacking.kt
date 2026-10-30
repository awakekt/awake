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
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.uniforms.DEFAULT_BASE_COLOR_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_EMISSIVE_FACTOR
import com.awakekt.awake.render.passes.uniforms.DrawUniformPlan
import com.awakekt.awake.render.passes.uniforms.InstancedUniformLayout
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.passes.uniforms.WorldTextFields
import com.awakekt.awake.render.passes.uniforms.WorldTextUniformLayout
import com.awakekt.awake.render.passes.uniforms.blendsAdditively
import com.awakekt.awake.render.passes.uniforms.coverageCutoff
import com.awakekt.awake.render.passes.uniforms.directionalLightFloats
import com.awakekt.awake.render.passes.uniforms.drawUniformPlan
import com.awakekt.awake.render.passes.uniforms.gpuLitShadowUniforms
import com.awakekt.awake.render.passes.uniforms.litUniforms
import com.awakekt.awake.render.passes.uniforms.putDebugView
import com.awakekt.awake.render.passes.uniforms.sceneCoverageCutoff
import com.awakekt.awake.render.passes.uniforms.texturedUniforms
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedFields
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
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
): FloatArray = uniformFloats(
    materialUniformFloatCount = materialUniformFloatCount,
    viewProjection = viewProjection,
    cameraEye = cameraEye,
    lightPayload = lightPayload,
    shadowCascades = shadowCascades,
    fogColor = fogColor,
    fogDensity = fogDensity,
    cameraForward = Vec3f(0f, 0f, -1f),
)

/** Packs uniforms with an explicit camera direction for stable cascade selection. */
fun RenderDrawCommand.uniformFloats(
    materialUniformFloatCount: Int,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    lightPayload: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    cameraForward: Vec3f,
    debugView: GpuDebugView = GpuDebugView.Off,
    exposure: Float = 1f,
    alphaToCoverageEnabled: Boolean = false,
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
            cameraForward = cameraForward,
            fogColor = fogColor,
            fogDensity = fogDensity,
            debugView = debugView,
            exposure = exposure,
        )

        DrawUniformPlan.Sprite -> UniformWriter(SpriteUniformLayout)
            .put(mvp.data, UniformFields.Mvp)
            .put(extraUniformFloats, SpriteFields.UvTransform, SpriteFields.Tint)
            .build()

        DrawUniformPlan.WorldText -> UniformWriter(WorldTextUniformLayout)
            .put(mvp.data, UniformFields.Mvp)
            .put(extraUniformFloats, WorldTextFields.Tint, WorldTextFields.Field)
            .build()

        DrawUniformPlan.Skinned ->
            skinnedUniforms(mvp, model, extraUniformFloats, sceneCoverageCutoff(alphaToCoverageEnabled), exposure, lightPayload, cameraEye, debugView, cameraForward)

        DrawUniformPlan.TexturedPbr -> texturedUniforms(
            mvp = mvp,
            model = model,
            lightPayload = lightPayload,
            extraUniformFloats = extraUniformFloats,
            cameraEye = cameraEye,
            fogColor = fogColor,
            fogDensity = fogDensity,
            alphaCutoff = sceneCoverageCutoff(alphaToCoverageEnabled),
            debugView = debugView,
            cameraForward = cameraForward,
            timeSeconds = timeSeconds,
            shadowCascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED,
            exposure = exposure,
            additive = blendsAdditively,
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
): FloatArray = instancedUniformFloats(
    kind = kind,
    viewProjection = viewProjection,
    lightPayload = lightPayload,
    cameraEye = cameraEye,
    shadowCascades = shadowCascades,
    fogColor = fogColor,
    fogDensity = fogDensity,
    materialUniformFloatCount = materialUniformFloatCount,
    cameraForward = Vec3f(0f, 0f, -1f),
)

/** Packs instanced uniforms with an explicit camera direction for stable cascade selection. */
fun RenderDrawCommand.instancedUniformFloats(
    kind: InstancedDrawKind,
    viewProjection: Mat4,
    lightPayload: FloatArray,
    cameraEye: Vec3f = Vec3f(0f, 0f, 0f),
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    materialUniformFloatCount: Int = 0,
    cameraForward: Vec3f,
    debugView: GpuDebugView = GpuDebugView.Off,
    exposure: Float = 1f,
    alphaToCoverageEnabled: Boolean = false,
): FloatArray = when (kind) {
    InstancedDrawKind.Particle -> UniformWriter(ParticleUniformLayout)
        .put(viewProjection.data, UniformFields.Mvp)
        .put(extraUniformFloats, *ParticleExtraUniformLayout.fields)
        .build()

    InstancedDrawKind.Plain,
    InstancedDrawKind.Skinned,
    -> {
        val plan = drawUniformPlan(mesh.format, materialUniformFloatCount, hasShadowCascades = shadowCascades != null)
        if (kind == InstancedDrawKind.Plain && plan == DrawUniformPlan.TexturedPbr) {
            // Each instance carries its own model, so the block's is identity and its mvp the view-projection.
            texturedUniforms(
                mvp = viewProjection,
                model = Mat4(),
                lightPayload = lightPayload,
                extraUniformFloats = extraUniformFloats,
                cameraEye = cameraEye,
                fogColor = fogColor,
                fogDensity = fogDensity,
                alphaCutoff = sceneCoverageCutoff(alphaToCoverageEnabled),
                debugView = debugView,
                cameraForward = cameraForward,
                timeSeconds = timeSeconds,
                shadowCascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED,
                exposure = exposure,
                additive = blendsAdditively,
            )
        } else if (shadowCascades != null || materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total) {
            gpuLitShadowUniforms(
                transform = Mat4(),
                extraUniformFloats = extraUniformFloats,
                vertexAnimation = vertexAnimation,
                timeSeconds = timeSeconds,
                mvp = viewProjection,
                lightPayload = lightPayload,
                cascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED,
                cameraEye = cameraEye,
                cameraForward = cameraForward,
                fogColor = fogColor,
                fogDensity = fogDensity,
                debugView = debugView,
                exposure = exposure,
            )
        } else {
            UniformWriter(InstancedUniformLayout)
                .put(viewProjection.data, UniformFields.Mvp)
                .put(
                    directionalLightFloats(lightPayload),
                    UniformFields.LightDirection,
                    UniformFields.LightColor,
                )
                .put(UniformFields.Exposure, exposure, 0f, 0f, 0f)
                .build()
        }
    }
}

/**
 * The frame's lights from [lightPayload], laid out as [MaterialUniformLayouts.SceneLight]: the sun's
 * direction and colour, then the point-light slots. What a draw was packed without stays zero, which
 * the shaders read as no sun and no point light.
 */
private fun UniformWriter.putLights(lightPayload: FloatArray): UniformWriter {
    val sun = if (lightPayload.size >= SUN_FLOATS) {
        put(lightPayload, UniformFields.LightDirection, UniformFields.LightColor)
    } else {
        put(UniformFields.LightDirection, 0f, 0f, 0f, 0f).put(UniformFields.LightColor, 0f, 0f, 0f, 0f)
    }
    return if (lightPayload.size >= MaterialUniformLayouts.SceneLight.total) {
        sun.put(lightPayload, POINTS_AT, UniformFields.PointLightPositions, UniformFields.PointLightColors)
    } else {
        sun.putPadded(UniformFields.PointLightPositions, NO_LIGHTS).putPadded(UniformFields.PointLightColors, NO_LIGHTS)
    }
}

private val SUN_FLOATS = UniformFields.LightDirection.floats + UniformFields.LightColor.floats
private val POINTS_AT = MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightPositions)
private val NO_LIGHTS = FloatArray(0)

/**
 * A skinned draw's block: its palette, tinted by its material's factors when [extras] carries them
 * as [SkinnedMaterialLayout], untinted (glTF's default factors) when it is a palette alone, and the
 * [alphaCutoff] a masked one is cut out below; then the sun and point lights from [lightPayload], the
 * eye and the [debugView] the debug views read.
 */
@Suppress("LongParameterList") // One argument per draw input the block holds.
private fun skinnedUniforms(
    mvp: Mat4,
    model: Mat4,
    extras: FloatArray,
    alphaCutoff: Float,
    exposure: Float,
    lightPayload: FloatArray,
    cameraEye: Vec3f,
    debugView: GpuDebugView,
    cameraForward: Vec3f,
): FloatArray {
    val writer = UniformWriter(SkinnedUniformLayout).put(mvp.data, UniformFields.Mvp)
    val unlit = if (extras.size == SkinnedMaterialLayout.total) {
        writer
            .put(extras, 0, SkinnedFields.JointPalette)
            .put(model.data, UniformFields.Model)
            .put(extras, SkinnedMaterialLayout.offsetOf(UniformFields.BaseColorFactor), UniformFields.BaseColorFactor)
            .put(extras, SkinnedMaterialLayout.offsetOf(UniformFields.EmissiveFactor), UniformFields.EmissiveFactor)
        SkinnedMaterialLayout.readVec4(extras, UniformFields.PbrFactors).w
    } else {
        writer
            .putPadded(SkinnedFields.JointPalette, extras)
            .put(model.data, UniformFields.Model)
            .put(UniformFields.BaseColorFactor, DEFAULT_BASE_COLOR_FACTOR)
            .put(UniformFields.EmissiveFactor, DEFAULT_EMISSIVE_FACTOR)
        0f
    }
    return writer
        .put(UniformFields.PbrFactors, 0f, 0f, alphaCutoff, unlit)
        .put(UniformFields.Exposure, exposure, 0f, 0f, 0f)
        .putLights(lightPayload)
        .put(UniformFields.CameraPosition, cameraEye)
        .putDebugView(debugView, cameraForward)
        .put(SkinnedFields.DebugJoint, debugView.layer.toFloat(), 0f, 0f, 0f)
        .build()
}
