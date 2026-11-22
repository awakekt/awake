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
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.samplerNonFiltering
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureDepth2d
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.textureSampleLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/** Depth at or past this threshold is cleared sky (no geometry occluding the sun). */
private const val SKY_DEPTH_THRESHOLD = 0.9999f
private const val OCCLUSION_TAPS = 5f
private const val MIN_RADIUS = 0.0001f

/**
 * Full-screen additive sun flare overlay drawn after geometry (#1537 Stage C).
 *
 * Places up to [MAX_SUN_FLARE_ELEMENTS] procedural or sprite-textured elements along the line
 * from the sun's NDC position through the screen centre `(0, 0)`. When [samplesDepth] is true,
 * five taps in [BindingSemantic.SceneDepth] around the sun's screen UV fade the flare out as
 * geometry covers the sun — no hardware occlusion queries required, so Vulkan and WebGPU behave
 * identically.
 */
@Suppress("LongMethod")
fun sunFlareShader(clipSpace: ClipSpace, samplesDepth: Boolean = true): AslShaderDefinition = shader(
    if (samplesDepth) "sun_flare" else "sun_flare_unoccluded",
) {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0)
    val handles = u.fieldsFrom(SunFlareUniformLayout)
    val sunScreen = handles.value("sunScreen")
    val flareParams = handles.value("flareParams")
    val elementGeometry = handles.array("elementGeometry")
    val elementColor = handles.array("elementColor")

    val flareTexture by texture2d(group = group, binding = 1)
    val flareSampler by sampler(group = group, binding = 2)

    val depthTap: ((AslExpr) -> AslExpr)? = if (samplesDepth) {
        val depthGroup = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)
        val sceneDepth by textureDepth2d(group = depthGroup, binding = 0)
        val sceneDepthSampler by samplerNonFiltering(group = depthGroup, binding = 1)
        val sample: (AslExpr) -> AslExpr = { at ->
            val clamped = clamp(at, vec2(0.001f.lit, 0.001f.lit), vec2(0.999f.lit, 0.999f.lit))
            step(SKY_DEPTH_THRESHOLD.lit, textureSampleLevelDepth(sceneDepth, sceneDepthSampler, clamped, 0.lit))
        }
        sample
    } else {
        null
    }

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    fragment {
        val sunNdc = let("sunNdc", sunScreen.xy)
        val occlusion = if (depthTap != null) {
            val sunUv = let("sunUv", ndcToUv(sunNdc, clipSpace))
            val r = let("occR", flareParams.z)
            val rx = let("occRx", r / max(sunScreen.w, 0.25f.lit))
            val sum = let(
                "occSum",
                depthTap(sunUv) +
                    depthTap(sunUv + vec2(rx, 0f.lit)) +
                    depthTap(sunUv - vec2(rx, 0f.lit)) +
                    depthTap(sunUv + vec2(0f.lit, r)) +
                    depthTap(sunUv - vec2(0f.lit, r)),
            )
            let("occlusion", sum / OCCLUSION_TAPS.lit)
        } else {
            let("occlusion", 1f.lit)
        }

        val count = let("count", toU32(flareParams.x + 0.5f.lit))
        val useTexture = let("useTexture", flareParams.y)
        val aspect = let("aspect", sunScreen.w)
        val total = variable("total", vec3(0f.lit))

        loopU32("i", 0u.lit, MAX_SUN_FLARE_ELEMENTS.toUInt().lit) { i ->
            iff(i ge count) { continueLoop() }
            val geom = let("geom", elementGeometry[i])
            val col = let("col", elementColor[i])
            // Position t=0 sits on sunNdc, t=1 sits at screen centre (0, 0), t=2 opposite across centre.
            val center = let("center", sunNdc * (1f.lit - geom.x))
            val delta = let("delta", ndc - center)
            val scaled = let("scaled", vec2(delta.x * aspect, delta.y))
            val radius = let("radius", max(geom.y, MIN_RADIUS.lit))
            val dist = let("dist", length(scaled) / radius)

            // Procedural disc (soft quadratic glow) or ring halo.
            val disc = let("disc", pow(saturate(1f.lit - dist), 2.5f.lit))
            val ringBand = let("ringBand", 1f.lit - smoothstep(0f.lit, 0.22f.lit, abs(dist - 0.78f.lit)))
            val procedural = let("procedural", select(disc, ringBand * 0.85f.lit, geom.z ge 0.5f.lit))

            val spriteUv = let("spriteUv", scaled / (radius * 2f.lit) + vec2(0.5f.lit, 0.5f.lit))
            val inBounds = let(
                "inBounds",
                step(0f.lit, spriteUv.x) * step(spriteUv.x, 1f.lit) * step(0f.lit, spriteUv.y) * step(spriteUv.y, 1f.lit),
            )
            val texSample = let("texSample", textureSampleLevel(flareTexture, flareSampler, saturate(spriteUv), 0f.lit))
            val shaped = let("shaped", mix(vec3(procedural), texSample.rgb * texSample.w * inBounds, useTexture))
            assign(total, total + shaped * col.rgb * geom.w)
        }

        val strength = let("strength", saturate(sunScreen.z) * occlusion)
        colorOutput(vec4(total * strength, 1f.lit))
    }
}
