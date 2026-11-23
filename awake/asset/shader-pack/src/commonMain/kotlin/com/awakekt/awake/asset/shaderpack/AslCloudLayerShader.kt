/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
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

    // Exact integer-safe mod289 polynomial hash (Gustavson permute): zero trig/SFU ops and immune to
    // GPU FMA reassociation seams across lattice corners (unlike fract(sin(dot) * 43758.5453)).
    val mod289 = fn("mod289") {
        val xVal by param(GpuDataShape.Float)
        returnValue(xVal - floor((xVal + 0.25f.lit) / 289f.lit) * 289f.lit)
    }

    val permute = fn("permute") {
        val xVal by param(GpuDataShape.Float)
        returnValue(mod289((xVal * 34f.lit + 1f.lit) * xVal))
    }

    val hash2d = fn("hash2d") {
        val p by param(GpuDataShape.Vec2)
        val px = let("px", mod289(p.x))
        val py = let("py", mod289(p.y))
        returnValue(permute(permute(px) + py) / 289f.lit)
    }

    val valueNoise = fn("valueNoise") {
        val p by param(GpuDataShape.Vec2)
        val i = let("i", floor(p))
        val f = let("f", p - i)
        // Quintic Hermite (C2-continuous): eliminates grid-axis crease artifacts in billowing clouds.
        val uSmooth = let(
            "uSmooth",
            f * f * f * (f * (f * 6f.lit - vec2(15f.lit)) + vec2(10f.lit)),
        )
        val a = let("a", hash2d(i))
        val b = let("b", hash2d(i + vec2(1f.lit, 0f.lit)))
        val c = let("c", hash2d(i + vec2(0f.lit, 1f.lit)))
        val d = let("d", hash2d(i + vec2(1f.lit, 1f.lit)))
        returnValue(mix(mix(a, b, uSmooth.x), mix(c, d, uSmooth.x), uSmooth.y))
    }

    val rotateOctave = fn("rotateOctave", returns = AslType.Data(GpuDataShape.Vec2)) {
        val p by param(GpuDataShape.Vec2)
        // 2x scale rotated by atan(3/4) (~36.9 deg) breaks axis alignment between FBM octaves.
        returnValue(vec2(p.x * 1.6f.lit - p.y * 1.2f.lit, p.x * 1.2f.lit + p.y * 1.6f.lit))
    }

    // Two-octave cumulus heightfield: macro bank + domed puff lobe octave (1 - (1-n)^2) for rounded billows.
    val cumulusMacro = fn("cumulusMacro") {
        val p by param(GpuDataShape.Vec2)
        val n1 = let("n1", valueNoise(p))
        val n2 = let("n2", valueNoise(rotateOctave(p) + vec2(17.3f.lit, 9.2f.lit)))
        val puff2 = let("puff2", 1f.lit - (1f.lit - n2) * (1f.lit - n2))
        returnValue(n1 * 0.62f.lit + puff2 * 0.38f.lit)
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
            val n2 = let("n2", valueNoise(rotateOctave(uvBase) + vec2(13.7f.lit, 7.1f.lit)))
            val fbm = let("fbmFast", n1 * 0.65f.lit + n2 * 0.35f.lit)
            assign(density, smoothstep(threshold, threshold + softness, fbm))
            assign(litFactor, clamp(0.45f.lit + fbm * 0.55f.lit, 0f.lit, 1f.lit))
        }

        iff(isStandard) {
            // Single-pass 3-shell parallax volume: base underside -> mid flank -> sunlit cumulus crown.
            // Smooth ray-plane offset clamps near the horizon and reverses automatically when viewed from below.
            val viewRay = let("viewRay", normalize(worldPos - cameraEye.xyz))
            val parallaxFactor = let("parallaxFactor", viewRay.y / (viewRay.y * viewRay.y + 0.18f.lit))
            val parallaxVec = let("parallaxVec", vec2(viewRay.x, viewRay.z) * parallaxFactor)
            val lookingDown = let("lookingDown", 1f.lit - smoothstep((-0.15f).lit, 0.15f.lit, viewRay.y))

            val baseH = let("baseH", cumulusMacro(uvBase))
            val baseAlpha = let("baseAlpha", smoothstep(threshold, threshold + softness * 0.85f.lit, baseH))

            val uvMid = let("uvMid", uvBase + parallaxVec * 0.14f.lit + drift * 0.08f.lit)
            val midH = let("midH", cumulusMacro(uvMid))
            val midAlpha = let(
                "midAlpha",
                smoothstep(threshold + 0.04f.lit, threshold + 0.04f.lit + softness * 0.70f.lit, midH),
            )

            val uvUpper = let("uvUpper", uvBase + parallaxVec * 0.28f.lit + drift * 0.16f.lit)
            val crownMacro = let("crownMacro", cumulusMacro(uvUpper))
            val crownDetail = let(
                "crownDetail",
                valueNoise(rotateOctave(rotateOctave(uvUpper)) - vec2(6.8f.lit, 14.2f.lit)),
            )
            val crownH = let("crownH", crownMacro * 0.84f.lit + crownDetail * 0.16f.lit)
            val crownAlpha = let(
                "crownAlpha",
                smoothstep(threshold + 0.09f.lit, threshold + 0.09f.lit + softness * 0.55f.lit, crownH),
            )

            // Directional sun-offset self-shadowing gives each puff a sunlit crown and cool leeward shadow.
            val sunStep = let("sunStep", vec2(sunDir.x, sunDir.z) * 0.24f.lit)
            val sunSample = let("sunSample", cumulusMacro(uvUpper + sunStep))
            val sunSlope = let("sunSlope", clamp((crownMacro - sunSample) * 4.2f.lit, (-0.50f).lit, 0.55f.lit))

            val baseLit = let("baseLit", clamp(0.10f.lit + baseH * 0.18f.lit + sunSlope * 0.16f.lit, 0.02f.lit, 0.40f.lit))
            val midLit = let("midLit", clamp(0.36f.lit + midH * 0.24f.lit + sunSlope * 0.42f.lit, 0.08f.lit, 0.86f.lit))
            val crownLit = let(
                "crownLit",
                clamp(0.70f.lit + smoothstep(0.45f.lit, 0.85f.lit, crownH) * 0.26f.lit + sunSlope * 0.55f.lit, 0.26f.lit, 1f.lit),
            )

            // Composite shells front-to-back (crown over mid over base when looking down; reversed looking up).
            val downA1 = let("downA1", midAlpha + baseAlpha * (1f.lit - midAlpha))
            val downLit1 = let(
                "downLit1",
                (midLit * midAlpha + baseLit * baseAlpha * (1f.lit - midAlpha)) / max(downA1, 0.001f.lit),
            )
            val downA2 = let("downA2", crownAlpha + downA1 * (1f.lit - crownAlpha))
            val downLit2 = let(
                "downLit2",
                (crownLit * crownAlpha + downLit1 * downA1 * (1f.lit - crownAlpha)) / max(downA2, 0.001f.lit),
            )

            val upA1 = let("upA1", midAlpha + crownAlpha * (1f.lit - midAlpha))
            val upLit1 = let(
                "upLit1",
                (midLit * midAlpha + crownLit * crownAlpha * (1f.lit - midAlpha)) / max(upA1, 0.001f.lit),
            )
            val upA2 = let("upA2", baseAlpha + upA1 * (1f.lit - baseAlpha))
            val upLit2 = let(
                "upLit2",
                (baseLit * baseAlpha + upLit1 * upA1 * (1f.lit - baseAlpha)) / max(upA2, 0.001f.lit),
            )

            assign(density, clamp(mix(upA2, downA2, lookingDown), 0f.lit, 1f.lit))
            assign(litFactor, clamp(mix(upLit2, downLit2, lookingDown), 0.02f.lit, 1f.lit))
        }

        val sunlitLift = let("sunlitLift", 1f.lit + smoothstep(0.65f.lit, 1f.lit, litFactor) * sunElev * 0.24f.lit)
        val shaded = let("shaded", mix(shadowColor.rgb, cloudColor.rgb, litFactor) * dayLight * sunlitLift)
        val horizonBlend = let("horizonBlend", mix(shaded, horizonColor.rgb, 0.05f.lit))
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
