/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.AslType
import io.github.awakelab.awake.asset.shaderdsl.F32
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.b
import io.github.awakelab.awake.asset.shaderdsl.clamp
import io.github.awakelab.awake.asset.shaderdsl.cross
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.dpdx
import io.github.awakelab.awake.asset.shaderdsl.dpdy
import io.github.awakelab.awake.asset.shaderdsl.exp
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.g
import io.github.awakelab.awake.asset.shaderdsl.ge
import io.github.awakelab.awake.asset.shaderdsl.inputsFrom
import io.github.awakelab.awake.asset.shaderdsl.inverseSqrt
import io.github.awakelab.awake.asset.shaderdsl.le
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.lt
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.normalize
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.pow
import io.github.awakelab.awake.asset.shaderdsl.r
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.texture2d
import io.github.awakelab.awake.asset.shaderdsl.textureSample
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec3
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.asset.shaderdsl.z
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic
import io.github.awakelab.awake.render.passes.uniforms.MaterialUniformLayouts
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS

/**
 * glTF metallic-roughness PBR for `PositionNormalColorUv` -- same Cook-Torrance BRDF as
 * lit_shadow, reading metallic/roughness/normal/occlusion/emissive from textures, no shadow
 * map. A material with no map binds a 1x1 neutral placeholder, so all five sample
 * unconditionally. The uniform struct derives from [MaterialUniformLayouts.PbrTextured] --
 * whose field is named `pbrFactors`; the hand-written file drifted to `material`, and the
 * derivation is what ends that class of mismatch. Bindings 3/4 are the shadow map in the
 * shared descriptor-set layout, left unused here so numbering stays stable across shaders.
 *
 * The TBN basis is reconstructed per-pixel from screen-space derivatives (no TANGENT
 * attribute exists on this path); the `mat3 * v` of the hand-written file is expanded to its
 * column sum, which is the same arithmetic without needing a mat3 type in ASL. Ceiling and
 * upgrade path unchanged: MikkTSpace tangents at load time plus a tangent vertex slot.
 */
@Suppress("LongMethod")
private fun textured(): AslShaderDefinition = shader("textured") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(MaterialUniformLayouts.PbrTextured)
    val mvp = handles.value("mvp")
    val lightDirection = handles.value("lightDirection")
    val lightColor = handles.value("lightColor")
    val pointLightPositions = handles.array("pointLightPositions")
    val pointLightColors = handles.array("pointLightColors")
    val model = handles.value("model")
    val cameraPosition = handles.value("cameraPosition")
    val pbrFactors = handles.value("pbrFactors")
    val baseColorFactor = handles.value("baseColorFactor")
    val emissiveFactor = handles.value("emissiveFactor")
    val fogColor = handles.value("fogColor")

    val baseColorTexture by texture2d(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 1,
    )
    // One sampler for every material texture -- all five sample the same uv with the same
    // filtering, so per-map samplers would only double the binding count.
    val baseColorSampler by sampler(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 2)
    val metallicRoughnessTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 5)
    val normalTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 6)
    val occlusionTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 7)
    val emissiveTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 8)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val uv by out.varying(GpuDataShape.Vec2, location = 2)
    val worldPos by out.varying(GpuDataShape.Vec3, location = 3)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val inPosition = ins.input(VertexSemantic.Position)
        out.position set (mvp * vec4(inPosition, 1f.lit))
        color set ins.input(VertexSemantic.Color)
        // World-space normal (model matrix directly -- correct for the rigid/uniform scales
        // Transform can express), so shading stays put while the mesh spins.
        normal set (model * vec4(ins.input(VertexSemantic.Normal), 0f.lit)).xyz
        worldPos set (model * vec4(inPosition, 1f.lit)).xyz
        // Undo createBitmap's OpenGL bottom-up Y flip here rather than forking the decoder.
        val inUv = ins.input(VertexSemantic.Uv)
        uv set vec2(inUv.x, 1f.lit - inUv.y)
    }

    val ambientStrength = const("AMBIENT_STRENGTH", 0.08f)
    val pi = const("PI", 3.14159265359f)
    val epsilon = const("EPSILON", 0.0001f)
    val dielectricF0 = const("DIELECTRIC_F0", 0.04f)
    val minRoughness = const("MIN_ROUGHNESS", 0.05f)

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

    // Screen-space TBN; degenerate uv (collapsed island, flat placeholder) falls back to the
    // geometric normal.
    val perturbNormal = fn("perturbNormal", returns = AslType.Data(GpuDataShape.Vec3)) {
        val n by param(GpuDataShape.Vec3)
        val pos by param(GpuDataShape.Vec3)
        val surfaceUv by param(GpuDataShape.Vec2)
        val tangentNormal by param(GpuDataShape.Vec3)
        val dPosDx = let("dPosDx", dpdx(pos))
        val dPosDy = let("dPosDy", dpdy(pos))
        val dUvDx = let("dUvDx", dpdx(surfaceUv))
        val dUvDy = let("dUvDy", dpdy(surfaceUv))
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

    val applyFog = fn("applyFog", returns = AslType.Data(GpuDataShape.Vec3)) {
        val baseColor by param(GpuDataShape.Vec3)
        val pos by param(GpuDataShape.Vec3)
        val dist = let("dist", length(cameraPosition.xyz - pos))
        val fogAmount = let("fogAmount", 1f.lit - exp(-fogColor.a * dist))
        returnValue(mix(baseColor, fogColor.rgb, saturate(fogAmount)))
    }

    fragment {
        val baseColorSample = let("baseColorSample", textureSample(baseColorTexture, baseColorSampler, uv))
        val albedo = let("albedo", baseColorSample.rgb * color * baseColorFactor.rgb)
        // glTF convention: G = roughness, B = metalness; factor * texture channel.
        val metallicRoughness =
            let("metallicRoughness", textureSample(metallicRoughnessTexture, baseColorSampler, uv))
        val metallic = let("metallic", clamp(metallicRoughness.b * pbrFactors.x, 0f.lit, 1f.lit))
        val roughness = let("roughness", clamp(metallicRoughness.g * pbrFactors.y, minRoughness, 1f.lit))
        val occlusion = let("occlusion", textureSample(occlusionTexture, baseColorSampler, uv).r)
        val emissive =
            let("emissive", textureSample(emissiveTexture, baseColorSampler, uv).rgb * emissiveFactor.rgb)
        val tangentNormal =
            let("tangentNormal", textureSample(normalTexture, baseColorSampler, uv).xyz * 2f.lit - vec3(1f.lit))

        val n = let("n", perturbNormal(normalize(normal), worldPos, uv, tangentNormal))
        val l = let("l", normalize(lightDirection.xyz))
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
        // lightColor is authored as reflectance, not radiance -- pay back the BRDF's 1/PI.
        val radiance = let("radiance", lightColor.xyz * pi * nDotL)
        val specularOut = let("specularOut", specular * radiance)
        val ambient = let("ambient", albedo * ambientStrength * occlusion)
        val litColor = variable(
            "lit",
            ambient + diffuse * radiance + specularOut / (specularOut + vec3(1f.lit)) + emissive,
        )
        // Point lights: unrolled over fixed slots; a disabled slot is one compare.
        loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
            val slot = let("slot", pointLightPositions[i])
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
            val pRadiance = let("pRadiance", pointLightColors[i].xyz * pi * pNdotL * attenuation)
            val pSpecularOut = let("pSpecularOut", pSpecular * pRadiance)
            assign(litColor, litColor + pDiffuse * pRadiance + pSpecularOut / (pSpecularOut + vec3(1f.lit)))
        }
        colorOutput(vec4(applyFog(litColor, worldPos), baseColorSample.a * baseColorFactor.a))
    }
}

val TexturedShader: AslShaderDefinition = textured()
