/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslExpr
import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.AslType
import io.github.awakelab.awake.asset.shaderdsl.AslVertexBuilder
import io.github.awakelab.awake.asset.shaderdsl.F32
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.clamp
import io.github.awakelab.awake.asset.shaderdsl.cos
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.exp
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.ge
import io.github.awakelab.awake.asset.shaderdsl.gt
import io.github.awakelab.awake.asset.shaderdsl.inputsFrom
import io.github.awakelab.awake.asset.shaderdsl.le
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.lt
import io.github.awakelab.awake.asset.shaderdsl.max
import io.github.awakelab.awake.asset.shaderdsl.min
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.mix
import io.github.awakelab.awake.asset.shaderdsl.ndcToUv
import io.github.awakelab.awake.asset.shaderdsl.normalize
import io.github.awakelab.awake.asset.shaderdsl.or
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.pow
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.samplerComparison
import io.github.awakelab.awake.asset.shaderdsl.saturate
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.sin
import io.github.awakelab.awake.asset.shaderdsl.sqrt
import io.github.awakelab.awake.asset.shaderdsl.textureDepth2dArray
import io.github.awakelab.awake.asset.shaderdsl.textureDimensions
import io.github.awakelab.awake.asset.shaderdsl.textureSampleCompareLevel
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
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.renderer.CascadePassUniformLayout
import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS
import io.github.awakelab.awake.render.renderer.MAX_SHADOW_CASCADES
import io.github.awakelab.awake.render.renderer.SHADOW_CASCADE_PASS_GROUP

/** Depth-only shadow-map pre-pass: binds lit_shadow's buffer (hence the full prefix struct),
 * reads only lightMvp, writes no color -- depth comes from the fixed-function pipeline. The
 * unused inputs keep the vertex layout identical to the main pass. */
val ShadowDepthShader: AslShaderDefinition = shader("shadow_depth") {
    val u = shadowUniforms(includeLitTail = false)
    // The cascade being rendered, from the pass rather than the draw: this shader runs once per
    // cascade over the same meshes, and a per-draw uniform is written once a frame.
    val pass = uniformBlock("Cascade", group = SHADOW_CASCADE_PASS_GROUP, binding = 0)
    val cascadeViewProjection =
        pass.fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    vertex {
        val world = u.model * vec4(animatedPosition(u), 1f.lit)
        returnPosition(cascadeViewProjection * world)
    }
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
    // This draw's own mvp, not a pass-scoped matrix: the camera-space pass renders once, so
    // there is nothing for a per-pass block to say. It therefore declares no group 1, and
    // `DepthOnlyPipeline` must be built for it with no cascade block -- see that class.
    vertex { returnPosition(u.mvp * vec4(animatedPosition(u), 1f.lit)) }
    fragment { }
}

/** PBR + shadow-mapped variant of the triangle shader, live on BOTH backends. The map is
 * declared `texture_depth_2d` (not `texture_2d<f32>`): WebGPU's auto layout derives a
 * filterable-float binding from the float spelling, which a Depth32Float view fails
 * validation against, while the depth spelling is legal on Vulkan too -- one shared source.
 * Hardware-compare PCF (`textureSampleCompareLevel` on a `sampler_comparison`): each tap
 * compares before filtering, so a linear sampler blends four COMPARISON RESULTS rather than
 * four depths -- the near-tie a manual point-sampled `select` flips on averages out instead.
 * Explicit-LOD (compare-level) sampling because implicit derivatives inside a loop are
 * undefined control flow.
 *
 * @param clipSpace The convention this is emitted for. It decides the shadow lookup's V axis and
 * nothing else -- see [ndcToUv], which is where that decision is made for every shader. With the
 * wrong axis the lookup is mirrored about the map's centre, so a caster's shadow lands on the far
 * side of the scene from the caster. */
@Suppress("LongMethod")
private fun litShadow(clipSpace: ClipSpace): AslShaderDefinition = shader("lit_shadow") {
    val u = shadowUniforms(includeLitTail = true)
    val shadowMap by textureDepth2dArray(
        group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth),
        binding = 0,
    )
    val shadowMapSampler by samplerComparison(
        group = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth),
        binding = 1,
    )

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val worldPos by out.varying(GpuDataShape.Vec3, location = 2)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColor)
        val inPosition = ins.input(VertexSemantic.Position)
        val wavelength = max(u.vertexAnimation.y, 0.0001f.lit)
        val phase = u.vertexAnimation.w * u.vertexAnimation.z
        val diagonal = (inPosition.x + inPosition.z) / wavelength + phase
        val cross = (inPosition.x - inPosition.z) / wavelength * 0.7f.lit + phase * 0.8f.lit
        val wave = (sin(diagonal) + cos(cross)) * u.vertexAnimation.x * 0.5f.lit
        val animatedPosition = vec3(inPosition.x, inPosition.y + wave, inPosition.z)
        val dX =
            (cos(diagonal) - sin(cross) * 0.7f.lit) * u.vertexAnimation.x * 0.5f.lit / wavelength
        val dZ =
            (cos(diagonal) + sin(cross) * 0.7f.lit) * u.vertexAnimation.x * 0.5f.lit / wavelength
        val animatedNormal = normalize(vec3(-dX, 1f.lit, -dZ))
        out.position set (u.mvp * vec4(animatedPosition, 1f.lit))
        color set ins.input(VertexSemantic.Color)
        normal set (u.model * vec4(animatedNormal, 0f.lit)).xyz
        worldPos set (u.model * vec4(animatedPosition, 1f.lit)).xyz
    }

    val ambientStrength = const("AMBIENT_STRENGTH", 0.08f)
    val pi = const("PI", 3.14159265359f)
    val epsilon = const("EPSILON", 0.0001f)
    val dielectricF0 = const("DIELECTRIC_F0", 0.04f)
    val minRoughness = const("MIN_ROUGHNESS", 0.05f)
    val invGamma = const("INV_GAMMA", 1.0f / 2.2f)
    // Bias measured in TEXELS of whichever cascade is sampled, not in metres and not in NDC.
    //
    // A texel is the only unit the error is actually in: the map stores one depth for a whole
    // texel, so a surface crossing that texel is misrepresented by its own slope across it. A
    // fixed distance is therefore too much in a near cascade (the shadow detaches) and too little
    // in a far one (the surface scales), and NDC -- what this used to be -- is both at once,
    // since every cascade maps a different world range into 0..1.
    //
    // These are the RECEIVER'S share of the bias only. The depth pass applies the source share
    // with the rasterizer's own per-polygon slope (`SHADOW_DEPTH_BIAS_CONSTANT`/`_SLOPE` in the
    // contract), which is why these are small: 2.5/3 was the floor while the receiver carried
    // everything, and no receiver-side constant could remove the per-texel waffle a tilted face
    // shows -- the receiver estimates slope from its own nDotL, the error lives in the MAP's
    // polygons. Swept 2026-09-01 against the hardware comparison sampler: 1.5/2 with offset 1
    // leaves the studio cube at worst 1 self-shadowed pixel per yaw on both backends, and
    // 1.5/1 with offset 0.5 is the measured cliff where the grazing-face probe fails again.
    val shadowBiasTexels = const("SHADOW_BIAS_TEXELS", 1.5f)
    val shadowSlopeTexels = const("SHADOW_SLOPE_BIAS_TEXELS", 2f)
    val maxSlopeScale = const("SHADOW_MAX_SLOPE_SCALE", 8f)
    // How far along the surface normal the lookup moves, in shadow-map texels. Bias alone cannot
    // fix a surface lit edge-on: its map texels store whatever stands above it (a box's own top
    // face), which is not an approximation of this surface but a different one. Moving the
    // LOOKUP samples clear of that shared texel, and unlike more bias it does not detach the
    // shadow from the caster.
    val normalOffsetTexels = const("SHADOW_NORMAL_OFFSET_TEXELS", 1f)
    val pcfRadius = constI32("PCF_RADIUS", 1)

    /**
     * How lit this fragment is, from whichever cascade contains it.
     *
     * Chosen by containment rather than by comparing a view distance against split planes: the
     * boxes were fitted to spheres around frustum slices, so a distance test agrees with them
     * only approximately, and where it disagrees a fragment samples a cascade that does not
     * cover it -- which reads as a band of missing shadow at a cascade boundary. Testing the
     * projection directly cannot disagree with the fit, because it IS the fit.
     *
     * Near cascade first, so the first containing cascade is also the highest-resolution one.
     */
    val sampleShadow = fn("sampleShadow") {
        val world by param(GpuDataShape.Vec3)
        val normal by param(GpuDataShape.Vec3)
        val nDotL by param(F32)
        // How much depth one texel of this surface covers, as the light gets glancing:
        // tan(acos(nDotL)), written out as sqrt(1 - n^2)/n. Clamped because it runs to infinity at
        // the horizon, where an unbounded bias would detach every shadow in the frame.
        //
        // This was (1 - n)/n, described as the same shape without the square root. It is not: the
        // two differ by sqrt((1 + n)/(1 - n)), which is 1.7x at n = 0.5, 2.4x at 0.7 and 4.4x at
        // 0.9. They agree only at grazing angles, so the cheap form was shortest exactly where a
        // face points AT the light -- and a spinning cube sweeps its lit faces through that band,
        // which is why the studio's cube stippled at some yaws and not others.
        val slopeScale = let(
            "slopeScale",
            min(sqrt(max(1f.lit - nDotL * nDotL, 0f.lit)) / max(nDotL, epsilon), maxSlopeScale),
        )
        val texSize = let("texSize", vec2(textureDimensions(shadowMap)))
        val texel = let("texel", 1f.lit / texSize)
        val lit = variable("lit", 1f.lit)
        val resolved = variable("resolved", 0.lit)
        loopI32("cascade", 0.lit, (MAX_SHADOW_CASCADES - 1).lit) { cascade ->
            iff(resolved gt 0.lit) { continueLoop() }
            // One texel as a distance: this cascade's world width times the map's own texel size.
            // Derived rather than passed, so a map resized at runtime cannot leave it stale.
            val texelWorld = let("texelWorld", u.cascadeDepthScales[cascade].y * texel.x)
            // Bias sized to THIS cascade's texel, then converted to its depth range below. A far
            // cascade gets a proportionally larger bias because its error is proportionally
            // larger, which a single distance cannot express for both ends of the split scheme.
            val worldBias = let(
                "worldBias",
                texelWorld * (shadowBiasTexels + shadowSlopeTexels * slopeScale),
            )
            // Divided by n dot l, not scaled by (1 - n dot l): one texel of the map covers
            // texel/nDotL of THIS surface, so that is how far the lookup has to move to leave
            // the texel it shares with whatever stands above it. The two agree for a surface
            // facing the light and diverge exactly where the artefact lives -- at nDotL 0.2 the
            // old form moved 1.6 texels and this moves 7.7.
            val slide = let("slide", texelWorld * normalOffsetTexels / max(nDotL, epsilon))
            // Containment answers for the FRAGMENT, not for the offset lookup. Selecting on the
            // offset position couples the two: growing the offset can push a fragment out of one
            // cascade into the next, and the artefact it was meant to fix then moves rather than
            // shrinks -- which is what made this constant behave chaotically when swept.
            val projected = let(
                "projected",
                u.cascadeViewProjections[cascade] * vec4(world, 1f.lit),
            )
            iff(projected.w le 0f.lit) { continueLoop() }
            // NDC depth is already 0..1 (ClipSpace.depthZeroToOne) -- no *0.5+0.5 remap.
            val ndc = let("ndc", projected.xyz / projected.w)
            val offsetProjected = let(
                "offsetProjected",
                u.cascadeViewProjections[cascade] * vec4(world + normal * slide, 1f.lit),
            )
            val offsetNdcXy = let("offsetNdcXy", offsetProjected.xy / offsetProjected.w)
            val sampleUv = let("sampleUv", ndcToUv(offsetNdcXy, clipSpace))
            iff(
                (sampleUv.x lt 0f.lit) or (sampleUv.x gt 1f.lit) or
                    (sampleUv.y lt 0f.lit) or (sampleUv.y gt 1f.lit) or
                    (ndc.z lt 0f.lit) or (ndc.z gt 1f.lit),
            ) { continueLoop() }
            assign(resolved, 1.lit)
            val bias = let("bias", worldBias * u.cascadeDepthScales[cascade].x)
            val offsetNdc = ndc
            val shadow = variable("shadow", 0f.lit)
            val samples = variable("samples", 0f.lit)
            loopI32("dx", -pcfRadius, pcfRadius) { dx ->
                loopI32("dy", -pcfRadius, pcfRadius) { dy ->
                    val offset = let("offset", vec2(toF32(dx), toF32(dy)) * texel)
                    // The GPU compares (LessEqual, set at sampler creation) and filters the
                    // results: each tap is already a lit fraction, not a depth.
                    val tapLit = let(
                        "tapLit",
                        textureSampleCompareLevel(
                            shadowMap,
                            shadowMapSampler,
                            sampleUv + offset,
                            cascade,
                            offsetNdc.z - bias,
                        ),
                    )
                    assign(shadow, shadow + tapLit)
                    assign(samples, samples + 1f.lit)
                }
            }
            assign(lit, shadow / samples)
        }
        // Nothing contained it: beyond the last cascade, where an unshadowed fragment is the
        // honest answer and a guessed one would flicker as the camera moves.
        returnValue(lit)
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
        returnValue(
            f0 + (vec3(1f.lit) - f0) * pow(
                clamp(1f.lit - cosTheta, 0f.lit, 1f.lit),
                5f.lit,
            ),
        )
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
        val shadowFactor = let("shadowFactor", sampleShadow(worldPos, n, nDotL))
        val direct =
            variable("direct", (diffuse + specular) * u.lightColor.xyz * nDotL * shadowFactor)
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
            val falloff =
                let("falloff", clamp(1f.lit - (dist * dist) / (slot.w * slot.w), 0f.lit, 1f.lit))
            val attenuation = let("attenuation", falloff * falloff / max(dist * dist, epsilon))
            val ph = let("ph", normalize(v + pl))
            val pNdotH = let("pNdotH", max(dot(n, ph), 0f.lit))
            val pFresnel = let("pFresnel", fresnelSchlick(max(dot(ph, v), 0f.lit), f0))
            val pSpecular = let(
                "pSpecular",
                (
                    distributionGgx(pNdotH, roughness) * geometrySmith(
                        nDotV,
                        pNdotL,
                        roughness,
                    ) * pFresnel
                    ) /
                    max(4f.lit * nDotV * pNdotL, epsilon),
            )
            val pDiffuse =
                let("pDiffuse", (vec3(1f.lit) - pFresnel) * (1f.lit - metallic) * color / pi)
            assign(
                direct,
                direct + (pDiffuse + pSpecular) * u.pointLightColors[i].xyz * pNdotL * attenuation,
            )
        }
        val ambient = let("ambient", color * ambientStrength)
        // Reinhard: the specular lobe blows past 1.0 at low roughness.
        val mapped = let("mapped", (ambient + direct) / (ambient + direct + vec3(1f.lit)))
        // Encode before writing -- the swapchain is _UNORM and nothing downstream encodes.
        colorOutput(vec4(applyFog(linearToSrgb(mapped), worldPos), 1f.lit))
    }
}

/** `lit_shadow` for [clipSpace]. One definition; the emitted V axis follows the backend. */
fun litShadowShader(clipSpace: ClipSpace): AslShaderDefinition = litShadow(clipSpace)

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
