/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslArrayHandle
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFunctionHandle
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.F32
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.or
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.sqrt
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSampleCompareLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
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
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES

/**
 * The cascade fields a shader's uniform block carries, in the layout `MaterialUniformLayouts.LitShadow`
 * gives them: one view-projection and one depth-scale vec4 per cascade.
 *
 * @property viewProjections Each cascade's light view-projection.
 * @property depthScales Per cascade: x = NDC depth per world unit, y = world extent, z = split far
 *   distance, w = blend start.
 * @property cameraPosition The camera's world position; only xyz is read.
 * @property cameraForward xyz = the camera's forward direction, w = the active cascade count.
 */
class CascadeShadowInputs(
    val viewProjections: AslArrayHandle,
    val depthScales: AslArrayHandle,
    val cameraPosition: AslExpr,
    val cameraForward: AslExpr,
)

/**
 * What [cascadeShadowSampling] declared.
 *
 * @property sampleShadow `sampleShadow(world: vec3, normal: vec3, nDotL: f32) -> f32`: 1 lit,
 *   0 fully shadowed, 1 outside every cascade.
 * @property biasTexels Receiver depth bias, in texels of the sampled layer.
 * @property slopeBiasTexels Receiver slope bias, in texels, scaled by the surface's slope.
 * @property maxSlopeScale The largest slope factor the bias applies.
 */
class CascadeShadowSampling(
    val sampleShadow: AslFunctionHandle,
    val biasTexels: AslExpr,
    val slopeBiasTexels: AslExpr,
    val maxSlopeScale: AslExpr,
)

/**
 * Declares cascaded directional-shadow sampling against the engine's shadow map, for any shader
 * that binds it: 5x5 hardware-compare PCF with texel-scaled bias and a normal-offset lookup,
 * cascades picked by camera depth and blended across each split's overlap.
 *
 * @param inputs The cascade fields from the shader's uniform block.
 * @param shadowMap The `texture_depth_2d_array` the depth pass writes.
 * @param shadowMapSampler Its comparison sampler.
 * @param clipSpace The backend's clip space; decides how NDC maps to texture UV.
 * @param epsilon A small positive guard against division by zero.
 */
@Suppress("LongMethod")
fun AslShaderBuilder.cascadeShadowSampling(
    inputs: CascadeShadowInputs,
    shadowMap: AslExpr,
    shadowMapSampler: AslExpr,
    clipSpace: ClipSpace,
    epsilon: AslExpr,
): CascadeShadowSampling {
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
    // Five-by-five comparison PCF keeps the existing texel footprint while hiding the stair-step
    // edges that are especially visible in the directional/cascade samples. Point shadows retain
    // the hardware-filtered single lookup because their perspective footprint already varies per
    // fragment and a second kernel there would multiply the cost for every point-light slot.
    val pcfRadius = constI32("PCF_RADIUS", 2)

    /** Samples a single cascade's PCF shadow depth; returns -1.0f if out of bounds. */
    val sampleSingleCascade = fn("sampleSingleCascade") {
        val cascade by param(AslType.I32)
        val world by param(GpuDataShape.Vec3)
        val normal by param(GpuDataShape.Vec3)
        val nDotL by param(F32)
        val slopeScale by param(F32)
        val texSize by param(GpuDataShape.Vec2)
        val texel by param(GpuDataShape.Vec2)

        val texelWorld = let("texelWorld", inputs.depthScales[cascade].y * texel.x)
        val worldBias = let(
            "worldBias",
            texelWorld * (shadowBiasTexels + shadowSlopeTexels * slopeScale),
        )
        val grazing = let(
            "grazing",
            clamp((0.45f.lit - nDotL) * 20f.lit, 0f.lit, 1f.lit),
        )
        val slide = let(
            "slide",
            texelWorld * normalOffsetTexels * grazing / max(nDotL, epsilon),
        )
        val projected = let(
            "projected",
            inputs.viewProjections[cascade] * vec4(world, 1f.lit),
        )
        iff(projected.w le 0f.lit) { returnValue(-1f.lit) }
        val ndc = let("ndc", projected.xyz / projected.w)
        iff(
            (ndc.x lt -1f.lit) or (ndc.x gt 1f.lit) or
                (ndc.y lt -1f.lit) or (ndc.y gt 1f.lit) or
                (ndc.z lt 0f.lit) or (ndc.z gt 1f.lit),
        ) { returnValue(-1f.lit) }

        val offsetProjected = let(
            "offsetProjected",
            inputs.viewProjections[cascade] * vec4(world + normal * slide, 1f.lit),
        )
        val offsetNdcXy = let("offsetNdcXy", offsetProjected.xy / offsetProjected.w)
        val sampleUv = let("sampleUv", ndcToUv(offsetNdcXy, clipSpace))
        val bias = let("bias", worldBias * inputs.depthScales[cascade].x)
        val minUv = let("minUv", texel * toF32(pcfRadius))
        val maxUv = let("maxUv", vec2(1f.lit) - minUv)
        val clampedSampleUv = let("clampedSampleUv", clamp(sampleUv, minUv, maxUv))
        val offsetNdc = ndc
        val shadow = variable("shadow", 0f.lit)
        val samples = variable("samples", 0f.lit)
        loopI32("dx", -pcfRadius, pcfRadius) { dx ->
            loopI32("dy", -pcfRadius, pcfRadius) { dy ->
                val offset = let("offset", vec2(toF32(dx), toF32(dy)) * texel)
                val tapLit = let(
                    "tapLit",
                    textureSampleCompareLevel(
                        shadowMap,
                        shadowMapSampler,
                        clampedSampleUv + offset,
                        cascade,
                        offsetNdc.z - bias,
                    ),
                )
                assign(shadow, shadow + tapLit)
                assign(samples, samples + 1f.lit)
            }
        }
        returnValue(shadow / samples)
    }

    /**
     * Selects by camera-space depth and blends across a narrow fitted split overlap. Blending at
     * light-map edges made the weight move as the camera moved and sampled a second map across
     * large portions of the image, causing temporal shimmer and unnecessary PCF work.
     */
    val sampleShadow = fn("sampleShadow") {
        val world by param(GpuDataShape.Vec3)
        val normal by param(GpuDataShape.Vec3)
        val nDotL by param(F32)

        val slopeScale = let(
            "slopeScale",
            min(sqrt(max(1f.lit - nDotL * nDotL, 0f.lit)) / max(nDotL, epsilon), maxSlopeScale),
        )
        val texSize = let("texSize", vec2(textureDimensions(shadowMap)))
        val texel = let("texel", 1f.lit / texSize)
        val viewDepth = let(
            "viewDepth",
            dot(world - inputs.cameraPosition.xyz, normalize(inputs.cameraForward.xyz)),
        )
        val lit = variable("lit", 1f.lit)
        val resolved = variable("resolved", 0.lit)

        loopI32("cascade", 0.lit, (MAX_SHADOW_CASCADES - 1).lit) { cascade ->
            iff(resolved gt 0.lit) { continueLoop() }
            val splitFar = let("splitFar", inputs.depthScales[cascade].z)
            val blendStart = let("blendStart", inputs.depthScales[cascade].w)
            val blendEnd = let("blendEnd", splitFar + (splitFar - blendStart))
            iff(viewDepth gt blendEnd) { continueLoop() }
            val primaryShadow = let(
                "primaryShadow",
                sampleSingleCascade(cascade, world, normal, nDotL, slopeScale, texSize, texel),
            )
            iff(primaryShadow lt 0f.lit) {
                // The fit should contain this camera slice. If a custom matrix does not, report
                // it unshadowed instead of sampling a different depth slice by accident.
                assign(resolved, 1.lit)
                continueLoop()
            }

            assign(resolved, 1.lit)
            assign(lit, primaryShadow)
            iff(
                (toF32(cascade) lt (inputs.cameraForward.w - 1f.lit)) and (viewDepth gt blendStart),
            ) {
                val nextCascade = let("nextCascade", cascade + 1.lit)
                val nextShadow = let(
                    "nextShadow",
                    sampleSingleCascade(nextCascade, world, normal, nDotL, slopeScale, texSize, texel),
                )
                iff(nextShadow ge 0f.lit) {
                    val alpha = let("blendAlpha", smoothstep(blendStart, blendEnd, viewDepth))
                    assign(lit, mix(primaryShadow, nextShadow, alpha))
                }
            }
        }
        returnValue(lit)
    }

    return CascadeShadowSampling(sampleShadow, shadowBiasTexels, shadowSlopeTexels, maxSlopeScale)
}
