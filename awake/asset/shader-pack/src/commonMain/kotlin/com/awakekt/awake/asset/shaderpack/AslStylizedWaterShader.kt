/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
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
 *   hemisphere sky-to-zenith reflection, directional sun specular sparkle, translucent sunlit
 *   wave-crest glow, optional depth-based shallow color absorption, animated underwater caustics,
 *   eroding lacy shore/obstacle foam & bubble specks, Fresnel-driven opacity, and fog.
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

    // Exact integer-safe mod289 polynomial hash (Gustavson permute): zero trig/SFU ops and immune to
    // GPU FMA reassociation seams across lattice corners. Emitted only when SceneDepth foam/caustics are enabled.
    val cellularNoise: Pair<(AslExpr) -> AslExpr, (AslExpr) -> AslExpr>? = if (samplesDepth) {
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
        val valueNoiseFn = fn("valueNoise") {
            val p by param(GpuDataShape.Vec2)
            val i = let("i", floor(p))
            val f = let("f", p - i)
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
        val rotateOctaveFn = fn("rotateOctave", returns = AslType.Data(GpuDataShape.Vec2)) {
            val p by param(GpuDataShape.Vec2)
            // 2x scale rotated by atan(3/4) (~36.9 deg) breaks grid alignment between foam/caustic octaves.
            returnValue(vec2(p.x * 1.6f.lit - p.y * 1.2f.lit, p.x * 1.2f.lit + p.y * 1.6f.lit))
        }
        Pair({ p -> valueNoiseFn(p) }, { p -> rotateOctaveFn(p) })
    } else {
        null
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

            // Clean translucent subsurface glow on wave crests (no speckled noise blotches in open water).
            val bodyColor = let("bodyColor", mix(deepColor.rgb, shallowColor.rgb, waveHeight) * dayTint)
            val crestMask = let("crestMask", smoothstep(0.64f.lit, 0.94f.lit, waveHeight) * highlightParams.y)
            val crestTint = let(
                "crestTint",
                mix(
                    clamp(shallowColor.rgb * 1.25f.lit, vec3(0f.lit), vec3(1.2f.lit)),
                    foamColor.rgb,
                    0.55f.lit,
                ) * dayTint,
            )

            val sunVis = let("sunVis", smoothstep(0f.lit, 0.15f.lit, sunDir.y))
            val sunAlign = let("sunAlign", max(dot(refl, sunDir), 0f.lit))
            val sunGlint = let("sunGlint", pow(sunAlign, 96f.lit) * lightColor.rgb * (highlightParams.x * sunVis))
            val baseAlpha = let("baseAlpha", clamp(zenithColor.w * 0.85f.lit + fresnel * 0.28f.lit, 0f.lit, 1f.lit))

            if (depthSample != null && clipPos != null && cellularNoise != null) {
                val (valueNoise, rotateOctave) = cellularNoise
                // Multi-octave rotated cellular noise (zero trig, FMA-safe) for lacy shore/rock foam and caustics.
                val foamUv1 = let(
                    "foamUv1",
                    vec2(flowX, flowZ) * (scale * 2.8f.lit) + vec2(h1 * 0.28f.lit, h2 * 0.28f.lit) +
                        vec2(time * 0.18f.lit, -time * 0.14f.lit),
                )
                val nCoarse = let("nCoarse", valueNoise(foamUv1))
                val domainWarp = let("domainWarp", vec2((nCoarse - 0.5f.lit) * 0.95f.lit, (h3 - h4) * 0.22f.lit))
                val foamUv2 = let(
                    "foamUv2",
                    rotateOctave(foamUv1 + domainWarp) + vec2(7.3f.lit - time * 0.24f.lit, 11.1f.lit + time * 0.19f.lit),
                )
                val nMid = let("nMid", valueNoise(foamUv2))
                val foamUv3 = let(
                    "foamUv3",
                    rotateOctave(foamUv2 + vec2((nMid - 0.5f.lit) * 0.75f.lit, (nCoarse - 0.5f.lit) * 0.75f.lit)) +
                        vec2(-5.4f.lit + time * 0.31f.lit, 13.7f.lit - time * 0.22f.lit),
                )
                val nFine = let("nFine", valueNoise(foamUv3))
                val ridgeMid = let("ridgeMid", 1f.lit - abs(nMid * 2f.lit - 1f.lit))
                val ridgeFine = let("ridgeFine", 1f.lit - abs(nFine * 2f.lit - 1f.lit))
                val foamLace = let("foamLace", clamp(ridgeMid * 0.56f.lit + ridgeFine * 0.44f.lit, 0f.lit, 1f.lit))
                val foamPattern = let(
                    "foamPattern",
                    clamp(foamLace * 0.62f.lit + nCoarse * 0.16f.lit + nFine * 0.30f.lit, 0f.lit, 1f.lit),
                )
                val foamLit = let("foamLit", clamp(dayTint * 1.34f.lit, vec3(0.22f.lit), vec3(1.35f.lit)))

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

                // Shallow-to-deep optical color absorption + animated underwater caustics.
                val absorbDepth = let("absorbDepth", max(foamWidth * 2.5f.lit, 0.25f.lit))
                val depthFactor = let(
                    "depthFactor",
                    mix(1f.lit, smoothstep(0f.lit, absorbDepth, contactDepth), validDepth),
                )
                val shallowLift = let(
                    "shallowLift",
                    clamp(
                        shallowColor.rgb * 1.28f.lit + vec3(0.03f.lit, 0.13f.lit, 0.09f.lit),
                        vec3(0f.lit),
                        vec3(1.25f.lit),
                    ) * dayTint,
                )
                val absorbedBody = let("absorbedBody", mix(shallowLift, bodyColor, depthFactor))
                val causticNet = let(
                    "causticNet",
                    clamp(pow(ridgeMid, 4.2f.lit) * 0.90f.lit + pow(ridgeFine, 4.5f.lit) * 0.38f.lit, 0f.lit, 1f.lit),
                )
                val causticZone = let(
                    "causticZone",
                    (1f.lit - depthFactor) * smoothstep(0.03f.lit, 0.20f.lit, contactDepth) * validDepth * sunVis,
                )
                val causticsRgb = let("causticsRgb", lightColor.rgb * (causticNet * causticZone * 0.62f.lit))
                val withCrest = let("withCrest", mix(absorbedBody + causticsRgb, crestTint, crestMask))
                val reflWeight = let(
                    "reflWeight",
                    clamp(fresnel * mix(0.42f.lit, 0.85f.lit, depthFactor), 0f.lit, 0.9f.lit),
                )
                val blended = let("blended", mix(withCrest, skyRefl, reflWeight) + sunGlint)

                // Frothy, lacy eroding contact foam around rocks and shorelines (no smooth stripes).
                val lapWave = let("lapWave", sin(contactDepth * 9.0f.lit - time * 2.8f.lit + nCoarse * 5.4f.lit))
                val warpedDepth = let(
                    "warpedDepth",
                    max(
                        contactDepth + (nCoarse - 0.5f.lit) * (foamWidth * 0.42f.lit) -
                            lapWave * (foamWidth * 0.07f.lit),
                        0f.lit,
                    ),
                )
                val contactRatio = let("contactRatio", saturate(warpedDepth / foamWidth))
                val rimEdge = let(
                    "rimEdge",
                    (1f.lit - smoothstep(0f.lit, foamWidth * 0.16f.lit, contactDepth)) *
                        smoothstep(0.24f.lit, 0.58f.lit, foamPattern),
                )
                val swashBoost = let(
                    "swashBoost",
                    smoothstep(0.25f.lit, 0.95f.lit, lapWave) * 0.16f.lit * (1f.lit - contactRatio),
                )
                val erodeThreshold = let(
                    "erodeThreshold",
                    clamp(0.38f.lit + contactRatio * 0.60f.lit - swashBoost, 0.22f.lit, 0.96f.lit),
                )
                val lacyFoam = let(
                    "lacyFoam",
                    smoothstep(erodeThreshold - 0.08f.lit, erodeThreshold + 0.06f.lit, foamPattern) *
                        (1f.lit - smoothstep(0.58f.lit, 1f.lit, contactRatio)),
                )
                val bubbleSpecks = let(
                    "bubbleSpecks",
                    smoothstep(0.72f.lit, 0.90f.lit, nFine) *
                        (1f.lit - smoothstep(0.10f.lit, 0.88f.lit, contactRatio)) * 0.62f.lit,
                )
                val contactFoam = let(
                    "contactFoam",
                    clamp((max(rimEdge, lacyFoam) + bubbleSpecks) * validDepth * flowAndFoam.w, 0f.lit, 1f.lit),
                )
                val thinFoamTint = let("thinFoamTint", mix(shallowColor.rgb, foamColor.rgb, 0.78f.lit))
                val foamShade = let(
                    "foamShade",
                    mix(
                        thinFoamTint,
                        foamColor.rgb * 1.12f.lit,
                        smoothstep(0.14f.lit, 0.72f.lit, contactFoam),
                    ) * foamLit,
                )
                val shallowAlpha = let("shallowAlpha", mix(0.28f.lit, baseAlpha, depthFactor))
                val edgeSoft = let("edgeSoft", mix(1f.lit, smoothstep(0f.lit, 0.08f.lit, contactDepth), validDepth))
                assign(surfaceRgb, mix(blended, foamShade, contactFoam))
                assign(surfaceAlpha, clamp(max(shallowAlpha * edgeSoft, contactFoam * 0.96f.lit), 0f.lit, 1f.lit))
            } else {
                val withCrest = let("withCrest", mix(bodyColor, crestTint, crestMask))
                val blended = let(
                    "blended",
                    mix(withCrest, skyRefl, clamp(fresnel * 0.85f.lit, 0f.lit, 0.9f.lit)) + sunGlint,
                )
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
