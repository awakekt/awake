/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslArrayHandle
import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.AslVaryings
import com.awakekt.awake.asset.shaderdsl.AslVertexBuilder
import com.awakekt.awake.asset.shaderdsl.F32
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cross
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.dpdx
import com.awakekt.awake.asset.shaderdsl.dpdy
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.instanceModelMatrix
import com.awakekt.awake.asset.shaderdsl.inverseSqrt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.storageArrayOfArrays
import com.awakekt.awake.asset.shaderdsl.texture2d
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
import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.MAX_JOINTS
import com.awakekt.awake.render.renderer.SkinnedDrawLayout

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun meshVariant(
    name: String,
    instanced: Boolean,
    skinned: Boolean,
    allowDiscard: Boolean = true,
): AslShaderDefinition =
    shader(name) {
        val u = uniformBlock(
            "Uniforms",
            group = BindingLayout.Standard.slot(BindingSemantic.Material),
            binding = 0,
        )
        val handles = u.fieldsFrom(if (skinned && !instanced) SkinnedDrawLayout else MaterialUniformLayouts.InstancedDraw)
        val frame = frameUniforms()
        val viewProjection = frame.value("viewProjection")
        val lightDirection = frame.value("lightDirection")
        val lightColor = frame.value("lightColor")
        val cameraPosition = frame.value("cameraPosition")
        val fogColor = frame.value("fogColor")
        val fogRange = frame.value("fogRange")
        val surfaceDeposit = surfaceDepositUniforms(frame)
        val drawFade = handles.value("drawFade")
        val uniformPalette = if (skinned && !instanced) handles.array("jointPalette") else null
        val entityModel = if (skinned && !instanced) handles.value("model") else null
        val tint = if (skinned && !instanced) handles.value("baseColorFactor") else null
        val glow = if (skinned && !instanced) handles.value("emissiveFactor") else null
        val pbrFactors = if (skinned && !instanced) handles.value("pbrFactors") else null
        val exposure = exposureAndFade(frame, handles)
        val pointLights = if (skinned && !instanced) SkinnedPointLights(frame::array) else null
        val debug = if (skinned && !instanced) SkinnedDebugInputs(frame::value) else null
        val displayTransform = sceneDisplayTransform(decodesDisplayReferred = true)
        val storagePalettes = if (skinned && instanced) {
            storageArrayOfArrays(
                structName = "JointPalette",
                varName = "palettes",
                group = BindingLayout.Standard.slot(BindingSemantic.JointPalette),
                binding = 0,
                fieldName = "joints",
                elementShape = GpuDataShape.Mat4,
                elementCount = MAX_JOINTS,
            )
        } else {
            null
        }

        val instanceTints = if (skinned && instanced) skinnedInstanceTints() else null
        val out = varyings("VertexOutput")
        val color by out.varying(if (instanceTints != null) GpuDataShape.Vec4 else GpuDataShape.Vec3, location = 0)
        val normal by out.varying(GpuDataShape.Vec3, location = 1)
        val debugVaryings = debug?.let { SkinnedDebugVaryings(out, firstLocation = 2) }
        val worldPos = if (debugVaryings == null) {
            val worldPos by out.varying(GpuDataShape.Vec3, location = 2)
            worldPos
        } else {
            null
        }

        vertex {
            val instance = if (storagePalettes != null) instanceIndex() else null
            val ins = inputsFrom(if (skinned) VertexFormat.PositionNormalColorSkin else VertexFormat.PositionNormalColor)
            val inPosition = ins.input(VertexSemantic.Position)
            val inNormal = ins.input(VertexSemantic.Normal)

            val position: AslExpr
            val outNormal: AslExpr
            if (skinned) {
                val joints = ins.input(VertexSemantic.JointIndices)
                val weights = ins.input(VertexSemantic.JointWeights)
                fun palette(slot: AslExpr): AslExpr = storagePalettes?.element(instance!!, slot)
                    ?: uniformPalette!![slot]
                val skinMatrix = let(
                    "skinMatrix",
                    weights.x * palette(joints.x) + weights.y * palette(joints.y) +
                        weights.z * palette(joints.z) + weights.w * palette(joints.w),
                )
                position = let("skinnedPosition", skinMatrix * vec4(inPosition, 1f.lit))
                outNormal = let("skinnedNormal", skinMatrix * vec4(inNormal, 0f.lit))
                if (debugVaryings != null) debugVaryings.write(this, entityModel!! * position, joints, weights, debug.joint)
            } else {
                position = vec4(inPosition, 1f.lit)
                outNormal = inNormal
            }

            if (instanced) {
                val model = let("model", instanceModelMatrix(startLocation = if (skinned) 5 else 3))
                val placed = let("placed", model * position)
                out.position set (viewProjection * placed)
                worldPos?.let { it set placed.xyz }
                normal set if (skinned) (model * outNormal).xyz else outNormal
            } else {
                val placed = if (entityModel != null) entityModel * position else position
                out.position set (viewProjection * placed)
                worldPos?.let { it set placed.xyz }
                // The palette poses the mesh in its own space; the entity's model turns its normals
                // into the world the light is in, or the lit side would turn with the mesh.
                normal set if (entityModel != null) (entityModel * outNormal).xyz else outNormal
            }
            color set instanceTintedColor(ins.input(VertexSemantic.Color), instanceTints, instance)
        }

        val ambient = const("AMBIENT_STRENGTH", 0.35f)

        fragment {
            val n = let("n", normalize(normal))
            val l = let("l", normalize(lightDirection.xyz))
            val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
            val ambientShare = let("ambientShare", select(ambient, lightColor.w, lightColor.w gt 0f.lit))
            val shade = variable("shade", sunShade(ambientShare, diffuse, lightColor))
            if (pointLights != null) addPointLights(shade, ambientShare, n, debugVaryings!!.worldPosition, pointLights)
            val rgb = variable("baseRgb", if (instanceTints != null) color.xyz else color)
            val fragWorldPos = debugVaryings?.worldPosition ?: worldPos!!
            val depositPos = if (entityModel != null) fragWorldPos - (entityModel * vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit)).xyz else fragWorldPos
            // Colours here are display-referred, lit as stored; the transform decodes them first.
            val output = if (tint != null && glow != null) {
                val stored = variable("stored", rgb * tint.xyz)
                applyUnlitSurfaceDeposit(stored, n, depositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = skinned)
                val lit = stored * shade
                val v = let("v", normalize(cameraPosition.xyz - fragWorldPos))
                val metallic = let("metallic", clamp(pbrFactors!!.x, 0f.lit, 1f.lit))
                val roughness = let("roughness", clamp(pbrFactors.y, 0.05f.lit, 1f.lit))
                val f0 = let("f0", mix(vec3(0.04f.lit), stored, metallic))
                val envRefl = let(
                    "envReflection",
                    environmentReflection(n, v, l, lightColor, stored, f0, metallic, roughness, glow.w, 1f.lit),
                )
                val litShaded = applySceneFog(
                    displayTransform.displayReferred(lit + envRefl + glow.xyz, exposure.x),
                    cameraPosition,
                    fragWorldPos,
                    fogColor,
                    fogRange,
                )
                val shaded = vec4(select(litShaded, displayTransform.unlit(stored), pbrFactors.w gt UNLIT_THRESHOLD.lit), tint.w)
                skinnedDebugColor(debug!!, debugVaryings!!, n, stored, displayTransform, exposure, shaded)
            } else {
                applyUnlitSurfaceDeposit(rgb, n, depositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = skinned)
                val lit = rgb * shade
                val fogged = applySceneFog(
                    displayTransform.displayReferred(lit, exposure.x),
                    cameraPosition,
                    fragWorldPos,
                    fogColor,
                    fogRange,
                )
                vec4(fogged, if (instanceTints != null) color.w else 1f.lit)
            }
            if (allowDiscard) ditherFade(exposure.y)
            colorOutput(output)
        }
    }

@Suppress("LongMethod")
private fun skinnedTextured(allowDiscard: Boolean = true): AslShaderDefinition =
    shader(if (allowDiscard) "skinned_textured" else "skinned_textured_solid") {
        val u = uniformBlock(
            "Uniforms",
            group = BindingLayout.Standard.slot(BindingSemantic.Material),
            binding = 0,
        )
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
        val displayTransform = sceneDisplayTransform()

        val matGroup = BindingLayout.Standard.slot(BindingSemantic.Material)
        val baseColorTexture by texture2d(group = matGroup, binding = 1)
        val baseColorSampler by sampler(group = matGroup, binding = 2)
        // Match `textured`'s PBR texture bindings (5..8) so glTF skinned meshes read metallic-roughness, normal, occlusion, and emissive maps.
        val metallicRoughnessTexture by texture2d(group = matGroup, binding = 5)
        val normalTexture by texture2d(group = matGroup, binding = 6)
        val occlusionTexture by texture2d(group = matGroup, binding = 7)
        val emissiveTexture by texture2d(group = matGroup, binding = 8)

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
            // Undo createBitmap's OpenGL bottom-up Y flip, as the textured shader does.
            val inUv = ins.input(VertexSemantic.Uv)
            uv set vec2(inUv.x, 1f.lit - inUv.y)
        }

        val ambient = const("AMBIENT_STRENGTH", 0.35f)
        val pi = const("PI", 3.14159265359f)
        val epsilon = const("EPSILON", 0.0001f)
        val dielectricF0 = const("DIELECTRIC_F0", 0.04f)
        val minRoughness = const("MIN_ROUGHNESS", 0.05f)
        val gamma = const("GAMMA", 2.2f)

        val srgbToLinear = fn("srgbToLinear", returns = AslType.Data(GpuDataShape.Vec3)) {
            val encoded by param(GpuDataShape.Vec3)
            returnValue(pow(max(encoded, vec3(0f.lit)), vec3(gamma)))
        }

        val distributionGgx = fn("distributionGgx") {
            val nDotH by param(F32)
            val roughness by param(F32)
            val a = let("a", roughness * roughness)
            val a2 = let("a2", a * a)
            val d = let("d", nDotH * nDotH * (a2 - 1f.lit) + 1f.lit)
            returnValue(a2 / max(pi * d * d, epsilon))
        }

        val geometrySmith = fn("geometrySmith") {
            val nDotV by param(F32)
            val nDotL by param(F32)
            val roughness by param(F32)
            val r = let("r", roughness + 1f.lit)
            val k = let("k", (r * r) / 8f.lit)
            val ggxV = let("ggxV", nDotV / (nDotV * (1f.lit - k) + k))
            val ggxL = let("ggxL", nDotL / (nDotL * (1f.lit - k) + k))
            returnValue(ggxV * ggxL)
        }

        val fresnelSchlick = fn("fresnelSchlick", returns = AslType.Data(GpuDataShape.Vec3)) {
            val cosTheta by param(F32)
            val f0 by param(GpuDataShape.Vec3)
            returnValue(f0 + (vec3(1f.lit) - f0) * pow(clamp(1f.lit - cosTheta, 0f.lit, 1f.lit), 5f.lit))
        }

        val perturbNormal = fn("perturbNormal", returns = AslType.Data(GpuDataShape.Vec3)) {
            val n by param(GpuDataShape.Vec3)
            val dPosDx by param(GpuDataShape.Vec3)
            val dPosDy by param(GpuDataShape.Vec3)
            val dUvDx by param(GpuDataShape.Vec2)
            val dUvDy by param(GpuDataShape.Vec2)
            val tangentNormal by param(GpuDataShape.Vec3)
            val perpDy = let("perpDy", cross(dPosDy, n))
            val perpDx = let("perpDx", cross(n, dPosDx))
            val tangent = let("tangent", perpDy * dUvDx.x + perpDx * dUvDy.x)
            val bitangent = let("bitangent", perpDy * dUvDx.y + perpDx * dUvDy.y)
            val scale = let("scale", max(dot(tangent, tangent), dot(bitangent, bitangent)))
            iff(scale lt epsilon) { returnValue(n) }
            val invMax = let("invMax", inverseSqrt(scale))
            returnValue(
                normalize(
                    (tangent * invMax) * tangentNormal.x + (bitangent * invMax) * tangentNormal.y +
                        n * tangentNormal.z,
                ),
            )
        }

        fragment {
            val worldPos = debugVaryings.worldPosition
            // Compute screen-space derivatives in uniform control flow so unlit skinned draws skip
            // PBR texture fetches, TBN normal perturbation, GGX BRDF, environment reflection, and point lights.
            val dPosDx = let("dPosDx", dpdx(worldPos))
            val dPosDy = let("dPosDy", dpdy(worldPos))
            val dUvDx = let("dUvDx", dpdx(uv))
            val dUvDy = let("dUvDy", dpdy(uv))
            val texColor = let("texColor", textureSampleGrad(baseColorTexture, baseColorSampler, uv, dUvDx, dUvDy))
            val alpha = let("alpha", texColor.a * tint.w)
            val stored = let("stored", texColor.rgb * color * tint.xyz)
            val cameraPosition = frame.value("cameraPosition")

            val materialUnlit = pbrFactors.w gt UNLIT_THRESHOLD.lit
            val noDebugView = toU32(debug.view.w + 0.5f.lit) eq 0u.lit
            val skipLit = materialUnlit and noDebugView
            val needsLit = select(1u.lit, 0u.lit, skipLit) eq 1u.lit
            val finalColor = variable("finalColor", vec4(0f.lit))

            val localDepositPos = worldPos - (model * vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit)).xyz
            iff(skipLit) {
                val unlitRgb = variable("unlitRgb", stored)
                val unlitN = let("unlitN", normalize(normal))
                iff(glow.w gt 0.001f.lit) {
                    val unlitV = let("unlitV", normalize(cameraPosition.xyz - worldPos))
                    assign(unlitRgb, unlitSignedChrome(stored, unlitN, unlitV, glow.w))
                }
                applyUnlitSurfaceDeposit(unlitRgb, unlitN, localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true)
                assign(finalColor, vec4(displayTransform.unlit(unlitRgb), alpha))
            }

            iff(needsLit) {
                val mrSample = let("metallicRoughness", textureSampleGrad(metallicRoughnessTexture, baseColorSampler, uv, dUvDx, dUvDy))
                val tangentNormal = let(
                    "tangentNormal",
                    textureSampleGrad(normalTexture, baseColorSampler, uv, dUvDx, dUvDy).xyz * 2f.lit - vec3(1f.lit),
                )
                val occlusion = let("occlusion", textureSampleGrad(occlusionTexture, baseColorSampler, uv, dUvDx, dUvDy).r)
                val emissiveMask = let(
                    "emissiveMask",
                    srgbToLinear(textureSampleGrad(emissiveTexture, baseColorSampler, uv, dUvDx, dUvDy).rgb),
                )

                val albedo = variable("albedo", srgbToLinear(texColor.rgb) * color * tint.xyz)
                val metallic = variable("metallic", clamp(mrSample.b * pbrFactors.x, 0f.lit, 1f.lit))
                val roughness = variable("roughness", clamp(mrSample.g * pbrFactors.y, minRoughness, 1f.lit))
                val emissive = let("emissive", emissiveMask * glow.xyz)

                val n = variable("n", perturbNormal(normalize(normal), dPosDx, dPosDy, dUvDx, dUvDy, tangentNormal))
                applyLitSurfaceDeposit(albedo, metallic, roughness, n, localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true)
                val l = let("l", normalize(frame.value("lightDirection").xyz))
                val v = let("v", normalize(cameraPosition.xyz - worldPos))
                val h = let("h", normalize(v + l))
                val nDotL = let("nDotL", max(dot(n, l), 0f.lit))
                val nDotV = let("nDotV", max(dot(n, v), epsilon))
                val nDotH = let("nDotH", max(dot(n, h), 0f.lit))
                val f0 = let("f0", mix(vec3(dielectricF0), albedo, metallic))
                val fresnel = let("fresnel", fresnelSchlick(max(dot(h, v), 0f.lit), f0))
                val specular = let(
                    "specular",
                    (distributionGgx(nDotH, roughness) * geometrySmith(nDotV, nDotL, roughness) * fresnel) /
                        max(4f.lit * nDotV * nDotL, epsilon),
                )
                val diffuse = let("diffuse", (vec3(1f.lit) - fresnel) * (1f.lit - metallic) * albedo / pi)
                val lightColor = frame.value("lightColor")
                val radiance = let("radiance", lightColor.xyz * pi * nDotL)
                val ambientShare = let("ambientShare", select(ambient, lightColor.w, lightColor.w gt 0f.lit))
                val envRefl = let(
                    "envReflection",
                    environmentReflection(n, v, l, lightColor, albedo, f0, metallic, roughness, glow.w, occlusion),
                )
                val litColor = variable("lit", albedo * ambientShare * occlusion + (diffuse + specular) * radiance + envRefl + emissive)

                val pointLights = SkinnedPointLights(frame::array)
                loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
                    val slot = let("slot", pointLights.positions[i])
                    iff(slot.w le 0f.lit) { continueLoop() }
                    val toLight = let("toLight", slot.xyz - worldPos)
                    val dist = let("dist", length(toLight))
                    iff(dist ge slot.w) { continueLoop() }
                    val pl = let("pl", toLight / max(dist, epsilon))
                    val pNdotL = let("pNdotL", max(dot(n, pl), 0f.lit))
                    iff(pNdotL le 0f.lit) { continueLoop() }
                    val falloff = let("falloff", clamp(1f.lit - (dist * dist) / (slot.w * slot.w), 0f.lit, 1f.lit))
                    val attenuation = let("attenuation", falloff * falloff / max(dist * dist, epsilon))
                    val ph = let("ph", normalize(v + pl))
                    val pNdotH = let("pNdotH", max(dot(n, ph), 0f.lit))
                    val pFresnel = let("pFresnel", fresnelSchlick(max(dot(ph, v), 0f.lit), f0))
                    val pSpecular = let(
                        "pSpecular",
                        (distributionGgx(pNdotH, roughness) * geometrySmith(nDotV, pNdotL, roughness) * pFresnel) /
                            max(4f.lit * nDotV * pNdotL, epsilon),
                    )
                    val pDiffuse = let("pDiffuse", (vec3(1f.lit) - pFresnel) * (1f.lit - metallic) * albedo / pi)
                    val pRadiance = let("pRadiance", pointLights.colors[i].xyz * pi * pNdotL * attenuation)
                    assign(litColor, litColor + (pDiffuse + pSpecular) * pRadiance)
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
                    val unlitReflected = variable("debugUnlitRgb", unlitSignedChrome(stored, n, v, glow.w))
                    applyUnlitSurfaceDeposit(unlitReflected, n, localDepositPos, cameraPosition, surfaceDeposit, drawFade, characterScale = true)
                    assign(shadedRgb, displayTransform.unlit(unlitReflected))
                }
                val shaded = vec4(shadedRgb, alpha)
                val debugColor = let("debugColor", skinnedDebugColor(debug, debugVaryings, n, stored, displayTransform, exposure, shaded))
                assign(finalColor, debugColor)
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

private const val UNLIT_THRESHOLD = 0.5f

/**
 * How lit a surface is under the scene's sun: [ambient], plus the sun's [lightColor] on the rest by
 * its [diffuse] cosine. Only the sun's own light takes its colour, so a scene without a sun, whose
 * colour is black, keeps the ambient share.
 */
private fun sunShade(ambient: AslExpr, diffuse: AslExpr, lightColor: AslExpr): AslExpr =
    ambient + (1f.lit - ambient) * diffuse * lightColor.xyz

/** The single-draw skinned block's point-light slots: position with range in `w`, and colour. */
private class SkinnedPointLights(array: (String) -> AslArrayHandle) {
    val positions = array("pointLightPositions")
    val colors = array("pointLightColors")
}

/**
 * Adds the scene's point [lights] to [shade] as [sunShade] adds the sun: each on the share [ambient]
 * leaves, by its cosine at [worldPosition] and the windowed inverse-square falloff the lit shaders
 * use, which reaches zero at its range. A slot with no range is off.
 */
private fun AslBlockBuilder.addPointLights(shade: AslExpr, ambient: AslExpr, normal: AslExpr, worldPosition: AslExpr, lights: SkinnedPointLights) {
    loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
        val slot = let("slot", lights.positions[i])
        iff(slot.w le 0f.lit) { continueLoop() }
        val toLight = let("toLight", slot.xyz - worldPosition)
        val dist = let("dist", length(toLight))
        iff(dist ge slot.w) { continueLoop() }
        val pNdotL = let("pNdotL", max(dot(normal, toLight / max(dist, POINT_EPSILON.lit)), 0f.lit))
        val falloff = let("falloff", clamp(1f.lit - (dist * dist) / (slot.w * slot.w), 0f.lit, 1f.lit))
        val attenuation = let("attenuation", falloff * falloff / max(dist * dist, POINT_EPSILON.lit))
        assign(shade, shade + (1f.lit - ambient) * pNdotL * attenuation * lights.colors[i].xyz)
    }
}

private const val POINT_EPSILON = 0.0001f

/** What the frame block holds for the skinned debug views: the scene's sun, the eye, the view and the joint. */
private class SkinnedDebugInputs(value: (String) -> AslExpr) {
    val lightDirection = value("lightDirection")
    val lightColor = value("lightColor")
    val cameraPosition = value("cameraPosition")
    val view = value("debugView")
    val joint = value("exposure").y
}

/** The varyings the debug views read, from [firstLocation] on: world position, joint colour and the selected joint's weight. */
private class SkinnedDebugVaryings(out: AslVaryings, firstLocation: Int) {
    val worldPosition by out.varying(GpuDataShape.Vec3, location = firstLocation)
    val jointColor by out.varying(GpuDataShape.Vec3, location = firstLocation + 1)
    val jointWeight by out.varying(GpuDataShape.Float, location = firstLocation + 2)

    /** Sets them from the posed vertex, [world] being it in the world as a `vec4`. */
    fun write(stage: AslVertexBuilder, world: AslExpr, joints: AslExpr, weights: AslExpr, joint: AslExpr) = with(stage) {
        worldPosition set world.xyz
        jointColor set jointWeightsColor(joints, weights)
        jointWeight set selectedJointWeight(joints, weights, joint)
    }
}

/**
 * [shaded], or the debug view of this skinned fragment: [albedo] is its colour unlit, as stored, and
 * clay is lit by the scene's sun. These shaders sample no shadow, so clay's sun is unshadowed.
 */
@Suppress("LongParameterList") // The fragment's own values; the inputs and varyings are grouped already.
private fun AslBlockBuilder.skinnedDebugColor(
    inputs: SkinnedDebugInputs,
    varyings: SkinnedDebugVaryings,
    normal: AslExpr,
    albedo: AslExpr,
    displayTransform: SceneDisplayTransform,
    exposure: AslExpr,
    shaded: AslExpr,
): AslExpr {
    val sun = inputs.lightDirection.xyz
    // A zero direction is no sun: its cosine is 0 rather than a normalized zero.
    val clayNDotL = let("clayNDotL", max(dot(normal, sun), 0f.lit) / max(length(sun), CLAY_SUN_EPSILON.lit))
    val surface = DebugSurface(
        normal = normal,
        worldPosition = varyings.worldPosition,
        albedo = albedo,
        clay = displayTransform.display(clayRadiance(clayNDotL, 1f.lit, inputs.lightColor.xyz, clayAmbient(inputs.lightColor)), exposure.x),
        jointWeights = varyings.jointColor,
        selectedJointWeight = varyings.jointWeight,
    )
    val out = debugViewColor(inputs.view, inputs.cameraPosition, surface, shaded)
    // Keep the material's alpha: a masked or blended skin stays so under every view.
    return vec4(out.xyz, shaded.w)
}

private const val CLAY_SUN_EPSILON = 0.0001f

/** Shader definition for instanced static mesh rendering with lighting and shadows. */
val InstancedShader: AslShaderDefinition = meshVariant("instanced", instanced = true, skinned = false)

/** Shader definition for skinned skeletal mesh rendering with lighting and shadows. */
val SkinnedShader: AslShaderDefinition = meshVariant("skinned", instanced = false, skinned = true)

/** Discard-free opaque companion of [SkinnedShader]. */
val SkinnedSolidShader: AslShaderDefinition = meshVariant("skinned_solid", instanced = false, skinned = true, allowDiscard = false)

/** Shader definition for skinned skeletal mesh rendering with diffuse texturing and shadows. */
val SkinnedTexturedShader: AslShaderDefinition = skinnedTextured()

/** Discard-free opaque companion of [SkinnedTexturedShader]. */
val SkinnedTexturedSolidShader: AslShaderDefinition = skinnedTextured(allowDiscard = false)

/** Shader definition for instanced and skinned skeletal mesh rendering with shadows. */
val SkinnedInstancedShader: AslShaderDefinition =
    meshVariant("skinned_instanced", instanced = true, skinned = true)
