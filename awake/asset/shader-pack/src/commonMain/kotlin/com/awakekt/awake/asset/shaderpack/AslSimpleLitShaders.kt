/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.namedInput
import com.awakekt.awake.asset.shaderdsl.namedVarying
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.or
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.samplerComparison
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDepth2dArray
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.textureSampleGrad
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
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TexturedBlend
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.SkinnedDrawLayout

/**
 * Lean mobile-first mesh shader family (`SimpleLit` / `SkinnedSimpleLit`):
 *
 * Keeps the exact draw/frame uniform structs and base-color bindings (`0`, `1`, `2`, plus frame
 * uniform) so existing meshes and `StandardMaterial` instances attach unchanged on both Vulkan and
 * WebGPU, while removing the major mobile TBDR fragment bottlenecks:
 * - **1 albedo texture fetch** (`baseColorTexture` only — zero `metallicRoughness`, `normal`,
 *   `occlusion`, or `emissive` texture bindings or fetches).
 * - **Zero screen-space TBN** (`dpdx(worldPos)` / `dpdy(worldPos)` / `perturbNormal` omitted).
 * - **Zero Cook-Torrance GGX / Smith / Fresnel `pow()` / analytic environment reflection** —
 *   uses energy-conserving Lambert diffuse + cheap Blinn-Phong highlight (`nDotH^16` via squaring),
 *   cascaded shadows, Lambert point lights, fog, and `emissiveFactor`.
 * - **Zero `pow(x, 2.2)` / `pow(x, 1/2.2)` SFU stalls** — 3-FMA polynomial sRGB decode and
 *   `sqrt`-based display encoding.
 * - **Opt-in surface deposit** (`includeSurfaceDeposit = false` by default, omitting 3-octave
 *   Simplex noise to preserve shader-core register occupancy on Mali/Adreno).
 */
private fun simpleLitShaderName(
    instanced: Boolean,
    tinted: Boolean,
    allowDiscard: Boolean,
    includeSurfaceDeposit: Boolean,
): String {
    val base = when {
        tinted -> "instanced_textured_tinted_simple"
        instanced -> "instanced_textured_simple"
        else -> "textured_simple"
    }
    return base + (if (includeSurfaceDeposit) "_deposit" else "") + if (allowDiscard) "" else "_solid"
}

@Suppress("LongMethod", "MagicNumber")
private fun simpleLitTextured(
    clipSpace: ClipSpace,
    instanced: Boolean = false,
    tinted: Boolean = false,
    allowDiscard: Boolean = true,
    includeSurfaceDeposit: Boolean = false,
): AslShaderDefinition = shader(simpleLitShaderName(instanced, tinted, allowDiscard, includeSurfaceDeposit)) {
    val matGroup = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = matGroup, binding = 0)
    val handles = u.fieldsFrom(MaterialUniformLayouts.PbrTexturedDraw)
    val frame = frameUniforms()
    val viewProjection = frame.value("viewProjection")
    val lightDirection = frame.value("lightDirection")
    val lightColor = frame.value("lightColor")
    val pointLightPositions = frame.array("pointLightPositions")
    val pointLightColors = frame.array("pointLightColors")
    val model = handles.value("model")
    val cameraPosition = frame.value("cameraPosition")
    val pbrFactors = handles.value("pbrFactors")
    val baseColorFactor = handles.value("baseColorFactor")
    val emissiveFactor = handles.value("emissiveFactor")
    val textureFrames = handles.value("textureFrames")
    val textureScroll = handles.value("textureScroll")
    val fogColor = frame.value("fogColor")
    val fogRange = frame.value("fogRange")
    val surfaceDeposit = surfaceDepositUniforms(frame)
    val drawFade = handles.value("drawFade")
    val debugView = frame.value("debugView")
    val exposure = exposureAndFade(frame, handles)
    val cascadeInputs = CascadeShadowInputs(
        frame.array("cascadeViewProjections"),
        frame.array("cascadeDepthScales"),
        cameraPosition,
        frame.value("cameraForward"),
    )
    val shadowMap by textureDepth2dArray(group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth), binding = 0)
    val shadowMapSampler by samplerComparison(group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth), binding = 1)

    val baseColorTexture by texture2d(group = matGroup, binding = 1)
    val baseColorSampler by sampler(group = matGroup, binding = 2)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val uv by out.varying(GpuDataShape.Vec2, location = 2)
    val worldPos by out.varying(GpuDataShape.Vec3, location = 3)
    val instanceFade = if (tinted && allowDiscard) out.namedVarying("instanceFade", GpuDataShape.Float, location = 4) else null

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val inPosition = ins.input(VertexSemantic.Position)
        val drawModel = if (instanced) instanceModelMatrixAfter(VertexFormat.PositionNormalColorUv) else model
        out.position set (viewProjection * (drawModel * vec4(inPosition, 1f.lit)))
        if (tinted) {
            val tint = let("instanceTint", namedInput("inTint", GpuDataShape.Vec4, SIMPLE_INSTANCE_TINT_LOCATION))
            color set ins.input(VertexSemantic.Color) * tint.xyz
            instanceFade?.let { it set tint.w }
        } else {
            color set ins.input(VertexSemantic.Color)
        }
        normal set (drawModel * vec4(ins.input(VertexSemantic.Normal), 0f.lit)).xyz
        worldPos set (drawModel * vec4(inPosition, 1f.lit)).xyz
        val inUv = ins.input(VertexSemantic.Uv)
        uv set vec2(inUv.x, 1f.lit - inUv.y)
    }

    val ambientStrength = const("AMBIENT_STRENGTH", 0.08f)
    val epsilon = const("EPSILON", 0.0001f)
    val displayTransform = fastSceneDisplayTransform()
    val fastSrgbToLinear = declareFastSrgbToLinear()
    val cascades = cascadeShadowSampling(cascadeInputs, shadowMap, shadowMapSampler, clipSpace, epsilon)
    val depositNoise = if (includeSurfaceDeposit) declareSurfaceDepositNoise() else null

    fragment {
        val (sampleUv, uvDx, uvDy) = animatedTextureUv(uv, textureFrames, textureScroll)
        val baseColorSample = let("baseColorSample", textureSampleGrad(baseColorTexture, baseColorSampler, sampleUv, uvDx, uvDy))
        val sourceAlpha = let("sourceAlpha", baseColorSample.a * baseColorFactor.a)
        val blendCode = let("blendCode", toU32(pbrFactors.w + 0.5f.lit))
        val materialUnlit = blendCode eq TexturedBlend.Unlit.code.toUInt().lit
        val addsUnlit = blendCode eq TexturedBlend.AddsUnlit.code.toUInt().lit
        val noDebugView = toU32(debugView.w + 0.5f.lit) eq 0u.lit
        val skipLit = (materialUnlit or addsUnlit) and noDebugView
        val needsLit = select(1u.lit, 0u.lit, skipLit) eq 1u.lit
        val finalColor = variable("finalColor", vec4(0f.lit))

        iff(skipLit) {
            val fastRgb = variable("fastRgb", vec3(0f.lit))
            iff(materialUnlit) {
                val unlitRgb = variable("unlitRgb", baseColorFactor.rgb * baseColorSample.rgb * color)
                if (includeSurfaceDeposit) {
                    applyUnlitSurfaceDeposit(unlitRgb, normalize(normal), worldPos, cameraPosition, surfaceDeposit, drawFade, simplexFn = depositNoise)
                }
                assign(fastRgb, displayTransform.unlit(unlitRgb))
            }
            iff(addsUnlit) {
                val unlitAlbedo = let("unlitAlbedo", fastSrgbToLinear(baseColorSample.rgb) * color * baseColorFactor.rgb)
                val dist = let("addFogDist", length(cameraPosition.xyz - worldPos))
                val fogT = saturate(fogAmount(dist, fogColor, fogRange))
                assign(fastRgb, mix(displayTransform.display(unlitAlbedo + emissiveFactor.rgb, exposure.x), vec3(0f.lit), fogT))
            }
            assign(finalColor, vec4(fastRgb, sourceAlpha))
        }

        iff(needsLit) {
            val albedo = variable("albedo", fastSrgbToLinear(baseColorSample.rgb) * color * baseColorFactor.rgb)
            val metallic = variable("metallic", clamp(pbrFactors.x, 0f.lit, 1f.lit))
            val roughness = variable("roughness", clamp(pbrFactors.y, 0.08f.lit, 1f.lit))
            val n = variable("n", normalize(normal))
            if (includeSurfaceDeposit) {
                applyLitSurfaceDeposit(albedo, metallic, roughness, n, worldPos, cameraPosition, surfaceDeposit, drawFade, simplexFn = depositNoise)
            }
            val l = let("l", normalize(lightDirection.xyz))
            val nDotL = let("nDotL", max(dot(n, l), 0f.lit))
            val shadowFactor = let("shadowFactor", cascades.sampleShadow(worldPos, n, nDotL))
            val ambientShare = let("ambientShare", select(ambientStrength, lightColor.w, lightColor.w gt 0f.lit))
            val smoothness = let("smoothness", 1f.lit - roughness)
            // Cheap Blinn-Phong specular highlight (3 squarings -> nDotH^16, zero pow/GGX/Smith).
            val v = let("v", normalize(cameraPosition.xyz - worldPos))
            val h = let("h", normalize(v + l))
            val nh = let("nh", max(dot(n, h), 0f.lit))
            val nh2 = let("nh2", nh * nh)
            val nh4 = let("nh4", nh2 * nh2)
            val nh8 = let("nh8", nh4 * nh4)
            val specLobe = let("specLobe", nh8 * nh8 * smoothness * smoothness * 0.25f.lit)
            val specColor = mix(vec3(0.04f.lit), albedo, metallic)
            val diffuseColor = albedo * (1f.lit - metallic * 0.65f.lit)
            val sunLit = (diffuseColor + specColor * specLobe) * lightColor.xyz * nDotL * shadowFactor
            val litColor = variable("lit", albedo * ambientShare + sunLit + emissiveFactor.rgb)

            loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
                val slot = let("slot", pointLightPositions[i])
                iff(slot.w le 0f.lit) { continueLoop() }
                val toLight = let("toLight", slot.xyz - worldPos)
                val distSq = let("distSq", dot(toLight, toLight))
                val rangeSq = let("rangeSq", slot.w * slot.w)
                iff(distSq ge rangeSq) { continueLoop() }
                val dist = let("dist", length(toLight))
                val pNdotL = let("pNdotL", max(dot(n, toLight / max(dist, epsilon)), 0f.lit))
                iff(pNdotL le 0f.lit) { continueLoop() }
                val falloff = let("falloff", saturate(1f.lit - distSq / rangeSq))
                val attenuation = let("attenuation", falloff * falloff / max(distSq, epsilon))
                assign(litColor, litColor + diffuseColor * pointLightColors[i].xyz * pNdotL * attenuation)
            }

            val additive = addsUnlit or (blendCode eq TexturedBlend.AddsLit.code.toUInt().lit)
            val emitted = select(litColor, albedo + emissiveFactor.rgb, addsUnlit)
            val fogTarget = select(fogColor.rgb, vec3(0f.lit), additive)
            val dist = let("fogDist", length(cameraPosition.xyz - worldPos))
            val litShaded = mix(displayTransform.display(emitted, exposure.x), fogTarget, saturate(fogAmount(dist, fogColor, fogRange)))
            val shadedRgb = variable("shadedRgb", litShaded)
            iff(materialUnlit) {
                val stored = variable("debugUnlitRgb", baseColorFactor.rgb * baseColorSample.rgb * color)
                if (includeSurfaceDeposit) {
                    applyUnlitSurfaceDeposit(stored, n, worldPos, cameraPosition, surfaceDeposit, drawFade, simplexFn = depositNoise)
                }
                assign(shadedRgb, displayTransform.unlit(stored))
            }
            val shaded = vec4(shadedRgb, sourceAlpha)
            val surface = DebugSurface(
                n,
                worldPos,
                displayTransform.encoded(albedo),
                shadow = shadowFactor,
                shadowCascade = cascades.shadowCascade(worldPos),
                clay = displayTransform.display(clayRadiance(nDotL, shadowFactor, lightColor.xyz, clayAmbient(lightColor)), exposure.x),
            )
            assign(finalColor, debugViewColor(debugView, cameraPosition, surface, shaded))
        }

        if (allowDiscard) {
            val coverage = materialCoverage(sourceAlpha, pbrFactors.z, finalColor.a)
            ditherFade(if (instanceFade != null) 1f.lit - (1f.lit - exposure.y) * instanceFade else exposure.y)
            colorOutput(vec4(finalColor.rgb, coverage))
        } else {
            colorOutput(finalColor)
        }
    }
}

/**
 * Lean mobile skinned textured shader: 1 albedo texture fetch (`baseColorTexture` only), zero TBN,
 * zero Cook-Torrance GGX/Smith/Fresnel, zero `pow()`, opt-in surface deposit, exact
 * `StandardMaterial` + `SkinnedDrawLayout` binding compatibility.
 */
@Suppress("LongMethod", "MagicNumber")
private fun skinnedSimpleLitTextured(
    allowDiscard: Boolean = true,
    includeSurfaceDeposit: Boolean = false,
): AslShaderDefinition = shader(
    "skinned_textured_simple" + (if (includeSurfaceDeposit) "_deposit" else "") + if (allowDiscard) "" else "_solid",
) {
    val matGroup = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = matGroup, binding = 0)
    val handles = u.fieldsFrom(SkinnedDrawLayout)
    val frame = frameUniforms()
    val viewProjection = frame.value("viewProjection")
    val model = handles.value("model")
    val uniformPalette = handles.array("jointPalette")
    val tint = handles.value("baseColorFactor")
    val glow = handles.value("emissiveFactor")
    val pbrFactors = handles.value("pbrFactors")
    val drawFade = handles.value("drawFade")
    val surfaceDeposit = surfaceDepositUniforms(frame)
    val exposure = exposureAndFade(frame, handles)
    val debug = SkinnedDebugInputs(frame::value)
    val displayTransform = fastSceneDisplayTransform()
    val fastSrgbToLinear = declareFastSrgbToLinear()
    val depositNoise = if (includeSurfaceDeposit) declareSurfaceDepositNoise() else null

    val baseColorTexture by texture2d(group = matGroup, binding = 1)
    val baseColorSampler by sampler(group = matGroup, binding = 2)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val uv by out.varying(GpuDataShape.Vec2, location = 2)
    val debugVaryings = SkinnedDebugVaryings(out, firstLocation = 3)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUvSkin)
        val inPosition = ins.input(VertexSemantic.Position)
        val inNormal = ins.input(VertexSemantic.Normal)
        val joints = ins.input(VertexSemantic.JointIndices)
        val weights = ins.input(VertexSemantic.JointWeights)
        val skinMatrix = let(
            "skinMatrix",
            weights.x * uniformPalette[joints.x] + weights.y * uniformPalette[joints.y] +
                weights.z * uniformPalette[joints.z] + weights.w * uniformPalette[joints.w],
        )
        val position = let("skinnedPosition", skinMatrix * vec4(inPosition, 1f.lit))
        val outNormal = let("skinnedNormal", skinMatrix * vec4(inNormal, 0f.lit))
        val world = let("worldPosition", model * position)
        out.position set (viewProjection * world)
        normal set (model * outNormal).xyz
        debugVaryings.write(this, world, joints, weights, debug.joint)
        color set ins.input(VertexSemantic.Color)
        val inUv = ins.input(VertexSemantic.Uv)
        uv set vec2(inUv.x, 1f.lit - inUv.y)
    }

    val ambient = const("AMBIENT_STRENGTH", 0.35f)
    val epsilon = const("EPSILON", 0.0001f)

    fragment {
        val worldPos = debugVaryings.worldPosition
        val texColor = let("texColor", textureSample(baseColorTexture, baseColorSampler, uv))
        val alpha = let("alpha", texColor.a * tint.w)
        val stored = let("stored", texColor.rgb * color * tint.xyz)
        val cameraPosition = frame.value("cameraPosition")

        val materialUnlit = pbrFactors.w gt SIMPLE_UNLIT_THRESHOLD.lit
        val noDebugView = toU32(debug.view.w + 0.5f.lit) eq 0u.lit
        val skipLit = materialUnlit and noDebugView
        val needsLit = select(1u.lit, 0u.lit, skipLit) eq 1u.lit
        val finalColor = variable("finalColor", vec4(0f.lit))
        val localDepositPos = if (includeSurfaceDeposit) worldPos - (model * vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit)).xyz else worldPos

        iff(skipLit) {
            val unlitRgb = variable("unlitRgb", stored)
            if (includeSurfaceDeposit) {
                applyUnlitSurfaceDeposit(unlitRgb, normalize(normal), localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true, simplexFn = depositNoise)
            }
            assign(finalColor, vec4(displayTransform.unlit(unlitRgb), alpha))
        }

        iff(needsLit) {
            val albedo = variable("albedo", fastSrgbToLinear(texColor.rgb) * color * tint.xyz)
            val metallic = variable("metallic", clamp(pbrFactors.x, 0f.lit, 1f.lit))
            val roughness = variable("roughness", clamp(pbrFactors.y, 0.08f.lit, 1f.lit))
            val n = variable("n", normalize(normal))
            if (includeSurfaceDeposit) {
                applyLitSurfaceDeposit(albedo, metallic, roughness, n, localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true, simplexFn = depositNoise)
            }
            val l = let("l", normalize(frame.value("lightDirection").xyz))
            val v = let("v", normalize(cameraPosition.xyz - worldPos))
            val h = let("h", normalize(v + l))
            val nDotL = let("nDotL", max(dot(n, l), 0f.lit))
            val smoothness = let("smoothness", 1f.lit - roughness)
            val nh = let("nh", max(dot(n, h), 0f.lit))
            val nh2 = let("nh2", nh * nh)
            val nh4 = let("nh4", nh2 * nh2)
            val nh8 = let("nh8", nh4 * nh4)
            val specLobe = let("specLobe", nh8 * nh8 * smoothness * smoothness * 0.25f.lit)
            val specColor = mix(vec3(0.04f.lit), albedo, metallic)
            val diffuseColor = albedo * (1f.lit - metallic * 0.65f.lit)
            val lightColor = frame.value("lightColor")
            val ambientShare = let("ambientShare", select(ambient, lightColor.w, lightColor.w gt 0f.lit))
            val litColor = variable("lit", albedo * ambientShare + (diffuseColor + specColor * specLobe) * lightColor.xyz * nDotL + glow.xyz)

            val pointLights = SkinnedPointLights(frame::array)
            loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
                val slot = let("slot", pointLights.positions[i])
                iff(slot.w le 0f.lit) { continueLoop() }
                val toLight = let("toLight", slot.xyz - worldPos)
                val distSq = let("distSq", dot(toLight, toLight))
                val rangeSq = let("rangeSq", slot.w * slot.w)
                iff(distSq ge rangeSq) { continueLoop() }
                val dist = let("dist", length(toLight))
                val pNdotL = let("pNdotL", max(dot(n, toLight / max(dist, epsilon)), 0f.lit))
                iff(pNdotL le 0f.lit) { continueLoop() }
                val falloff = let("falloff", saturate(1f.lit - distSq / rangeSq))
                val attenuation = let("attenuation", falloff * falloff / max(distSq, epsilon))
                assign(litColor, litColor + diffuseColor * pointLights.colors[i].xyz * pNdotL * attenuation)
            }

            val litShaded = applySceneFog(
                displayTransform.display(litColor, exposure.x),
                cameraPosition,
                worldPos,
                frame.value("fogColor"),
                frame.value("fogRange"),
            )
            val shadedRgb = variable("shadedRgb", litShaded)
            iff(materialUnlit) {
                val unlitReflected = variable("debugUnlitRgb", stored)
                if (includeSurfaceDeposit) {
                    applyUnlitSurfaceDeposit(unlitReflected, n, localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true, simplexFn = depositNoise)
                }
                assign(shadedRgb, displayTransform.unlit(unlitReflected))
            }
            val shaded = vec4(shadedRgb, alpha)
            assign(finalColor, skinnedDebugColor(debug, debugVaryings, n, stored, displayTransform, exposure, shaded))
        }

        if (allowDiscard) {
            val coverage = materialCoverage(alpha, pbrFactors.z, finalColor.w)
            ditherFade(exposure.y)
            colorOutput(vec4(finalColor.xyz, coverage))
        } else {
            colorOutput(finalColor)
        }
    }
}

/** 3-FMA polynomial sRGB-to-linear decode (max error < 0.0005 across `[0, 1]`, zero `pow()` SFU ops). */
@Suppress("MagicNumber")
private fun AslShaderBuilder.declareFastSrgbToLinear() = fn("fastSrgbToLinear", returns = AslType.Data(GpuDataShape.Vec3)) {
    val encoded by param(GpuDataShape.Vec3)
    val c = let("c", max(encoded, vec3(0f.lit)))
    returnValue(c * (c * (c * 0.30530602f.lit + 0.6821711f.lit) + 0.012522878f.lit))
}

/** Lean mobile textured static mesh shader (1 albedo fetch, no TBN/PBR fetches, opt-in deposit). */
fun simpleLitTexturedShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, includeSurfaceDeposit = includeSurfaceDeposit)

/** Discard-free Early-Z companion of [simpleLitTexturedShader]. */
fun simpleLitTexturedSolidShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, allowDiscard = false, includeSurfaceDeposit = includeSurfaceDeposit)

/** Lean mobile instanced textured mesh shader. */
fun instancedSimpleLitTexturedShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, instanced = true, includeSurfaceDeposit = includeSurfaceDeposit)

/** Discard-free Early-Z companion of [instancedSimpleLitTexturedShader]. */
fun instancedSimpleLitTexturedSolidShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, instanced = true, allowDiscard = false, includeSurfaceDeposit = includeSurfaceDeposit)

/** Lean mobile instanced tinted textured mesh shader (`PipelineVariant.InstancedTinted`). */
fun instancedSimpleLitTexturedTintedShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, instanced = true, tinted = true, includeSurfaceDeposit = includeSurfaceDeposit)

/** Discard-free Early-Z companion of [instancedSimpleLitTexturedTintedShader]. */
fun instancedSimpleLitTexturedTintedSolidShader(clipSpace: ClipSpace, includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    simpleLitTextured(clipSpace, instanced = true, tinted = true, allowDiscard = false, includeSurfaceDeposit = includeSurfaceDeposit)

/** Lean mobile skinned textured mesh shader (1 albedo fetch, no TBN/PBR fetches, opt-in deposit). */
fun skinnedSimpleLitTexturedShader(includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    skinnedSimpleLitTextured(includeSurfaceDeposit = includeSurfaceDeposit)

/** Discard-free Early-Z companion of [skinnedSimpleLitTexturedShader]. */
fun skinnedSimpleLitTexturedSolidShader(includeSurfaceDeposit: Boolean = false): AslShaderDefinition =
    skinnedSimpleLitTextured(allowDiscard = false, includeSurfaceDeposit = includeSurfaceDeposit)

private const val SIMPLE_INSTANCE_TINT_LOCATION = 8
private const val SIMPLE_UNLIT_THRESHOLD = 0.5f
