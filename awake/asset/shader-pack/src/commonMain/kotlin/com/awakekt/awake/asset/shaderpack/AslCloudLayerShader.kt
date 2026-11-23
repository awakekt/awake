/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Single-pass procedural cloud layer shader with zero texture fetches and a uniform quality switch
 * ([CloudLayerQuality.Fast] vs [CloudLayerQuality.Standard]).
 *
 * Replaces stacked multi-mesh cloud-sea sheets with one alpha-blended pass, halving transparent
 * overdraw while supporting two parallax-drifting layers, warm sunlit tops with cool undersides,
 * smooth distance horizon fading, and automatic day/night colour adaptation.
 */
@Suppress("LongMethod")
val CloudLayerShader: AslShaderDefinition = shader("cloud_layer") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(CloudLayerUniformLayout)
    val viewProjection = handles.value("viewProjection")
    val cameraEye = handles.value("cameraEye")
    val lightDirection = handles.value("lightDirection")
    val lightColor = handles.value("lightColor")
    val horizonColor = handles.value("horizonColor")
    val fogColor = handles.value("fogColor")
    val fogAndFade = handles.value("fogAndFade")
    val cloudColor = handles.value("cloudColor")
    val shadowColor = handles.value("shadowColor")
    val cloudParams = handles.value("cloudParams")
    val origin = handles.value("origin")

    val out = varyings("VertexOutput")
    val worldPos by out.varying(GpuDataShape.Vec3, location = 0)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val pos = let("worldPosition", ins.input(VertexSemantic.Position) + origin.xyz)
        out.position set (viewProjection * vec4(pos, 1f.lit))
        worldPos set pos
    }

    val hash2d = fn("hash2d") {
        val p by param(GpuDataShape.Vec2)
        val h = let("h", dot(p, vec2(127.1f.lit, 311.7f.lit)))
        returnValue(fract(sin(h) * 43758.5453f.lit))
    }

    val valueNoise = fn("valueNoise") {
        val p by param(GpuDataShape.Vec2)
        val i = let("i", floor(p))
        val f = let("f", fract(p))
        val uSmooth = let("uSmooth", f * f * (vec2(3f.lit) - f * 2f.lit))
        val a = let("a", hash2d(i))
        val b = let("b", hash2d(i + vec2(1f.lit, 0f.lit)))
        val c = let("c", hash2d(i + vec2(0f.lit, 1f.lit)))
        val d = let("d", hash2d(i + vec2(1f.lit, 1f.lit)))
        returnValue(mix(mix(a, b, uSmooth.x), mix(c, d, uSmooth.x), uSmooth.y))
    }

    val displayTransform = sceneDisplayTransform()

    fragment {
        val time = let("time", cameraEye.w)
        val scale = let("cloudScale", cloudColor.w)
        val drift = let("drift", vec2(cloudParams.x * time, cloudParams.y * time))
        val uvBase = let("uvBase", vec2(worldPos.x * scale, worldPos.z * scale) + drift)

        val sunDir = let("sunDir", normalize(lightDirection.xyz))
        val sunElev = let("sunElev", saturate(sunDir.y))
        val ambient = let("ambient", max(lightColor.w, 0.22f.lit))
        val dayLight = let("dayLight", clamp(lightColor.rgb * (sunElev * 0.72f.lit) + vec3(ambient), vec3(0.20f.lit), vec3(1.25f.lit)))

        val dist = let("dist", length(cameraEye.xyz - worldPos))
        val threshold = let("threshold", 1f.lit - clamp(cloudParams.z, 0.05f.lit, 0.95f.lit))
        val softness = let("softness", max(cloudParams.w, 0.05f.lit))

        val qualityCode = let("qualityCode", toU32(lightDirection.w + 0.5f.lit))
        val isStandard = qualityCode eq CloudLayerQuality.Standard.code.toUInt().lit
        val isFast = select(1u.lit, 0u.lit, isStandard) eq 1u.lit

        val density = variable("density", 0f.lit)
        val litFactor = variable("litFactor", 0.65f.lit)

        iff(isFast) {
            val n1 = let("n1", valueNoise(uvBase))
            val n2 = let("n2", valueNoise(uvBase * 2.03f.lit + vec2(13.7f.lit, 7.1f.lit)))
            val fbm = let("fbmFast", n1 * 0.65f.lit + n2 * 0.35f.lit)
            assign(density, smoothstep(threshold, threshold + softness, fbm))
            assign(litFactor, clamp(0.45f.lit + fbm * 0.55f.lit, 0f.lit, 1f.lit))
        }

        iff(isStandard) {
            // Lower wispy cloud deck drifting at 1.0x speed.
            val l1a = let("l1a", valueNoise(uvBase))
            val l1b = let("l1b", valueNoise(uvBase * 2.07f.lit + vec2(11.3f.lit, 5.9f.lit)))
            val lowerNoise = let("lowerNoise", l1a * 0.62f.lit + l1b * 0.38f.lit)
            val lowerDensity = let("lowerDensity", smoothstep(threshold, threshold + softness, lowerNoise))

            // Upper billowing cloud deck drifting faster at 1.55x parallax speed in the same pass.
            val uvUpper = let("uvUpper", vec2(worldPos.x * scale, worldPos.z * scale) * 1.35f.lit + drift * 1.55f.lit + vec2(4.2f.lit, 9.6f.lit))
            val u1 = let("u1", valueNoise(uvUpper))
            val u2 = let("u2", valueNoise(uvUpper * 2.11f.lit + vec2(19.4f.lit, 3.7f.lit)))
            val u3 = let("u3", valueNoise(uvUpper * 4.19f.lit - vec2(6.8f.lit, 14.2f.lit)))
            val upperNoise = let("upperNoise", u1 * 0.55f.lit + u2 * 0.30f.lit + u3 * 0.15f.lit)
            val upperDensity = let("upperDensity", smoothstep(threshold + 0.04f.lit, threshold + 0.04f.lit + softness, upperNoise))

            val combined = let("combined", clamp(lowerDensity * 0.70f.lit + upperDensity * (1f.lit - lowerDensity * 0.45f.lit), 0f.lit, 1f.lit))
            assign(density, combined)
            assign(litFactor, clamp(0.32f.lit + upperDensity * 0.52f.lit + upperNoise * 0.22f.lit, 0f.lit, 1f.lit))
        }

        val shaded = let("shaded", mix(shadowColor.rgb, cloudColor.rgb, litFactor) * dayLight)
        val horizonBlend = let("horizonBlend", mix(shaded, horizonColor.rgb, 0.15f.lit))
        val exposed = let("exposed", displayTransform.display(horizonBlend, horizonColor.w))
        val fogRange = let("fogRange", vec4(fogAndFade.x, fogAndFade.y, 0f.lit, 0f.lit))
        val fog = let("fog", fogAmount(dist, fogColor, fogRange))
        val finalRgb = let("finalRgb", mix(exposed, fogColor.rgb, saturate(fog)))

        val fadeActive = fogAndFade.w gt fogAndFade.z
        val edgeFade = let(
            "edgeFade",
            select(1f.lit, 1f.lit - smoothstep(fogAndFade.z, fogAndFade.w, dist), fadeActive),
        )
        val finalAlpha = let("finalAlpha", clamp(density * shadowColor.w * edgeFade, 0f.lit, 1f.lit))
        colorOutput(vec4(finalRgb, finalAlpha))
    }
}
