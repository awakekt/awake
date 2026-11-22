/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFragmentBuilder
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.sqrt
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * Single-pass AMD FidelityFX CAS shaders for the scaled-scene upscale pass (#1830):
 * - [CasSharpenShader]: 5-tap contrast-adaptive sharpening over bilinearly sampled colour.
 * - [CasUpscaleShader]: 12-tap edge-thinning spatial upscale and contrast-adaptive sharpening.
 *
 * Adapted from `GPUOpen-Effects/FidelityFX-CAS` (`ffx-cas/ffx_cas.h`, MIT); see
 * `docs/reference/source-provenance.json`.
 */
object UpscaleShaders {
    /** Identifier for the 5-tap contrast-adaptive sharpening shader. */
    const val CAS_SHARPEN_SHADER_ID: String = "upscale_cas_sharpen"

    /** Identifier for the 12-tap edge-thinning upscale and sharpening shader. */
    const val CAS_UPSCALE_SHADER_ID: String = "upscale_cas_scale"

    /** `(1/outputWidth, 1/outputHeight, peak, 0)` for [CasSharpenShader]. */
    val CasSharpenParamsField: UniformField = UniformField("casParams", GpuDataShape.Vec4)

    /** `(inputWidth, inputHeight, 1/inputWidth, 1/inputHeight)` for [CasUpscaleShader]. */
    val CasScaleParamsField: UniformField = UniformField("scaleParams", GpuDataShape.Vec4)

    /** `(peak, 0, 0, 0)` for [CasUpscaleShader]. */
    val CasFilterParamsField: UniformField = UniformField("filterParams", GpuDataShape.Vec4)

    /** Uniform block layout for [CasSharpenShader]. */
    val CasSharpenUniformLayout: UniformLayout = UniformLayout(CasSharpenParamsField)

    /** Uniform block layout for [CasUpscaleShader]. */
    val CasUpscaleUniformLayout: UniformLayout = UniformLayout(CasScaleParamsField, CasFilterParamsField)

    /** Minimum reciprocal divisor guarding against pure-black neighbourhoods. */
    private const val MIN_LUMA_DIVISOR = 0.0001f

    /** Edge-thinning bias (`1 / 32`) from FidelityFX CAS scaling mode. */
    private const val EDGE_THIN_BIAS = 1f / 32f

    /**
     * Five-tap contrast-adaptive sharpening shader (AMD FidelityFX CAS non-scaling kernel,
     * green-channel weight mode). Runs over a linearly sampled scaled target at one-output-pixel
     * steps so flat regions stay invariant and blurred bilinear edges regain contrast.
     */
    val CasSharpenShader: AslShaderDefinition = shader(CAS_SHARPEN_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val casParams by uniforms.field(GpuDataShape.Vec4)

        val sourceTexture by texture2d(group = 0, binding = 1)
        val sourceSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            val stepX = let("stepX", vec2(casParams.x, 0f.lit))
            val stepY = let("stepY", vec2(0f.lit, casParams.y))
            // SPDX-SnippetBegin
            // SPDX-SnippetCopyrightText: 2017-2019 Advanced Micro Devices, Inc.
            // SPDX-License-Identifier: MIT
            // Adapted from AMD FidelityFX CAS (ffx-cas/ffx_cas.h); see docs/reference/source-provenance.json.
            val b = let("b", textureSample(sourceTexture, sourceSampler, uv - stepY).rgb)
            val d = let("d", textureSample(sourceTexture, sourceSampler, uv - stepX).rgb)
            val center = let("center", textureSample(sourceTexture, sourceSampler, uv))
            val e = let("e", center.rgb)
            val f = let("f", textureSample(sourceTexture, sourceSampler, uv + stepX).rgb)
            val h = let("h", textureSample(sourceTexture, sourceSampler, uv + stepY).rgb)
            val cross = casCrossStats("c", b.g, d.g, e.g, f.g, h.g, casParams.z)
            val rcpWeight = let("rcpWeight", 1f.lit / (1f.lit + 4f.lit * cross.weight))
            val filtered = let(
                "filtered",
                clamp(((b + d + f + h) * cross.weight + e) * rcpWeight, vec3(0f.lit), vec3(1f.lit)),
            )
            // SPDX-SnippetEnd
            colorOutput(vec4(filtered, center.a))
        }
    }

    /**
     * Single-pass twelve-tap edge-thinning upscale and contrast-adaptive sharpening shader
     * (AMD FidelityFX CAS scaling kernel, green-channel weight mode, up to 4x area / 50% scale).
     */
    val CasUpscaleShader: AslShaderDefinition = shader(CAS_UPSCALE_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val scaleParams by uniforms.field(GpuDataShape.Vec4)
        val filterParams by uniforms.field(GpuDataShape.Vec4)

        val sourceTexture by texture2d(group = 0, binding = 1)
        val sourceSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            // SPDX-SnippetBegin
            // SPDX-SnippetCopyrightText: 2017-2019 Advanced Micro Devices, Inc.
            // SPDX-License-Identifier: MIT
            // Adapted from AMD FidelityFX CAS (ffx-cas/ffx_cas.h); see docs/reference/source-provenance.json.
            val ppFull = let("ppFull", uv * scaleParams.xy - vec2(0.5f.lit, 0.5f.lit))
            val fp = let("fp", floor(ppFull))
            val pp = let("pp", ppFull - fp)
            val invSize = let("invSize", scaleParams.zw)
            fun tap(name: String, dx: Float, dy: Float): AslExpr = let(
                name,
                textureSample(sourceTexture, sourceSampler, (fp + vec2((dx + 0.5f).lit, (dy + 0.5f).lit)) * invSize),
            )
            val b = let("b", tap("tb", 0f, -1f).rgb)
            val c = let("c", tap("tc", 1f, -1f).rgb)
            val e = let("e", tap("te", -1f, 0f).rgb)
            val tf = tap("tf", 0f, 0f)
            val tg = tap("tg", 1f, 0f)
            val f = let("f", tf.rgb)
            val g = let("g", tg.rgb)
            val h = let("h", tap("th", 2f, 0f).rgb)
            val i = let("i", tap("ti", -1f, 1f).rgb)
            val tj = tap("tj", 0f, 1f)
            val tk = tap("tk", 1f, 1f)
            val j = let("j", tj.rgb)
            val k = let("k", tk.rgb)
            val l = let("l", tap("tl", 2f, 1f).rgb)
            val n = let("n", tap("tn", 0f, 2f).rgb)
            val o = let("o", tap("to", 1f, 2f).rgb)

            val peak = filterParams.x
            val cf = casCrossStats("f", b.g, e.g, f.g, g.g, j.g, peak)
            val cg = casCrossStats("g", c.g, f.g, g.g, h.g, k.g, peak)
            val cj = casCrossStats("j", f.g, i.g, j.g, k.g, n.g, peak)
            val ck = casCrossStats("k", g.g, j.g, k.g, l.g, o.g, peak)

            val oneMinusX = let("oneMinusX", 1f.lit - pp.x)
            val oneMinusY = let("oneMinusY", 1f.lit - pp.y)
            val s0 = let("s0", oneMinusX * oneMinusY)
            val t0 = let("t0", pp.x * oneMinusY)
            val u0 = let("u0", oneMinusX * pp.y)
            val v0 = let("v0", pp.x * pp.y)

            val thin = EDGE_THIN_BIAS.lit
            val s = let("s", s0 / (thin + cf.contrast))
            val t = let("t", t0 / (thin + cg.contrast))
            val u = let("u", u0 / (thin + cj.contrast))
            val v = let("v", v0 / (thin + ck.contrast))

            val qbe = let("qbe", cf.weight * s)
            val qch = let("qch", cg.weight * t)
            val qf = let("qf", cg.weight * t + cj.weight * u + s)
            val qg = let("qg", cf.weight * s + ck.weight * v + t)
            val qj = let("qj", cf.weight * s + ck.weight * v + u)
            val qk = let("qk", cg.weight * t + cj.weight * u + v)
            val qin = let("qin", cj.weight * u)
            val qlo = let("qlo", ck.weight * v)

            val rcpW = let(
                "rcpW",
                1f.lit / (2f.lit * (qbe + qch + qin + qlo) + qf + qg + qj + qk),
            )
            val num = let(
                "num",
                (b + e) * qbe + (c + h) * qch + (i + n) * qin + (l + o) * qlo +
                    f * qf + g * qg + j * qj + k * qk,
            )
            val filtered = let("filtered", clamp(num * rcpW, vec3(0f.lit), vec3(1f.lit)))
            val alpha = let("alpha", tf.a * s0 + tg.a * t0 + tj.a * u0 + tk.a * v0)
            // SPDX-SnippetEnd
            colorOutput(vec4(filtered, alpha))
        }
    }

    /** Self-contained [ImagePassShaderSpec] for [CasSharpenShader]. */
    val CasSharpenSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = CAS_SHARPEN_SHADER_ID,
            wgslSource = CasSharpenShader.emitWgsl(),
            groupBindings = CasSharpenShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = CasSharpenUniformLayout,
        )
    }

    /** Self-contained [ImagePassShaderSpec] for [CasUpscaleShader]. */
    val CasUpscaleSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = CAS_UPSCALE_SHADER_ID,
            wgslSource = CasUpscaleShader.emitWgsl(),
            groupBindings = CasUpscaleShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = CasUpscaleUniformLayout,
        )
    }

    /**
     * Converts a `0f..1f` sharpness setting to AMD FidelityFX CAS's negative lobe peak
     * (`-1 / lerp(8, 5, strength)`): `-0.125` at `0f` (subtle/low ringing) to `-0.2` at `1f`.
     */
    fun casPeak(strength: Float): Float {
        val clamped = strength.coerceIn(0f, 1f)
        return -1f / (8f - 3f * clamped)
    }

    /** Packs [CasSharpenUniformLayout] for an [outputWidth] x [outputHeight] destination. */
    fun packCasSharpenUniforms(outputWidth: Int, outputHeight: Int, strength: Float): FloatArray {
        val invW = 1f / outputWidth.coerceAtLeast(1)
        val invH = 1f / outputHeight.coerceAtLeast(1)
        return UniformWriter(CasSharpenUniformLayout)
            .put(CasSharpenParamsField, invW, invH, casPeak(strength), 0f)
            .build()
    }

    /** Packs [CasUpscaleUniformLayout] for an [inputWidth] x [inputHeight] scaled source target. */
    fun packCasUpscaleUniforms(inputWidth: Int, inputHeight: Int, strength: Float): FloatArray {
        val w = inputWidth.coerceAtLeast(1).toFloat()
        val h = inputHeight.coerceAtLeast(1).toFloat()
        return UniformWriter(CasUpscaleUniformLayout)
            .put(CasScaleParamsField, w, h, 1f / w, 1f / h)
            .put(CasFilterParamsField, casPeak(strength), 0f, 0f, 0f)
            .build()
    }

    private class CasCross(val weight: AslExpr, val contrast: AslExpr)

    @Suppress("LongParameterList")
    private fun AslFragmentBuilder.casCrossStats(
        tag: String,
        top: AslExpr,
        left: AslExpr,
        center: AslExpr,
        right: AslExpr,
        bottom: AslExpr,
        peak: AslExpr,
    ): CasCross {
        val mn = let("mn_$tag", min(min(min(left, center), right), min(top, bottom)))
        val mx = let("mx_$tag", max(max(max(left, center), right), max(top, bottom)))
        val rcpM = let("rcpM_$tag", 1f.lit / max(mx, MIN_LUMA_DIVISOR.lit))
        val amp = let("amp_$tag", sqrt(clamp(min(mn, 1f.lit - mx) * rcpM, 0f.lit, 1f.lit)))
        val w = let("w_$tag", amp * peak)
        val contrast = let("contrast_$tag", mx - mn)
        return CasCross(w, contrast)
    }
}
