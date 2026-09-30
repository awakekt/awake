/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.F32
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cross
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.dpdx
import com.awakekt.awake.asset.shaderdsl.dpdy
import com.awakekt.awake.asset.shaderdsl.exp
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.inputsFrom
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
import com.awakekt.awake.asset.shaderdsl.samplerComparison
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDepth2dArray
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.textureSampleGrad
import com.awakekt.awake.asset.shaderdsl.times
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
import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * glTF metallic-roughness PBR for `PositionNormalColorUv` -- same Cook-Torrance BRDF and cascaded
 * sun shadow as lit_shadow, reading metallic/roughness/normal/occlusion/emissive from textures.
 * A material with no map binds a 1x1 neutral placeholder, so all five sample
 * unconditionally. The uniform struct derives from [MaterialUniformLayouts.PbrTextured] --
 * whose field is named `pbrFactors`; the hand-written file drifted to `material`, and the
 * derivation is what ends that class of mismatch. Material bindings 3/4 stay unused so numbering
 * matches the other shaders; the shadow map is the engine's `ShadowDepth` group.
 *
 * The TBN basis is reconstructed per-pixel from screen-space derivatives (no TANGENT
 * attribute exists on this path); the `mat3 * v` of the hand-written file is expanded to its
 * column sum, which is the same arithmetic without needing a mat3 type in ASL. Ceiling and
 * upgrade path unchanged: MikkTSpace tangents at load time plus a tangent vertex slot.
 */
@Suppress("LongMethod")
private fun textured(clipSpace: ClipSpace, instanced: Boolean = false): AslShaderDefinition =
    shader(if (instanced) "instanced_textured" else "textured") {
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
    val textureFrames = handles.value("textureFrames")
    val textureScroll = handles.value("textureScroll")
    val fogColor = handles.value("fogColor")
    val debugView = handles.value("debugView")
    val cascadeInputs = CascadeShadowInputs(
        handles.array("cascadeViewProjections"),
        handles.array("cascadeDepthScales"),
        cameraPosition,
        handles.value("cameraForward"),
    )
    val shadowMap by textureDepth2dArray(group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth), binding = 0)
    val shadowMapSampler by samplerComparison(group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth), binding = 1)

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
        // Instanced, each copy's model comes from the instance buffer and `mvp` holds only the
        // view-projection; otherwise `mvp` already includes this draw's model.
        val drawModel = if (instanced) instanceModelMatrixAfter(VertexFormat.PositionNormalColorUv) else model
        out.position set (mvp * if (instanced) drawModel * vec4(inPosition, 1f.lit) else vec4(inPosition, 1f.lit))
        color set ins.input(VertexSemantic.Color)
        // World-space normal (model matrix directly -- correct for the rigid/uniform scales
        // Transform can express), so shading stays put while the mesh spins.
        normal set (drawModel * vec4(ins.input(VertexSemantic.Normal), 0f.lit)).xyz
        worldPos set (drawModel * vec4(inPosition, 1f.lit)).xyz
        // Undo createBitmap's OpenGL bottom-up Y flip here rather than forking the decoder.
        val inUv = ins.input(VertexSemantic.Uv)
        uv set vec2(inUv.x, 1f.lit - inUv.y)
    }

    val ambientStrength = const("AMBIENT_STRENGTH", 0.08f)
    val pi = const("PI", 3.14159265359f)
    val epsilon = const("EPSILON", 0.0001f)
    val dielectricF0 = const("DIELECTRIC_F0", 0.04f)
    val minRoughness = const("MIN_ROUGHNESS", 0.05f)
    val gamma = const("GAMMA", 2.2f)
    val invGamma = const("INV_GAMMA", 1.0f / 2.2f)

    // glTF stores base colour and emissive as sRGB; lighting needs them linear.
    val srgbToLinear = fn("srgbToLinear", returns = AslType.Data(GpuDataShape.Vec3)) {
        val encoded by param(GpuDataShape.Vec3)
        returnValue(pow(max(encoded, vec3(0f.lit)), vec3(gamma)))
    }

    // Targets are UNORM and take sRGB-encoded colour, as lit_shadow writes it.
    val linearToSrgb = fn("linearToSrgb", returns = AslType.Data(GpuDataShape.Vec3)) {
        val linear by param(GpuDataShape.Vec3)
        returnValue(pow(max(linear, vec3(0f.lit)), vec3(invGamma)))
    }
    val cascades = cascadeShadowSampling(cascadeInputs, shadowMap, shadowMapSampler, clipSpace, epsilon)

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
        val (sampleUv, uvDx, uvDy) = animatedTextureUv(uv, textureFrames, textureScroll)
        val baseColorSample = let("baseColorSample", textureSampleGrad(baseColorTexture, baseColorSampler, sampleUv, uvDx, uvDy))
        val albedo = let("albedo", srgbToLinear(baseColorSample.rgb) * color * baseColorFactor.rgb)
        // glTF convention: G = roughness, B = metalness; factor * texture channel.
        val metallicRoughness =
            let("metallicRoughness", textureSampleGrad(metallicRoughnessTexture, baseColorSampler, sampleUv, uvDx, uvDy))
        val metallic = let("metallic", clamp(metallicRoughness.b * pbrFactors.x, 0f.lit, 1f.lit))
        val roughness = let("roughness", clamp(metallicRoughness.g * pbrFactors.y, minRoughness, 1f.lit))
        val occlusion = let("occlusion", textureSampleGrad(occlusionTexture, baseColorSampler, sampleUv, uvDx, uvDy).r)
        val emissive =
            let("emissive", srgbToLinear(textureSampleGrad(emissiveTexture, baseColorSampler, sampleUv, uvDx, uvDy).rgb) * emissiveFactor.rgb)
        val tangentNormal =
            let("tangentNormal", textureSampleGrad(normalTexture, baseColorSampler, sampleUv, uvDx, uvDy).xyz * 2f.lit - vec3(1f.lit))

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
        val shadowFactor = let("shadowFactor", cascades.sampleShadow(worldPos, n, nDotL))
        // lightColor is authored as reflectance, not radiance -- pay back the BRDF's 1/PI.
        val radiance = let("radiance", lightColor.xyz * pi * nDotL * shadowFactor)
        val specularOut = let("specularOut", specular * radiance)
        // The scene's ambient when it sets one (lightColor.w above 0), this shader's otherwise.
        val ambientShare = let("ambientShare", select(ambientStrength, lightColor.w, lightColor.w gt 0f.lit))
        val ambient = let("ambient", albedo * ambientShare * occlusion)
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
        val shaded = vec4(applyFog(linearToSrgb(litColor), worldPos), baseColorSample.a * baseColorFactor.a)
        val surface = DebugSurface(n, worldPos, linearToSrgb(albedo), shadow = shadowFactor, shadowCascade = cascades.shadowCascade(worldPos))
        // A masked material's cut-out, last: after it no derivative may follow. An opaque material's
        // cutoff is 0, so its texture alpha is ignored.
        discardIf(baseColorSample.a * baseColorFactor.a lt pbrFactors.z)
        colorOutput(debugViewColor(debugView, cameraPosition, surface, shaded))
    }
}

/** `textured` for [clipSpace]: the shadow lookup's V axis follows the backend, as in lit_shadow. */
fun texturedShader(clipSpace: ClipSpace): AslShaderDefinition = textured(clipSpace)

/** `textured` drawing many copies of one mesh, each placed by its own instance matrix. */
fun instancedTexturedShader(clipSpace: ClipSpace): AslShaderDefinition = textured(clipSpace, instanced = true)

/**
 * The UV to sample a textured material at, and its screen derivatives, after the material's
 * texture animation (`textureFrames`, `textureScroll`; see `TextureAnimation`).
 *
 * The scroll and the frame sheet work in the image's own UV space, where V runs down the image:
 * the vertex stage flipped V for the bitmap decoder, so it is flipped back here and again at the
 * end. Frames run in reading order. A frame wraps the UV into its cell with `fract`, which jumps
 * inside a primitive, so the derivatives come from the unwrapped UV scaled to the cell; a 1 x 1
 * sheet skips the wrap entirely and samples exactly as a still texture.
 */
private fun AslBlockBuilder.animatedTextureUv(uv: AslExpr, frames: AslExpr, scroll: AslExpr): Triple<AslExpr, AslExpr, AslExpr> {
    val time = scroll.z
    val imageUv = let("imageUv", vec2(uv.x, 1f.lit - uv.y) + scroll.xy * time)
    val sheet = let("sheet", frames.xy)
    val played = let("played", floor(time * frames.z))
    val frame = let("frame", played - frames.w * floor(played / frames.w))
    val cell = let("cell", vec2(frame - sheet.x * floor(frame / sheet.x), floor(frame / sheet.x)))
    val inCell = let("inCell", (fract(imageUv) + cell) / sheet)
    val animated = let("animated", select(imageUv, inCell, (sheet.x * sheet.y) gt 1.5f.lit))
    return Triple(
        let("sampleUv", vec2(animated.x, 1f.lit - animated.y)),
        let("uvDx", dpdx(uv) / sheet),
        let("uvDy", dpdy(uv) / sheet),
    )
}
