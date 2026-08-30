/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.AslExpr
import io.github.awakelab.awake.asset.shaderdsl.AslType
import io.github.awakelab.awake.asset.shaderdsl.AslVertexBuilder
import io.github.awakelab.awake.asset.shaderdsl.F32
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.clamp
import io.github.awakelab.awake.asset.shaderdsl.cos
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.exp
import io.github.awakelab.awake.asset.shaderdsl.ge
import io.github.awakelab.awake.asset.shaderdsl.gt
import io.github.awakelab.awake.asset.shaderdsl.inputsFrom
import io.github.awakelab.awake.asset.shaderdsl.le
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.lt
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.normalize
import io.github.awakelab.awake.asset.shaderdsl.or
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.pow
import io.github.awakelab.awake.asset.shaderdsl.r
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.sin
import io.github.awakelab.awake.asset.shaderdsl.select
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.textureDepth2d
import io.github.awakelab.awake.asset.shaderdsl.textureDimensions
import io.github.awakelab.awake.asset.shaderdsl.textureSampleLevelDepth
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.toF32
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec3
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.w
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.xy
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.asset.shaderdsl.z
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic
import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS

/** Depth-only shadow-map pre-pass: binds lit_shadow's buffer (hence the full prefix struct),
 * reads only lightMvp, writes no color -- depth comes from the fixed-function pipeline. The
 * unused inputs keep the vertex layout identical to the main pass. */
val ShadowDepthShader: AslShaderDefinition = shader("shadow_depth") {
    val u = shadowUniforms(includeLitTail = false)
    vertex { returnPosition(u.lightMvp * vec4(animatedPosition(u), 1f.lit)) }
    fragment { }
}

/**
 * The same depth-only pass rendered from the camera instead of the light -- the frame's own
 * depth, for a later pass that needs to know what is already in front of it (water, soft
 * particles, depth fog).
 *
 * Binding-compatible with [ShadowDepthShader] because it reads the same uniform buffer; only the
 * matrix differs. Sharing [animatedPosition] is not tidiness: depth that disagrees with the
 * scene pass by even one vertex displacement reads as geometry floating above or sinking into
 * itself, and two hand-copied animation blocks is exactly how that drift arrives.
 */
val SceneDepthShader: AslShaderDefinition = shader("scene_depth") {
    val u = shadowUniforms(includeLitTail = false)
    vertex { returnPosition(u.mvp * vec4(animatedPosition(u), 1f.lit)) }
    fragment { }
}

/** PBR + shadow-mapped variant of the triangle shader, live on BOTH backends. The map is
 * declared `texture_depth_2d` (not `texture_2d<f32>`): WebGPU's auto layout derives a
 * filterable-float binding from the float spelling, which a Depth32Float view fails
 * validation against, while the depth spelling is legal on Vulkan too -- one shared source.
 * Manual PCF because the Vulkan JNI sampler binding has no compareEnable/compareOp;
 * explicit-LOD sampling because implicit derivatives inside a loop are undefined control
 * flow. */
@Suppress("LongMethod")
private fun litShadow(): AslShaderDefinition = shader("lit_shadow") {
    val u = shadowUniforms(includeLitTail = true)
    val shadowMap by textureDepth2d(
        group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth),
        binding = 0,
    )
    val shadowMapSampler by sampler(
        group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth),
        binding = 1,
    )

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val shadowPos by out.varying(GpuDataShape.Vec4, location = 2)
    val worldPos by out.varying(GpuDataShape.Vec3, location = 3)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColor)
        val inPosition = ins.input(VertexSemantic.Position)
        val wavelength = max(u.vertexAnimation.y, 0.0001f.lit)
        val phase = u.vertexAnimation.w * u.vertexAnimation.z
        val diagonal = (inPosition.x + inPosition.z) / wavelength + phase
        val cross = (inPosition.x - inPosition.z) / wavelength * 0.7f.lit + phase * 0.8f.lit
        val wave = (sin(diagonal) + cos(cross)) * u.vertexAnimation.x * 0.5f.lit
        val animatedPosition = vec3(inPosition.x, inPosition.y + wave, inPosition.z)
        val dX = (cos(diagonal) - sin(cross) * 0.7f.lit) * u.vertexAnimation.x * 0.5f.lit / wavelength
        val dZ = (cos(diagonal) + sin(cross) * 0.7f.lit) * u.vertexAnimation.x * 0.5f.lit / wavelength
        val animatedNormal = normalize(vec3(-dX, 1f.lit, -dZ))
        out.position set (u.mvp * vec4(animatedPosition, 1f.lit))
        color set ins.input(VertexSemantic.Color)
        normal set (u.model!! * vec4(animatedNormal, 0f.lit)).xyz
        shadowPos set (u.lightMvp * vec4(animatedPosition, 1f.lit))
        worldPos set (u.model * vec4(animatedPosition, 1f.lit)).xyz
    }

    val ambientStrength = const("AMBIENT_STRENGTH", 0.08f)
    val pi = const("PI", 3.14159265359f)
    val epsilon = const("EPSILON", 0.0001f)
    val dielectricF0 = const("DIELECTRIC_F0", 0.04f)
    val minRoughness = const("MIN_ROUGHNESS", 0.05f)
    val invGamma = const("INV_GAMMA", 1.0f / 2.2f)
    // Slope-scaled bias: grazing angles need more, face-on contact shadows need less.
    val shadowBiasMin = const("SHADOW_BIAS_MIN", 0.0015f)
    val shadowBiasMax = const("SHADOW_BIAS_MAX", 0.0090f)
    val pcfRadius = constI32("PCF_RADIUS", 1)

    val sampleShadow = fn("sampleShadow") {
        val pos by param(GpuDataShape.Vec4)
        val nDotL by param(F32)
        iff(pos.w le 0f.lit) { returnValue(1f.lit) }
        // NDC depth is already 0..1 (ClipSpace.depthZeroToOne) -- no *0.5+0.5 remap.
        val ndc = let("ndc", pos.xyz / pos.w)
        val uv = let("uv", ndc.xy * vec2(0.5f.lit, 0.5f.lit) + vec2(0.5f.lit, 0.5f.lit))
        iff(
            (uv.x lt 0f.lit) or (uv.x gt 1f.lit) or (uv.y lt 0f.lit) or (uv.y gt 1f.lit) or
                (ndc.z lt 0f.lit) or (ndc.z gt 1f.lit),
        ) { returnValue(1f.lit) }
        val bias = let("bias", max(shadowBiasMax * (1f.lit - nDotL), shadowBiasMin))
        val texSize = let("texSize", vec2(textureDimensions(shadowMap)))
        val texel = let("texel", 1f.lit / texSize)
        val shadow = variable("shadow", 0f.lit)
        val samples = variable("samples", 0f.lit)
        loopI32("dx", -pcfRadius, pcfRadius) { dx ->
            loopI32("dy", -pcfRadius, pcfRadius) { dy ->
                val offset = let("offset", vec2(toF32(dx), toF32(dy)) * texel)
                // Depth form returns f32 directly, and WGSL wants an INTEGER level here.
                val closestDepth = let(
                    "closestDepth",
                    textureSampleLevelDepth(shadowMap, shadowMapSampler, uv + offset, 0.lit),
                )
                assign(shadow, shadow + select(1f.lit, 0f.lit, (ndc.z - bias) gt closestDepth))
                assign(samples, samples + 1f.lit)
            }
        }
        returnValue(shadow / samples)
    }

    // GGX/Trowbridge-Reitz normal distribution.
    val distributionGgx = fn("distributionGgx") {
        val nDotH by param(F32)
        val roughness by param(F32)
        val a = let("a", roughness * roughness)
        val a2 = let("a2", a * a)
        val d = let("d", nDotH * nDotH * (a2 - 1f.lit) + 1f.lit)
        returnValue(a2 / max(pi * d * d, epsilon))
    }

    // Smith geometry term with Schlick-GGX.
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

    // Gamma 2.2, not exact sRGB -- matches the rest of the pipeline's colour (im)precision.
    val linearToSrgb = fn("linearToSrgb", returns = AslType.Data(GpuDataShape.Vec3)) {
        val linear by param(GpuDataShape.Vec3)
        returnValue(pow(max(linear, vec3(0f.lit)), vec3(invGamma)))
    }

    // Exponential distance fog; density 0 leaves the colour untouched.
    val applyFog = fn("applyFog", returns = AslType.Data(GpuDataShape.Vec3)) {
        val baseColor by param(GpuDataShape.Vec3)
        val pos by param(GpuDataShape.Vec3)
        val dist = let("dist", length(u.cameraPosition!!.xyz - pos))
        val fogAmount = let("fogAmount", 1f.lit - exp(-u.fogColor!!.a * dist))
        returnValue(mix(baseColor, u.fogColor.rgb, saturate(fogAmount)))
    }

    fragment {
        val n = let("n", normalize(normal))
        val l = let("l", normalize(u.lightDirection.xyz))
        val v = let("v", normalize(u.cameraPosition!!.xyz - worldPos))
        val h = let("h", normalize(v + l))
        val nDotL = let("nDotL", max(dot(n, l), 0f.lit))
        val nDotV = let("nDotV", max(dot(n, v), epsilon))
        val nDotH = let("nDotH", max(dot(n, h), 0f.lit))
        val metallic = let("metallic", clamp(u.material!!.x, 0f.lit, 1f.lit))
        // Floored roughness: perfectly smooth GGX aliases into a single-pixel highlight.
        val roughness = let("roughness", clamp(u.material.y, minRoughness, 1f.lit))
        val f0 = let("f0", mix(vec3(dielectricF0), color, metallic))
        val fresnel = let("fresnel", fresnelSchlick(max(dot(h, v), 0f.lit), f0))
        val specular = let(
            "specular",
            (distributionGgx(nDotH, roughness) * geometrySmith(nDotV, nDotL, roughness) * fresnel) /
                max(4f.lit * nDotV * nDotL, epsilon),
        )
        val diffuse = let("diffuse", (vec3(1f.lit) - fresnel) * (1f.lit - metallic) * color / pi)
        val shadowFactor = let("shadowFactor", sampleShadow(shadowPos, nDotL))
        val direct = variable("direct", (diffuse + specular) * u.lightColor.xyz * nDotL * shadowFactor)
        // Point lights: same BRDF per slot, unshadowed (the one shadow map is directional).
        // Slot count is MAX_POINT_LIGHTS itself -- the same constant that sizes the layout's
        // arrays, so the loop and the struct cannot disagree.
        loopU32("i", 0u.lit, MAX_POINT_LIGHTS.toUInt().lit) { i ->
            val slot = let("slot", u.pointLightPositions[i])
            iff(slot.w le 0f.lit) { continueLoop() }
            val toLight = let("toLight", slot.xyz - worldPos)
            val dist = let("dist", length(toLight))
            iff(dist ge slot.w) { continueLoop() }
            val pl = let("pl", toLight / max(dist, epsilon))
            val pNdotL = let("pNdotL", max(dot(n, pl), 0f.lit))
            iff(pNdotL le 0f.lit) { continueLoop() }
            // Windowed inverse-square: reaches exactly zero at range.
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
            val pDiffuse = let("pDiffuse", (vec3(1f.lit) - pFresnel) * (1f.lit - metallic) * color / pi)
            assign(direct, direct + (pDiffuse + pSpecular) * u.pointLightColors[i].xyz * pNdotL * attenuation)
        }
        val ambient = let("ambient", color * ambientStrength)
        // Reinhard: the specular lobe blows past 1.0 at low roughness.
        val mapped = let("mapped", (ambient + direct) / (ambient + direct + vec3(1f.lit)))
        // Encode before writing -- the swapchain is _UNORM and nothing downstream encodes.
        colorOutput(vec4(applyFog(linearToSrgb(mapped), worldPos), 1f.lit))
    }
}

val LitShadowShader: AslShaderDefinition = litShadow()

/**
 * The vertex position both depth passes rasterise, with the scene's wave displacement applied.
 *
 * All three attributes are declared even though only position is read: a depth pass shares the
 * main pass's vertex layout, and the format says so structurally rather than by comment.
 */
private fun AslVertexBuilder.animatedPosition(u: ShadowUniforms): AslExpr {
    val ins = inputsFrom(VertexFormat.PositionNormalColor)
    val position = ins.input(VertexSemantic.Position)
    val wavelength = max(u.vertexAnimation.y, 0.0001f.lit)
    val phase = u.vertexAnimation.w * u.vertexAnimation.z
    val diagonal = (position.x + position.z) / wavelength + phase
    val cross = (position.x - position.z) / wavelength * 0.7f.lit + phase * 0.8f.lit
    val displacement = (sin(diagonal) + cos(cross)) * u.vertexAnimation.x * 0.5f.lit
    return vec3(position.x, position.y + displacement, position.z)
}
