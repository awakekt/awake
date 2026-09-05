/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.fullScreenTriangleCorner
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.normalize
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.smoothstep
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.renderer.SkyboxUniformLayout

/**
 * Procedural sky drawn first in the 3D pass, depth test/write both off. No vertex buffer: the
 * vertex stage emits one oversized triangle from `vertex_index` (three vertices cover the
 * viewport; a quad would need two triangles and a diagonal seam). The uniform struct derives
 * from [SkyboxUniformLayout] -- the same block `skyboxUniformFloats` packs. Cosine-space disc
 * thresholds: `dot(rayDir, sunDir)` is already a cosine, so comparing there skips an `acos`
 * per pixel; the discs are wider than the real sun's half degree so they read at typical
 * fov/resolution combinations.
 */
val SkyboxShader: AslShaderDefinition = shader("skybox") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(SkyboxUniformLayout)
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val sunDirection = handles.value("sunDirection")
    val horizonColor = handles.value("horizonColor")
    val zenithColor = handles.value("zenithColor")
    val sunColor = handles.value("sunColor")
    val moonColor = handles.value("moonColor")

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        // z = 1: the far plane in both backends' 0..1 depth range. NDC passes through
        // unchanged so the fragment's unprojection stays clip-space-convention-agnostic.
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    val sunCosOuter = const("SUN_COS_OUTER", 0.9975f)
    val sunCosInner = const("SUN_COS_INNER", 0.9995f)
    val moonCosOuter = const("MOON_COS_OUTER", 0.9985f)
    val moonCosInner = const("MOON_COS_INNER", 0.9996f)

    fragment {
        // The far plane is the best-conditioned depth to unproject a direction from.
        val far = let("far", inverseViewProjection * vec4(ndc, 1f.lit, 1f.lit))
        val rayDir = let("rayDir", normalize(far.xyz / far.w - cameraEye.xyz))
        val sunDir = let("sunDir", normalize(sunDirection.xyz))
        val sky = variable("color", mix(horizonColor.rgb, zenithColor.rgb, saturate(rayDir.y)))
        val sun = let("sun", smoothstep(sunCosOuter, sunCosInner, dot(rayDir, sunDir)))
        assign(sky, sky + sunColor.rgb * sun)
        // Scaled by (1 - sun) so both discs can never paint the same pixel.
        val moon = let("moon", smoothstep(moonCosOuter, moonCosInner, dot(rayDir, -sunDir)))
        assign(sky, sky + moonColor.rgb * moon * (1f.lit - sun))
        colorOutput(vec4(sky, 1f.lit))
    }
}
