/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.exp
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.fullScreenTriangleCorner
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.ndcToUv
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.step
import io.github.awakelab.awake.asset.shaderdsl.textureDepth2d
import io.github.awakelab.awake.asset.shaderdsl.textureSampleLevelDepth
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.renderer.DepthFogUniformLayout

/**
 * Anything at or past this depth is the cleared far plane -- nothing rasterised there.
 *
 * Not fogged: unprojecting the far plane yields a distance that saturates the exponential, so a
 * sky would flatten to a flat sheet of fog colour. Real fog does hide the horizon, but that is
 * the sky shader's own gradient to blend, not this pass's to overwrite.
 */
private const val FAR_PLANE = 0.9999f

/**
 * Full-screen exponential distance fog, blended over the scene from the depth already in front
 * of it.
 *
 * The engine's first consumer of [BindingSemantic.SceneDepth], and what that pass exists for.
 * `lit_shadow` already fogs per fragment from its own interpolated world position -- this fogs
 * from the depth target instead, so it covers every pipeline in the frame, including content
 * whose shader knows nothing about fog.
 *
 * Distance is the world distance from the eye, not a depth value: the sampled depth and the
 * fragment's NDC unproject through `inverseViewProjection` to a world position, which is the same
 * quantity `applyFog` measures in `lit_shadow`. Fogging by raw depth instead would make the
 * density mean something different at every field of view.
 *
 * @param clipSpace The convention this is emitted for. It decides how a fragment's NDC becomes a
 * coordinate into the depth target and nothing else -- see [ndcToUv], where that decision is made
 * for every shader that needs it.
 */
fun depthFogShader(clipSpace: ClipSpace): AslShaderDefinition = shader("depth_fog") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(DepthFogUniformLayout)
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val fogColor = handles.value("fogColor")

    val depthGroup = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)
    val sceneDepth by textureDepth2d(group = depthGroup, binding = 0)
    val sceneDepthSampler by sampler(group = depthGroup, binding = 1)

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        // Depth test and write are both off (PipelineVariant.Overlay), so this z is never
        // compared against anything -- 1 keeps it in range rather than meaning anything.
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    val farPlane = const("FAR_PLANE", FAR_PLANE)

    fragment {
        val uv = let("uv", ndcToUv(ndc, clipSpace))
        val depth = let("depth", textureSampleLevelDepth(sceneDepth, sceneDepthSampler, uv, 0.lit))
        val clip = let("clip", inverseViewProjection * vec4(ndc, depth, 1f.lit))
        val world = let("world", clip.xyz / clip.w)
        val dist = let("dist", length(world - cameraEye.xyz))
        val fog = let("fog", saturate(1f.lit - exp(-fogColor.a * dist)))
        // 1 where something was rasterised, 0 on the cleared far plane -- see [FAR_PLANE].
        val drawn = let("drawn", step(depth, farPlane))
        colorOutput(vec4(fogColor.rgb, fog * drawn))
    }
}
