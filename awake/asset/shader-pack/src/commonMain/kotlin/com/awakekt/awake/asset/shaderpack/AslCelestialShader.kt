/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.sqrt
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * The sun and moon drawn over whatever sky is behind them, before geometry, with alpha blending.
 * No vertex buffer: one oversized triangle.
 *
 * Without a texture a body is the same cosine-space disc the procedural sky draws. With one, its
 * texture fills a square of the same angular size on the plane facing the camera. The moon's phase
 * cuts its disc along a terminator ellipse: new moon shows nothing, full moon the whole disc.
 */
val CelestialShader: AslShaderDefinition = shader("celestial") {
    val u = uniformBlock("Uniforms", group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 0)
    val handles = u.fieldsFrom(CelestialUniformLayout)
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val sunDirection = handles.value("sunDirection")
    val sunRight = handles.value("sunRight")
    val sunUp = handles.value("sunUp")
    val moonDirection = handles.value("moonDirection")
    val moonRight = handles.value("moonRight")
    val moonUp = handles.value("moonUp")
    val sunColor = handles.value("sunColor")
    val moonColor = handles.value("moonColor")
    val visibility = handles.value("visibility")
    val params = handles.value("params")

    val sunTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 1)
    val moonTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 2)
    val bodySampler by sampler(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 3)

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    val sunCosOuter = const("SUN_COS_OUTER", 0.9975f)
    val sunCosInner = const("SUN_COS_INNER", 0.9995f)
    val moonCosOuter = const("MOON_COS_OUTER", 0.9985f)
    val moonCosInner = const("MOON_COS_INNER", 0.9996f)
    // tan of each disc's outer angular radius: the half-width of its square on the plane at distance 1.
    val sunHalf = const("SUN_HALF", 0.0708f)
    val moonHalf = const("MOON_HALF", 0.0548f)

    fragment {
        val far = let("far", inverseViewProjection * vec4(ndc, 1f.lit, 1f.lit))
        val rayDir = let("rayDir", normalize(far.xyz / far.w - cameraEye.xyz))

        val sunCos = let("sunCos", dot(rayDir, sunDirection.xyz))
        val sunFront = let("sunFront", step(0.01f.lit, sunCos))
        val sunPlane = let("sunPlane", rayDir / max(sunCos, 0.01f.lit) - sunDirection.xyz)
        val sunX = let("sunX", dot(sunPlane, sunRight.xyz) / sunHalf)
        val sunY = let("sunY", dot(sunPlane, sunUp.xyz) / sunHalf)
        val sunTexel = let("sunTexel", textureSample(sunTexture, bodySampler, vec2(sunX * 0.5f.lit + 0.5f.lit, 0.5f.lit - sunY * 0.5f.lit)))
        val sunInside = let("sunInside", step(max(abs(sunX), abs(sunY)), 1f.lit) * sunFront)
        val sunDisc = let("sunDisc", smoothstep(sunCosOuter, sunCosInner, sunCos))
        val sunAlpha = let("sunAlpha", mix(sunDisc, sunTexel.w * sunInside, params.z) * visibility.x)
        val sunRgb = let("sunRgb", sunColor.rgb * mix(vec3(1f.lit, 1f.lit, 1f.lit), sunTexel.rgb, params.z))

        val moonCos = let("moonCos", dot(rayDir, moonDirection.xyz))
        val moonFront = let("moonFront", step(0.01f.lit, moonCos))
        val moonPlane = let("moonPlane", rayDir / max(moonCos, 0.01f.lit) - moonDirection.xyz)
        val moonX = let("moonX", dot(moonPlane, moonRight.xyz) / moonHalf)
        val moonY = let("moonY", dot(moonPlane, moonUp.xyz) / moonHalf)
        val moonTexel = let("moonTexel", textureSample(moonTexture, bodySampler, vec2(moonX * 0.5f.lit + 0.5f.lit, 0.5f.lit - moonY * 0.5f.lit)))
        val moonInside = let("moonInside", step(max(abs(moonX), abs(moonY)), 1f.lit) * moonFront)
        val moonDisc = let("moonDisc", smoothstep(moonCosOuter, moonCosInner, moonCos))
        // The terminator: lit where (waxing right, waning left) side of cos(angle) * the disc's width.
        val width = let("width", sqrt(max(1f.lit - moonY * moonY, 0f.lit)))
        val side = let("side", params.y * moonX - params.x * width)
        val litSide = let("litSide", max(smoothstep((-0.06f).lit, 0.06f.lit, side), step(params.x, (-0.9999f).lit)))
        val moonAlpha = let("moonAlpha", mix(moonDisc, moonTexel.w * moonInside, params.w) * litSide * visibility.y)
        val moonRgb = let("moonRgb", moonColor.rgb * mix(vec3(1f.lit, 1f.lit, 1f.lit), moonTexel.rgb, params.w))

        // The sun covers the moon where they meet, so both never paint one pixel.
        val moonShown = let("moonShown", saturate(moonAlpha * (1f.lit - sunAlpha)))
        val alpha = let("alpha", saturate(sunAlpha + moonShown))
        val rgb = let("bodyRgb", (sunRgb * sunAlpha + moonRgb * moonShown) / max(alpha, 0.0001f.lit))
        colorOutput(vec4(rgb, alpha))
    }
}
