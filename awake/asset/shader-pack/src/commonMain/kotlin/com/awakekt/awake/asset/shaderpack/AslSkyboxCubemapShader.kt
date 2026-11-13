/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.textureCube
import com.awakekt.awake.asset.shaderdsl.textureSampleCube
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/** Uniform field declarations for sampled cubemap skybox rendering. */
object SkyboxCubemapFields {
    /** Inverse view-projection matrix used to reconstruct view rays from screen quad UVs. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** World-space camera eye position. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** Exposure scale in x; in y, how far the first cubemap has blended into the second, 0 to 1. */
    val Exposure = UniformField("exposure", GpuDataShape.Vec4)

    /** Colour the sampled sky is multiplied by, in rgb. White leaves it as baked. */
    val Tint = UniformField("tint", GpuDataShape.Vec4)
}

/** Uniform buffer layout specification for cubemap skybox rendering. */
val SkyboxCubemapUniformLayout = UniformLayout(
    SkyboxCubemapFields.InverseViewProjection,
    SkyboxCubemapFields.CameraEye,
    SkyboxCubemapFields.Exposure,
    SkyboxCubemapFields.Tint,
)

/**
 * Packs cubemap skybox uniforms into a float array conforming to [SkyboxCubemapUniformLayout].
 *
 * @param inverseViewProjection Inverse view-projection matrix reconstructing world view rays.
 * @param cameraEye World-space camera eye position.
 * @param exposure Tone mapping exposure factor.
 * @param blend How far the first cubemap has blended into the second, 0 to 1.
 * @param tint Colour the sampled sky is multiplied by.
 * @return Formatted uniform buffer float array.
 */
fun skyboxCubemapUniformFloats(
    inverseViewProjection: Mat4,
    cameraEye: Vec3f,
    exposure: Float = 1.0f,
    blend: Float = 0f,
    tint: Vec3f = Vec3f(1f, 1f, 1f),
): FloatArray = UniformWriter(SkyboxCubemapUniformLayout)
    .put(SkyboxCubemapFields.InverseViewProjection, inverseViewProjection)
    .put(SkyboxCubemapFields.CameraEye, cameraEye)
    .put(SkyboxCubemapFields.Exposure, exposure, blend, 0f, 0f)
    .put(SkyboxCubemapFields.Tint, tint.x, tint.y, tint.z, 0f)
    .build()

/**
 * Environmental cubemap sky drawn first in the 3D pass. No vertex buffer: the vertex stage
 * emits one oversized triangle from `vertex_index`. Samples two cubemaps via [textureSampleCube] and
 * blends them, so a day cycle can fade one panorama into the next.
 */
val SkyboxCubemapShader: AslShaderDefinition = shader("skybox_cubemap") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(SkyboxCubemapUniformLayout)
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val exposure = handles.value("exposure")
    val tint = handles.value("tint")

    val envMap by textureCube(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 1,
    )
    val envSampler by sampler(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 2,
    )

    val envMapB by textureCube(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 3,
    )

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    fragment {
        val far = let("far", inverseViewProjection * vec4(ndc, 1f.lit, 1f.lit))
        val rayDir = let("rayDir", normalize(far.xyz / far.w - cameraEye.xyz))
        val sampled = let("sampledColor", textureSampleCube(envMap, envSampler, rayDir))
        val next = let("blendedColor", textureSampleCube(envMapB, envSampler, rayDir))
        // blend 0 is exactly the first cubemap, and a white tint exactly the baked colour.
        val exposed = mix(sampled.rgb, next.rgb, exposure.y) * tint.rgb * exposure.x
        colorOutput(vec4(exposed, 1f.lit))
    }
}
