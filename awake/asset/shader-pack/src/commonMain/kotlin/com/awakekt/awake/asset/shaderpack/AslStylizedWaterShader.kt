/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.samplerNonFiltering
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.textureDepth2d
import com.awakekt.awake.asset.shaderdsl.textureSampleLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

private const val WATER_FAR_PLANE = 0.9999f

/**
 * Procedural stylized water surface shader with a uniform quality switch
 * ([StylizedWaterQuality.Fast] vs [StylizedWaterQuality.Standard]), optional directional flow, and
 * optional [BindingSemantic.SceneDepth] contact foam around shorelines, rocks, pillars, and wading
 * characters.
 *
 * - **Fast**: two cross-directional world-space sine waves driving shallow/deep tinting, day/night
 *   scaling, Schlick Fresnel horizon sheen, and distance fog (0 texture fetches even when
 *   [samplesDepth] is true).
 * - **Standard**: four cross-directional stylized wave octaves with analytic normal perturbation,
 *   hemisphere sky-to-zenith reflection, directional sun specular sparkle, bright wave crests,
 *   optional depth-based shore/obstacle foam & bubble rings, Fresnel-driven opacity, and fog.
 */
@Suppress("LongMethod")
fun stylizedWaterShader(clipSpace: ClipSpace, samplesDepth: Boolean = false): AslShaderDefinition = shader(
    if (samplesDepth) "stylized_water_depth" else "stylized_water",
) {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(StylizedWaterUniformLayout)
    val viewProjection = handles.value("viewProjection")
    val inverseViewProjection = handles.value("inverseViewProjection")
    val cameraEye = handles.value("cameraEye")
    val lightDirection = handles.value("lightDirection")
    val lightColor = handles.value("lightColor")
    val horizonColor = handles.value("horizonColor")
    val zenithColor = handles.value("zenithColor")
    val fogColor = handles.value("fogColor")
    val fogRange = handles.value("fogRange")
    val shallowColor = handles.value("shallowColor")
    val deepColor = handles.value("deepColor")
    val foamColor = handles.value("foamColor")
    val highlightParams = handles.value("highlightParams")
    val flowAndFoam = handles.value("flowAndFoam")

    val depthSample: ((AslExpr) -> AslExpr)? = if (samplesDepth) {
        val depthGroup = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)
        val sceneDepth by textureDepth2d(group = depthGroup, binding = 0)
        val sceneDepthSampler by samplerNonFiltering(group = depthGroup, binding = 1)
        val tap: (AslExpr) -> AslExpr = { uv ->
            val clamped = clamp(uv, vec2(0.001f.lit, 0.001f.lit), vec2(0.999f.lit, 0.999f.lit))
            textureSampleLevelDepth(sceneDepth, sceneDepthSampler, clamped, 0.lit)
        }
        tap
    } else {
        null
    }

    val out = varyings("VertexOutput")
    val worldPos by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val clipPos = if (samplesDepth) {
        val v by out.varying(GpuDataShape.Vec4, location = 2)
        v
    } else {
        null
    }

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val origin = let("origin", vec3(highlightParams.z, fogRange.z, highlightParams.w))
        val pos = let("worldPosition", ins.input(VertexSemantic.Position) + origin)
        val projected = let("projected", viewProjection * vec4(pos, 1f.lit))
        out.position set projected
        worldPos set pos
        normal set ins.input(VertexSemantic.Normal)
        if (clipPos != null) {
            clipPos set projected
        }
    }

    val displayTransform = sceneDisplayTransform()

    fragment {
        val rawTime = let("rawTime", cameraEye.w)
        val time = let("time", rawTime * deepColor.w)
        val scale = let("waveScale", shallowColor.w)
        val sunDir = let("sunDir", normalize(lightDirection.xyz))
        val toEye = let("toEye", cameraEye.xyz - worldPos)
        val dist = let("dist", length(toEye))
        val viewDir = let("viewDir", normalize(toEye))
        val baseNormal = let("baseNormal", normalize(normal))
        val sunElev = let("sunElev", saturate(sunDir.y))
        val ambient = let("ambient", max(lightColor.w, 0.15f.lit))
        val dayTint = let("dayTint", clamp(lightColor.rgb * (sunElev * 0.75f.lit) + vec3(ambient), vec3(0.18f.lit), vec3(1.25f.lit)))

        val qualityCode = let("qualityCode", toU32(lightDirection.w + 0.5f.lit))
        val isStandard = qualityCode eq StylizedWaterQuality.Standard.code.toUInt().lit
        val isFast = select(1u.lit, 0u.lit, isStandard) eq 1u.lit

        val surfaceRgb = variable("surfaceRgb", vec3(0f.lit))
        val surfaceAlpha = variable("surfaceAlpha", zenithColor.w)

        // Optional river/stream flow advection in world XZ (zero flow reduces to pure standing waves).
        val flowX = let("flowX", worldPos.x - flowAndFoam.x * rawTime)
        val flowZ = let("flowZ", worldPos.z - flowAndFoam.y * rawTime)

        // Two primary cross-directional wave phases shared conceptually across tiers.
        val p1 = let("p1", flowX * scale + flowZ * (scale * 0.42f.lit) + time * 1.35f.lit)
        val p2 = let("p2", flowZ * (scale * 1.18f.lit) - flowX * (scale * 0.36f.lit) - time * 1.10f.lit)

        iff(isFast) {
            val w1 = let("w1", sin(p1))
            val w2 = let("w2", cos(p2))
            val wave = let("wave", clamp(0.5f.lit + 0.25f.lit * (w1 + w2), 0f.lit, 1f.lit))
            val baseWater = let("baseWater", mix(deepColor.rgb, shallowColor.rgb, wave) * dayTint)
            val nDotV = let("nDotV", clamp(abs(dot(baseNormal, viewDir)), 0.01f.lit, 1f.lit))
            val fresnel = let("fresnel", 0.04f.lit + 0.96f.lit * pow(1f.lit - nDotV, 5f.lit))
            val sheen = let("sheen", mix(baseWater, horizonColor.rgb, fresnel * 0.65f.lit))
            assign(surfaceRgb, sheen)
            assign(surfaceAlpha, clamp(zenithColor.w + fresnel * 0.18f.lit, 0f.lit, 1f.lit))
        }

        iff(isStandard) {
            val p3 = let("p3", (flowX * 1.75f.lit - flowZ * 1.45f.lit) * scale + time * 1.95f.lit)
            val p4 = let("p4", (flowX * 0.95f.lit + flowZ * 2.15f.lit) * scale - time * 1.65f.lit)

            val h1 = let("h1", sin(p1))
            val h2 = let("h2", cos(p2))
            val h3 = let("h3", sin(p3))
            val h4 = let("h4", cos(p4))
            val waveHeight = let("waveHeight", clamp(0.5f.lit + 0.20f.lit * h1 + 0.16f.lit * h2 + 0.10f.lit * h3 + 0.08f.lit * h4, 0f.lit, 1f.lit))

            // Analytic spatial derivatives of the four wave octaves for crisp ripple normals.
            val dHdx = let(
                "dHdx",
                cos(p1) * 0.45f.lit + sin(p2) * 0.18f.lit + cos(p3) * 0.35f.lit - sin(p4) * 0.16f.lit,
            )
            val dHdz = let(
                "dHdz",
                cos(p1) * 0.19f.lit - sin(p2) * 0.42f.lit - cos(p3) * 0.29f.lit - sin(p4) * 0.34f.lit,
            )
            val strength = let("strength", foamColor.w)
            val n = let("perturbedNormal", normalize(vec3(-dHdx * strength, 1f.lit, -dHdz * strength)))
            val nDotV = let("nDotVStd", clamp(abs(dot(n, viewDir)), 0.01f.lit, 1f.lit))
            val fresnel = let("fresnelStd", 0.04f.lit + 0.96f.lit * pow(1f.lit - nDotV, 5f.lit))

            // Reflect view vector across perturbed normal into live scene horizon/zenith sky gradient.
            val refl = let("refl", normalize(n * (2f.lit * dot(n, viewDir)) - viewDir))
            val skyMix = let("skyMix", saturate(refl.y * 0.85f.lit + 0.15f.lit))
            val skyRefl = let("skyRefl", mix(horizonColor.rgb, zenithColor.rgb, skyMix))

            val bodyColor = let("bodyColor", mix(deepColor.rgb, shallowColor.rgb, waveHeight) * dayTint)
            val crestMask = let("crestMask", smoothstep(0.62f.lit, 0.92f.lit, waveHeight) * highlightParams.y)
            val withCrest = let("withCrest", mix(bodyColor, foamColor.rgb * dayTint, crestMask))

            val sunVis = let("sunVis", smoothstep(0f.lit, 0.15f.lit, sunDir.y))
            val sunAlign = let("sunAlign", max(dot(refl, sunDir), 0f.lit))
            val sunGlint = let("sunGlint", pow(sunAlign, 96f.lit) * lightColor.rgb * (highlightParams.x * sunVis))

            val blended = let("blended", mix(withCrest, skyRefl, clamp(fresnel * 0.85f.lit, 0f.lit, 0.9f.lit)) + sunGlint)
            val baseAlpha = let("baseAlpha", clamp(zenithColor.w * 0.85f.lit + fresnel * 0.28f.lit, 0f.lit, 1f.lit))

            if (depthSample != null && clipPos != null) {
                val ndc = let("ndc", clipPos.xy / max(abs(clipPos.w), 1e-4f.lit))
                val uv = let("sceneDepthUv", ndcToUv(ndc, clipSpace))
                val sceneD = let("sceneD", depthSample(uv))
                val sceneClip = let("sceneClip", inverseViewProjection * vec4(ndc, sceneD, 1f.lit))
                val sceneWorld = let("sceneWorld", sceneClip.xyz / sceneClip.w)
                val validDepth = let("validDepth", step(sceneD, WATER_FAR_PLANE.lit))
                val vertDepth = let("vertDepth", max(worldPos.y - sceneWorld.y, 0f.lit))
                val rayDepth = let("rayDepth", max(length(sceneWorld - cameraEye.xyz) - dist, 0f.lit))
                val contactDepth = let("contactDepth", min(vertDepth, rayDepth))
                val foamWidth = let("foamWidth", max(flowAndFoam.z, 0.01f.lit))
                val foamZone = let("foamZone", (1f.lit - smoothstep(0f.lit, foamWidth, contactDepth)) * validDepth)
                val tightEdge = let("tightEdge", (1f.lit - smoothstep(0f.lit, foamWidth * 0.28f.lit, contactDepth)) * validDepth)
                val ringWave = let("ringWave", 0.5f.lit + 0.5f.lit * sin(contactDepth * 14f.lit - time * 3.8f.lit + waveHeight * 6f.lit))
                val contactFoam = let(
                    "contactFoam",
                    clamp((tightEdge + foamZone * ringWave * 0.72f.lit) * flowAndFoam.w, 0f.lit, 1f.lit),
                )
                val shallowClear = let("shallowClear", mix(1f.lit, smoothstep(0f.lit, 0.14f.lit, contactDepth), validDepth))
                assign(surfaceRgb, mix(blended, foamColor.rgb * dayTint, contactFoam))
                assign(surfaceAlpha, clamp(max(baseAlpha * shallowClear, contactFoam * 0.94f.lit), 0f.lit, 1f.lit))
            } else {
                assign(surfaceRgb, blended)
                assign(surfaceAlpha, baseAlpha)
            }
        }

        val exposed = let("exposed", displayTransform.display(surfaceRgb, horizonColor.w))
        val fog = let("fog", fogAmount(dist, fogColor, fogRange))
        val finalRgb = let("finalRgb", mix(exposed, fogColor.rgb, saturate(fog)))
        colorOutput(vec4(finalRgb, surfaceAlpha))
    }
}

/** Zero-texture procedural stylized water shader (without scene-depth contact foam). */
val StylizedWaterShader: AslShaderDefinition = stylizedWaterShader(ClipSpace.WebGpu, samplesDepth = false)
