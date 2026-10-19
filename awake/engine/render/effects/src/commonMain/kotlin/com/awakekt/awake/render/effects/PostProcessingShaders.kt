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
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
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
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * Shaders and uniform layouts for post-processing stack image passes.
 * - Tone-mapping & colour grading (with optional bloom composite)
 * - Bright-pass threshold for bloom
 * - FXAA (Fast Approximate Anti-Aliasing)
 */
object PostProcessingShaders {
    const val TONE_MAPPING_GRADE_SHADER_ID: String = "post_tone_mapping_grade"
    const val TONE_MAPPING_GRADE_BLOOM_SHADER_ID: String = "post_tone_mapping_grade_bloom"
    const val BLOOM_BRIGHT_PASS_SHADER_ID: String = "post_bloom_bright"
    const val FXAA_SHADER_ID: String = "post_fxaa"

    // --- Tone Mapping & Grade Uniforms ---
    val ExposureAndContrastField: UniformField = UniformField("exposureAndContrast", GpuDataShape.Vec4)
    val WhiteBalanceField: UniformField = UniformField("whiteBalance", GpuDataShape.Vec4)
    val LiftField: UniformField = UniformField("lift", GpuDataShape.Vec4)
    val GammaField: UniformField = UniformField("gamma", GpuDataShape.Vec4)
    val GainField: UniformField = UniformField("gain", GpuDataShape.Vec4)

    val ToneMappingGradeUniformLayout: UniformLayout = UniformLayout(
        ExposureAndContrastField,
        WhiteBalanceField,
        LiftField,
        GammaField,
        GainField,
    )

    // --- Bloom Bright Pass Uniforms ---
    val BloomParamsField: UniformField = UniformField("bloomParams", GpuDataShape.Vec4)
    val BloomBrightPassUniformLayout: UniformLayout = UniformLayout(BloomParamsField)

    // --- FXAA Uniforms ---
    val FxaaParamsField: UniformField = UniformField("fxaaParams", GpuDataShape.Vec4)
    val FxaaUniformLayout: UniformLayout = UniformLayout(FxaaParamsField)

    @Suppress("LongParameterList")
    private fun AslFragmentBuilder.applyGradingAndToneMap(
        radiance: AslExpr,
        exposureAndContrast: AslExpr,
        whiteBalance: AslExpr,
        lift: AslExpr,
        gamma: AslExpr,
        gain: AslExpr,
    ) {
        // 1. Exposure
        val exposed = let("exposed", radiance * exposureAndContrast.x)

        // 2. White balance (temperature = x, tint = y)
        val temp = let("temp", whiteBalance.x)
        val tint = let("tint", whiteBalance.y)
        val balancedR = let("balancedR", exposed.r * (1f.lit + temp * 0.2f.lit + tint * 0.1f.lit))
        val balancedG = let("balancedG", exposed.g * (1f.lit - tint * 0.2f.lit))
        val balancedB = let("balancedB", exposed.b * (1f.lit - temp * 0.2f.lit + tint * 0.1f.lit))
        val balanced = let("balanced", max(vec3(balancedR, balancedG, balancedB), vec3(0f.lit)))

        // 3. Lift / Gamma / Gain
        val liftVal = let("liftVal", lift.xyz)
        val gammaVal = let("gammaVal", max(gamma.xyz, vec3(0.001f.lit)))
        val gainVal = let("gainVal", gain.xyz)
        val lifted = let("lifted", max(balanced * (vec3(1f.lit) - liftVal) + liftVal, vec3(0f.lit)))
        val gained = let("gained", lifted * gainVal)
        val graded = let("graded", pow(max(gained, vec3(0f.lit)), vec3(1f.lit) / gammaVal))

        // 4. Contrast around 0.18 mid-gray
        val contrast = let("contrast", exposureAndContrast.y)
        val contrasted = let("contrasted", max((graded - vec3(0.18f.lit)) * contrast + vec3(0.18f.lit), vec3(0f.lit)))

        // 5. Saturation
        val saturation = let("saturation", exposureAndContrast.z)
        val luma = let("luma", dot(contrasted, vec3(0.2126f.lit, 0.7152f.lit, 0.0722f.lit)))
        val saturated = let("saturated", max(mix(vec3(luma), contrasted, saturation), vec3(0f.lit)))

        // 6. Khronos PBR Neutral Tone Curve
        val startCompression = 0.76f.lit
        val desaturation = 0.15f.lit
        val low = let("low", min(saturated.r, min(saturated.g, saturated.b)))
        val offset = let("offset", select(0.04f.lit, low - 6.25f.lit * low * low, low lt 0.08f.lit))
        val shifted = let("shifted", max(saturated - vec3(offset), vec3(0f.lit)))
        val peak = let("peak", max(shifted.r, max(shifted.g, shifted.b)))
        val shoulder = 1f.lit - startCompression
        val newPeak = let("newPeak", 1f.lit - shoulder * shoulder / (peak + shoulder - startCompression))
        val fade = let("fade", 1f.lit - 1f.lit / (desaturation * (peak - newPeak) + 1f.lit))
        val mapped = let(
            "mapped",
            select(shifted, mix(shifted * (newPeak / peak), vec3(newPeak), fade), peak gt startCompression),
        )

        // 7. Gamma 2.2 encode & alpha luma for downstream FXAA
        val encoded = let("encoded", pow(max(mapped, vec3(0f.lit)), vec3(1f.lit / 2.2f.lit)))
        val finalLuma = let("finalLuma", dot(encoded, vec3(0.2126f.lit, 0.7152f.lit, 0.0722f.lit)))
        colorOutput(vec4(encoded, finalLuma))
    }

    val ToneMappingGradeShader: AslShaderDefinition = shader(TONE_MAPPING_GRADE_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val exposureAndContrast by uniforms.field(GpuDataShape.Vec4)
        val whiteBalance by uniforms.field(GpuDataShape.Vec4)
        val lift by uniforms.field(GpuDataShape.Vec4)
        val gamma by uniforms.field(GpuDataShape.Vec4)
        val gain by uniforms.field(GpuDataShape.Vec4)

        val sceneTexture by texture2d(group = 0, binding = 1)
        val sceneSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            val rawScene = let("rawScene", textureSample(sceneTexture, sceneSampler, uv).rgb)
            applyGradingAndToneMap(rawScene, exposureAndContrast, whiteBalance, lift, gamma, gain)
        }
    }

    val ToneMappingGradeBloomShader: AslShaderDefinition = shader(TONE_MAPPING_GRADE_BLOOM_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val exposureAndContrast by uniforms.field(GpuDataShape.Vec4)
        val whiteBalance by uniforms.field(GpuDataShape.Vec4)
        val lift by uniforms.field(GpuDataShape.Vec4)
        val gamma by uniforms.field(GpuDataShape.Vec4)
        val gain by uniforms.field(GpuDataShape.Vec4)

        val sceneTexture by texture2d(group = 0, binding = 1)
        val sceneSampler by sampler(group = 0, binding = 2)
        val bloomTexture by texture2d(group = 0, binding = 3)
        val bloomSampler by sampler(group = 0, binding = 4)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            val rawScene = let("rawScene", textureSample(sceneTexture, sceneSampler, uv).rgb)
            val bloom = let("bloom", textureSample(bloomTexture, bloomSampler, uv).rgb)
            val radiance = let("radiance", rawScene + bloom * exposureAndContrast.w)
            applyGradingAndToneMap(radiance, exposureAndContrast, whiteBalance, lift, gamma, gain)
        }
    }

    val BloomBrightPassShader: AslShaderDefinition = shader(BLOOM_BRIGHT_PASS_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val bloomParams by uniforms.field(GpuDataShape.Vec4)

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
            val color = let("color", textureSample(sourceTexture, sourceSampler, uv))
            val luma = let("luma", max(color.r, max(color.g, color.b)))
            val threshold = let("threshold", bloomParams.x)
            val knee = let("knee", max(bloomParams.y, 0.0001f.lit))

            val soft = let("soft", clamp(luma - threshold + knee, 0f.lit, 2f.lit * knee))
            val softWeight = let("softWeight", soft * soft / (4f.lit * knee + 0.00001f.lit))
            val hardWeight = let("hardWeight", max(0f.lit, luma - threshold))
            val contribution = let("contribution", max(softWeight, hardWeight) / max(luma, 0.00001f.lit))
            val bright = let("bright", color.rgb * contribution)
            colorOutput(vec4(max(bright, vec3(0f.lit)), 1f.lit))
        }
    }

    val FxaaShader: AslShaderDefinition = shader(FXAA_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val fxaaParams by uniforms.field(GpuDataShape.Vec4)

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
            val rcpFrame = let("rcpFrame", fxaaParams.xy)
            val colorM = let("colorM", textureSample(sourceTexture, sourceSampler, uv))
            val lumaM = let("lumaM", colorM.a)
            val lumaN = let("lumaN", textureSample(sourceTexture, sourceSampler, uv + vec2(0f.lit, -rcpFrame.y)).a)
            val lumaS = let("lumaS", textureSample(sourceTexture, sourceSampler, uv + vec2(0f.lit, rcpFrame.y)).a)
            val lumaW = let("lumaW", textureSample(sourceTexture, sourceSampler, uv + vec2(-rcpFrame.x, 0f.lit)).a)
            val lumaE = let("lumaE", textureSample(sourceTexture, sourceSampler, uv + vec2(rcpFrame.x, 0f.lit)).a)

            val rangeMin = let("rangeMin", min(lumaM, min(min(lumaN, lumaS), min(lumaW, lumaE))))
            val rangeMax = let("rangeMax", max(lumaM, max(max(lumaN, lumaS), max(lumaW, lumaE))))
            val range = let("range", rangeMax - rangeMin)

            val edgeThreshold = let("edgeThreshold", max(0.0833f.lit, rangeMax * 0.125f.lit))
            val colorResult = variable("colorResult", colorM)

            iff(range gt edgeThreshold) {
                val lumaNW = let("lumaNW", textureSample(sourceTexture, sourceSampler, uv + vec2(-rcpFrame.x, -rcpFrame.y)).a)
                val lumaNE = let("lumaNE", textureSample(sourceTexture, sourceSampler, uv + vec2(rcpFrame.x, -rcpFrame.y)).a)
                val lumaSW = let("lumaSW", textureSample(sourceTexture, sourceSampler, uv + vec2(-rcpFrame.x, rcpFrame.y)).a)
                val lumaSE = let("lumaSE", textureSample(sourceTexture, sourceSampler, uv + vec2(rcpFrame.x, rcpFrame.y)).a)

                val lumaL = let("lumaL", (lumaN + lumaS + lumaW + lumaE) * 0.25f.lit)
                val rangeL = let("rangeL", abs(lumaL - lumaM))
                val blendL = let("blendL", max(0f.lit, (rangeL / range) - 0.2f.lit) * 1.25f.lit)

                val edgeH = let(
                    "edgeH",
                    abs(lumaNW + lumaNE - 2f.lit * lumaN) +
                        2f.lit * abs(lumaW + lumaE - 2f.lit * lumaM) +
                        abs(lumaSW + lumaSE - 2f.lit * lumaS),
                )
                val edgeV = let(
                    "edgeV",
                    abs(lumaNW + lumaSW - 2f.lit * lumaW) +
                        2f.lit * abs(lumaN + lumaS - 2f.lit * lumaM) +
                        abs(lumaNE + lumaSE - 2f.lit * lumaE),
                )
                val isHorz = let("isHorz", edgeH gt edgeV)

                val stepLength = let("stepLength", select(rcpFrame.x, rcpFrame.y, isHorz))
                val lumaNeg = let("lumaNeg", select(lumaW, lumaN, isHorz))
                val lumaPos = let("lumaPos", select(lumaE, lumaS, isHorz))
                val gradNeg = let("gradNeg", abs(lumaNeg - lumaM))
                val gradPos = let("gradPos", abs(lumaPos - lumaM))
                val isNegSteeper = let("isNegSteeper", gradNeg gt gradPos)

                val stepSign = let("stepSign", select(1f.lit, -1f.lit, isNegSteeper))
                val offset = let(
                    "offset",
                    select(
                        vec2(stepSign * stepLength * 0.5f.lit, 0f.lit),
                        vec2(0f.lit, stepSign * stepLength * 0.5f.lit),
                        isHorz,
                    ),
                )
                val subpixelOffset = let("subpixelOffset", offset * blendL)
                val filtered = let("filtered", textureSample(sourceTexture, sourceSampler, uv + subpixelOffset))
                assign(colorResult, vec4(filtered.rgb, 1f.lit))
            }

            colorOutput(colorResult)
        }
    }

    val ToneMappingGradeSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = TONE_MAPPING_GRADE_SHADER_ID,
            wgslSource = ToneMappingGradeShader.emitWgsl(),
            groupBindings = ToneMappingGradeShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = ToneMappingGradeUniformLayout,
        )
    }

    val ToneMappingGradeBloomSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = TONE_MAPPING_GRADE_BLOOM_SHADER_ID,
            wgslSource = ToneMappingGradeBloomShader.emitWgsl(),
            groupBindings = ToneMappingGradeBloomShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = ToneMappingGradeUniformLayout,
        )
    }

    val BloomBrightPassSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = BLOOM_BRIGHT_PASS_SHADER_ID,
            wgslSource = BloomBrightPassShader.emitWgsl(),
            groupBindings = BloomBrightPassShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = BloomBrightPassUniformLayout,
        )
    }

    val FxaaSpec: ImagePassShaderSpec by lazy {
        ImagePassShaderSpec(
            id = FXAA_SHADER_ID,
            wgslSource = FxaaShader.emitWgsl(),
            groupBindings = FxaaShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = ImageEffectVertexFormat,
            uniformLayout = FxaaUniformLayout,
        )
    }

    fun packToneMappingGradeUniforms(
        exposure: Float = 1.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        bloomIntensity: Float = 0.0f,
        whiteBalanceTemperature: Float = 0.0f,
        whiteBalanceTint: Float = 0.0f,
        lift: FloatArray = floatArrayOf(0f, 0f, 0f),
        gamma: FloatArray = floatArrayOf(1f, 1f, 1f),
        gain: FloatArray = floatArrayOf(1f, 1f, 1f),
    ): FloatArray = UniformWriter(ToneMappingGradeUniformLayout)
        .put(ExposureAndContrastField, exposure.coerceAtLeast(0.001f), contrast, saturation, bloomIntensity)
        .put(WhiteBalanceField, whiteBalanceTemperature.coerceIn(-1f, 1f), whiteBalanceTint.coerceIn(-1f, 1f), 0f, 0f)
        .put(LiftField, lift[0], lift[1], lift[2], 0f)
        .put(GammaField, gamma[0].coerceAtLeast(0.001f), gamma[1].coerceAtLeast(0.001f), gamma[2].coerceAtLeast(0.001f), 0f)
        .put(GainField, gain[0].coerceAtLeast(0f), gain[1].coerceAtLeast(0f), gain[2].coerceAtLeast(0f), 0f)
        .build()

    fun packBloomBrightPassUniforms(threshold: Float, knee: Float): FloatArray =
        UniformWriter(BloomBrightPassUniformLayout)
            .put(BloomParamsField, threshold.coerceAtLeast(0f), knee.coerceIn(0f, 1f), 0f, 0f)
            .build()

    fun packFxaaUniforms(width: Int, height: Int, qualityTier: Int = 1): FloatArray =
        UniformWriter(FxaaUniformLayout)
            .put(
                FxaaParamsField,
                1f / width.coerceAtLeast(1).toFloat(),
                1f / height.coerceAtLeast(1).toFloat(),
                qualityTier.toFloat(),
                0f,
            )
            .build()
}
