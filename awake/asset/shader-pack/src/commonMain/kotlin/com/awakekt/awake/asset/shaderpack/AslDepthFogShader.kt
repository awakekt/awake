/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.exp
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.samplerNonFiltering
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.textureDepth2d
import com.awakekt.awake.asset.shaderdsl.textureDepth2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArrayLevelDepth
import com.awakekt.awake.asset.shaderdsl.textureSampleLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.DepthFogUniformLayout

/**
 * Anything at or past this depth is the cleared far plane -- nothing rasterised there.
 *
 * Not fogged: unprojecting the far plane yields a distance that saturates the exponential, so a
 * sky would flatten to a flat sheet of fog colour. Real fog does hide the horizon, but that is
 * the sky shader's own gradient to blend, not this pass's to overwrite.
 */
private const val FAR_PLANE = 0.9999f

/**
 * How far behind the nearest surface an exempt draw may sit and still count as that surface: the
 * scene depth and mask passes draw it through the same depth pipeline, so the two distances agree
 * but for rounding in the unprojection, never by a visible gap.
 */
private const val EXEMPT_RELATIVE_TOLERANCE = 0.0005f
private const val EXEMPT_ABSOLUTE_TOLERANCE = 0.001f

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
fun depthFogShader(clipSpace: ClipSpace): AslShaderDefinition = depthFogShader(clipSpace, exempting = false)

/**
 * [depthFogShader] that, [exempting], also leaves a pixel unfogged where its nearest surface is in
 * the mask's [COVERING_EXEMPT_MASK_LAYER]: an unlit draw, which promises its colour as stored.
 * [depthFogContentFeature] draws with it; it declares the mask group, which the plain shader does not.
 */
internal fun depthFogShader(clipSpace: ClipSpace, exempting: Boolean): AslShaderDefinition = shader(
    if (exempting) "depth_fog_exempting" else "depth_fog",
) {
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
    val sceneDepthSampler by samplerNonFiltering(group = depthGroup, binding = 1)

    // The camera depth of the draws exempt from coverings, an unlit material's, in its own layer.
    val exemptDepthAt: ((AslExpr) -> AslExpr)? = if (exempting) {
        val maskGroup = BindingLayout.Standard.slot(BindingSemantic.MaskDepth)
        val mask by textureDepth2dArray(group = maskGroup, binding = 0)
        val maskSampler by samplerNonFiltering(group = maskGroup, binding = 1)
        val sample: (AslExpr) -> AslExpr = { at ->
            textureSampleArrayLevelDepth(mask, maskSampler, at, COVERING_EXEMPT_MASK_LAYER.lit, 0.lit)
        }
        sample
    } else {
        null
    }

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
        if (exemptDepthAt == null) {
            colorOutput(vec4(fogColor.rgb, fog * drawn))
        } else {
            // 1 where the nearest surface is an exempt draw: the mask's layer holds it at the
            // scene's own distance. cameraEye.w is 1 only on a frame that drew that layer;
            // otherwise the layer holds whatever an earlier frame left there, so it is not read.
            val exemptDepth = let("exemptDepth", exemptDepthAt(uv))
            val exemptClip = let("exemptClip", inverseViewProjection * vec4(ndc, exemptDepth, 1f.lit))
            val exemptDist = let("exemptDist", length(exemptClip.xyz / exemptClip.w - cameraEye.xyz))
            val reach = let("reach", dist * (1f.lit + EXEMPT_RELATIVE_TOLERANCE.lit) + EXEMPT_ABSOLUTE_TOLERANCE.lit)
            val exempt = let("exempt", cameraEye.w * step(exemptDepth, farPlane) * step(exemptDist, reach))
            colorOutput(vec4(fogColor.rgb, fog * drawn * (1f.lit - exempt)))
        }
    }
}
