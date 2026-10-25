/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassColorEncoding
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Builds backend-neutral [ImagePassChain] instances for a camera's post-processing stack.
 */
object PostProcessingStack {
    const val TRANSIENT_BLOOM_BRIGHT_ID: String = "bloom_bright"
    const val TRANSIENT_LDR_SCENE_ID: String = "ldr_scene_color"

    fun levelTargetId(level: Int): String = "bloom_level_$level"

    /**
     * Builds an [ImagePassChain] executing the ordered list of [effects] inside [targetContext].
     *
     * The tone-mapping step follows [ImagePassTargetContext.colorEncoding]:
     * - a [ToneMappingGradeEffect] in [effects] runs as listed, in its own [ToneMappingMode];
     * - with none listed, a [ImagePassColorEncoding.LinearHdr] target gets the default
     *   [ToneMappingMode.PbrNeutral] step, since its colour is not displayable yet;
     * - with none listed, a [ImagePassColorEncoding.DisplayReferred] target gets no tone-mapping
     *   step: FXAA and custom effects read a copy of the target's colour as the scene drew it.
     *   Only bloom, which composites in that step, adds a [ToneMappingMode.Passthrough] one.
     *
     * Returns [ImagePassChain.Empty] when [targetContext] has [EffectQuality.Off], when dimensions
     * are zero or negative, or when no enabled effect leaves a step to run.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    fun buildChain(
        effects: List<PostProcessingEffect>,
        targetContext: ImagePassTargetContext,
    ): ImagePassChain {
        if (targetContext.effectQuality == EffectQuality.Off ||
            targetContext.width <= 0 ||
            targetContext.height <= 0
        ) {
            return ImagePassChain.Empty
        }

        val active = effects.filter { it.enabled }
        if (active.isEmpty()) return ImagePassChain.Empty

        val listedToneMapping = active.filterIsInstance<ToneMappingGradeEffect>().firstOrNull()
        val bloom = active.filterIsInstance<BloomEffect>().firstOrNull()?.takeIf { it.intensity > 0f }
        val fxaa = active.filterIsInstance<FxaaEffect>().firstOrNull()?.takeIf { it.quality != EffectQuality.Off }
        val customEffects = active.filterIsInstance<CustomPostEffect>()

        val targets = mutableListOf<ImagePassTargetSpec>()
        val steps = mutableListOf<ImagePassStep>()

        val linearSampler = ImagePassSamplerSpec(
            filter = ImagePassFilterMode.Linear,
            addressMode = ImagePassAddressMode.ClampToEdge,
        )

        // 1. Bloom downsample/upsample chain
        var finalBloomTargetId: String? = null
        if (bloom != null) {
            val blurChain = BlurChain.forRadius(bloom.radius, targetContext.effectQuality)
            if (blurChain != null) {
                val brightWidth = (targetContext.width / 2).coerceAtLeast(1)
                val brightHeight = (targetContext.height / 2).coerceAtLeast(1)

                // Bright-pass transient target
                targets += ImagePassTargetSpec(
                    id = TRANSIENT_BLOOM_BRIGHT_ID,
                    width = brightWidth,
                    height = brightHeight,
                    format = ImagePassTargetFormat.Rgba16Float,
                )

                // Dual-filter downsample/upsample transient targets
                for (level in 1..blurChain.iterations) {
                    targets += ImagePassTargetSpec(
                        id = levelTargetId(level),
                        width = BlurChain.levelSize(brightWidth, level),
                        height = BlurChain.levelSize(brightHeight, level),
                        format = ImagePassTargetFormat.Rgba16Float,
                    )
                }

                // Step 1: Bright pass extraction
                steps += ImagePassStep(
                    shader = ImagePassShaderRegistry.require(PostProcessingShaders.BLOOM_BRIGHT_PASS_SHADER_ID),
                    inputs = listOf(
                        ImagePassTextureBinding(
                            binding = 1,
                            source = ImagePassSource.DestinationSnapshot,
                            sampler = linearSampler,
                        ),
                    ),
                    destination = ImagePassDestination.TransientTarget(TRANSIENT_BLOOM_BRIGHT_ID),
                    uniformData = PostProcessingShaders.packBloomBrightPassUniforms(bloom.threshold, bloom.knee),
                    quadVertices = buildImagePassQuadVertices(
                        rect = ImagePassRect(0f, 0f, brightWidth.toFloat(), brightHeight.toFloat()),
                        targetWidth = brightWidth,
                        targetHeight = brightHeight,
                        clipSpace = targetContext.clipSpace,
                    ),
                    quadIndices = ImagePassQuadIndices,
                    clearDestination = true,
                )

                // Downsample passes: level 1 from bright pass, then level k from level k-1
                for (level in 1..blurChain.iterations) {
                    val src = if (level == 1) {
                        ImagePassSource.TransientTarget(TRANSIENT_BLOOM_BRIGHT_ID)
                    } else {
                        ImagePassSource.TransientTarget(levelTargetId(level - 1))
                    }
                    val dst = ImagePassDestination.TransientTarget(levelTargetId(level))
                    val srcW = if (level == 1) brightWidth else BlurChain.levelSize(brightWidth, level - 1)
                    val srcH = if (level == 1) brightHeight else BlurChain.levelSize(brightHeight, level - 1)
                    val vertices = buildBlurPassQuad(0, 0, srcW, srcH, blurChain.offset, isUpsample = false)

                    steps += ImagePassStep(
                        shader = ImagePassShaderRegistry.require(BlurEffectShaders.BLUR_PASS_SHADER_ID),
                        inputs = listOf(
                            ImagePassTextureBinding(
                                binding = 0,
                                source = src,
                                sampler = linearSampler,
                            ),
                        ),
                        destination = dst,
                        quadVertices = vertices,
                        quadIndices = ImagePassQuadIndices,
                    )
                }

                // Upsample passes: level k from level k+1 down to outputLevel
                for (level in blurChain.iterations - 1 downTo blurChain.outputLevel) {
                    val src = ImagePassSource.TransientTarget(levelTargetId(level + 1))
                    val dst = ImagePassDestination.TransientTarget(levelTargetId(level))
                    val srcW = BlurChain.levelSize(brightWidth, level + 1)
                    val srcH = BlurChain.levelSize(brightHeight, level + 1)
                    val vertices = buildBlurPassQuad(0, 0, srcW, srcH, blurChain.offset, isUpsample = true)

                    steps += ImagePassStep(
                        shader = ImagePassShaderRegistry.require(BlurEffectShaders.BLUR_PASS_SHADER_ID),
                        inputs = listOf(
                            ImagePassTextureBinding(
                                binding = 0,
                                source = src,
                                sampler = linearSampler,
                            ),
                        ),
                        destination = dst,
                        quadVertices = vertices,
                        quadIndices = ImagePassQuadIndices,
                    )
                }

                finalBloomTargetId = levelTargetId(blurChain.outputLevel)
            }
        }

        // 2. Tone-mapping & Color Grading
        val toneMapping = listedToneMapping ?: when {
            targetContext.colorEncoding == ImagePassColorEncoding.LinearHdr -> ToneMappingGradeEffect()
            finalBloomTargetId != null -> ToneMappingGradeEffect(mode = ToneMappingMode.Passthrough)
            else -> null
        }
        val hasSubsequentSteps = fxaa != null || customEffects.isNotEmpty()
        if (toneMapping == null && !hasSubsequentSteps) return ImagePassChain.Empty
        // FXAA and custom effects read display colour from TRANSIENT_LDR_SCENE_ID: the
        // tone-mapping step's output, or else a copy of the target's own colour, which a
        // display-referred scene already wrote ready for display. A copy, not the snapshot itself:
        // a step that samples the snapshot while drawing into the active target reads the image it
        // writes on a backend that snapshots in place.
        val ldrSource = ImagePassSource.TransientTarget(TRANSIENT_LDR_SCENE_ID)
        if (hasSubsequentSteps) {
            targets += ImagePassTargetSpec(
                id = TRANSIENT_LDR_SCENE_ID,
                width = targetContext.width,
                height = targetContext.height,
                format = if (toneMapping != null) ImagePassTargetFormat.Rgba8Unorm else ImagePassTargetFormat.DestinationMatch,
            )
        }
        if (toneMapping == null) {
            steps += copyTargetStep(targetContext, linearSampler)
        } else {
            steps += toneMappingStep(
                toneMapping = toneMapping,
                bloomIntensity = if (finalBloomTargetId != null) bloom?.intensity ?: 0f else 0f,
                bloomTargetId = finalBloomTargetId,
                toLdrTarget = hasSubsequentSteps,
                targetContext = targetContext,
                sampler = linearSampler,
            )
        }

        // 3. Custom post-processing stages
        for (custom in customEffects) {
            val customSpec = ImagePassShaderRegistry[custom.shaderId] ?: continue
            steps += ImagePassStep(
                shader = customSpec,
                inputs = listOf(
                    ImagePassTextureBinding(
                        binding = 1,
                        source = ldrSource,
                        sampler = linearSampler,
                    ),
                ),
                destination = ImagePassDestination.ActiveTarget(blendEnabled = false),
                uniformData = custom.uniforms,
                quadVertices = buildImagePassQuadVertices(
                    rect = ImagePassRect(0f, 0f, targetContext.width.toFloat(), targetContext.height.toFloat()),
                    targetWidth = targetContext.width,
                    targetHeight = targetContext.height,
                    clipSpace = targetContext.clipSpace,
                ),
                quadIndices = ImagePassQuadIndices,
            )
        }

        // 4. FXAA stage
        if (fxaa != null) {
            steps += ImagePassStep(
                shader = ImagePassShaderRegistry.require(PostProcessingShaders.FXAA_SHADER_ID),
                inputs = listOf(
                    ImagePassTextureBinding(
                        binding = 1,
                        source = ldrSource,
                        sampler = linearSampler,
                    ),
                ),
                destination = ImagePassDestination.ActiveTarget(blendEnabled = false),
                uniformData = PostProcessingShaders.packFxaaUniforms(
                    width = targetContext.width,
                    height = targetContext.height,
                    qualityTier = fxaa.quality.ordinal,
                    lumaInAlpha = toneMapping != null,
                ),
                quadVertices = buildImagePassQuadVertices(
                    rect = ImagePassRect(0f, 0f, targetContext.width.toFloat(), targetContext.height.toFloat()),
                    targetWidth = targetContext.width,
                    targetHeight = targetContext.height,
                    clipSpace = targetContext.clipSpace,
                ),
                quadIndices = ImagePassQuadIndices,
            )
        }

        return ImagePassChain(
            sourceBounds = ImagePassRect(0f, 0f, targetContext.width.toFloat(), targetContext.height.toFloat()),
            transientTargets = targets,
            steps = steps,
        )
    }

    /** Copies the target's colour, as the scene drew it, into [TRANSIENT_LDR_SCENE_ID]. */
    private fun copyTargetStep(targetContext: ImagePassTargetContext, sampler: ImagePassSamplerSpec): ImagePassStep =
        ImagePassStep(
            shader = ImagePassShaderRegistry.require(ImageEffectShaders.COPY_SHADER_ID),
            inputs = listOf(
                ImagePassTextureBinding(binding = 1, source = ImagePassSource.DestinationSnapshot, sampler = sampler),
            ),
            destination = ImagePassDestination.TransientTarget(TRANSIENT_LDR_SCENE_ID),
            uniformData = ImageEffectShaders.packCopyUniforms(0f, 0f, 1f, 1f),
            quadVertices = buildImagePassQuadVertices(
                rect = ImagePassRect(0f, 0f, targetContext.width.toFloat(), targetContext.height.toFloat()),
                targetWidth = targetContext.width,
                targetHeight = targetContext.height,
                clipSpace = targetContext.clipSpace,
            ),
            quadIndices = ImagePassQuadIndices,
            clearDestination = true,
        )

    /** The tone-mapping and grading step: reads the target's colour, plus bloom when there is some. */
    @Suppress("LongParameterList")
    private fun toneMappingStep(
        toneMapping: ToneMappingGradeEffect,
        bloomIntensity: Float,
        bloomTargetId: String?,
        toLdrTarget: Boolean,
        targetContext: ImagePassTargetContext,
        sampler: ImagePassSamplerSpec,
    ): ImagePassStep {
        val toneMapDestination = if (toLdrTarget) {
            ImagePassDestination.TransientTarget(TRANSIENT_LDR_SCENE_ID)
        } else {
            ImagePassDestination.ActiveTarget(blendEnabled = false)
        }

        val toneMapShaderId = if (bloomTargetId != null) {
            PostProcessingShaders.TONE_MAPPING_GRADE_BLOOM_SHADER_ID
        } else {
            PostProcessingShaders.TONE_MAPPING_GRADE_SHADER_ID
        }

        val toneMapInputs = mutableListOf<ImagePassTextureBinding>()
        toneMapInputs += ImagePassTextureBinding(
            binding = 1,
            source = ImagePassSource.DestinationSnapshot,
            sampler = sampler,
        )
        if (bloomTargetId != null) {
            toneMapInputs += ImagePassTextureBinding(
                binding = 3,
                source = ImagePassSource.TransientTarget(bloomTargetId),
                sampler = sampler,
            )
        }

        return ImagePassStep(
            shader = ImagePassShaderRegistry.require(toneMapShaderId),
            inputs = toneMapInputs,
            destination = toneMapDestination,
            uniformData = PostProcessingShaders.packToneMappingGradeUniforms(
                exposure = toneMapping.exposure,
                contrast = toneMapping.contrast,
                saturation = toneMapping.saturation,
                bloomIntensity = bloomIntensity,
                whiteBalanceTemperature = toneMapping.whiteBalanceTemperature,
                whiteBalanceTint = toneMapping.whiteBalanceTint,
                lift = toneMapping.lift,
                gamma = toneMapping.gamma,
                gain = toneMapping.gain,
                mode = toneMapping.mode,
            ),
            quadVertices = buildImagePassQuadVertices(
                rect = ImagePassRect(0f, 0f, targetContext.width.toFloat(), targetContext.height.toFloat()),
                targetWidth = targetContext.width,
                targetHeight = targetContext.height,
                clipSpace = targetContext.clipSpace,
            ),
            quadIndices = ImagePassQuadIndices,
        )
    }

    @Suppress("LongParameterList")
    private fun buildBlurPassQuad(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        offset: Float,
        isUpsample: Boolean,
    ): FloatArray {
        val stride = 10
        val data = FloatArray(4 * stride)
        val upsampleFloat = if (isUpsample) 1.0f else 0.0f
        val cl = left.toFloat()
        val ct = top.toFloat()
        val cr = right.toFloat()
        val cb = bottom.toFloat()

        fun writeVertex(index: Int, x: Float, y: Float) {
            val cursor = index * stride
            data[cursor] = x
            data[cursor + 1] = y
            data[cursor + 2] = cl
            data[cursor + 3] = ct
            data[cursor + 4] = cr
            data[cursor + 5] = cb
            data[cursor + 6] = offset
            data[cursor + 7] = upsampleFloat
            data[cursor + 8] = 0f
            data[cursor + 9] = 0f
        }

        writeVertex(0, -1f, -1f)
        writeVertex(1, 1f, -1f)
        writeVertex(2, 1f, 1f)
        writeVertex(3, -1f, 1f)
        return data
    }

    // --- Deterministic CPU Reference Twin ---

    /**
     * Pure Kotlin reference twin evaluating tone-mapping and color grading on a single RGB pixel.
     */
    @Suppress("LongParameterList")
    fun evaluateToneMappingGradeReference(
        radiance: FloatArray,
        exposure: Float = 1.0f,
        whiteBalanceTemperature: Float = 0.0f,
        whiteBalanceTint: Float = 0.0f,
        lift: FloatArray = floatArrayOf(0f, 0f, 0f),
        gamma: FloatArray = floatArrayOf(1f, 1f, 1f),
        gain: FloatArray = floatArrayOf(1f, 1f, 1f),
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        bloom: FloatArray? = null,
        bloomIntensity: Float = 0.0f,
        mode: ToneMappingMode = ToneMappingMode.PbrNeutral,
    ): FloatArray {
        var r = radiance[0] + (bloom?.get(0) ?: 0f) * bloomIntensity
        var g = radiance[1] + (bloom?.get(1) ?: 0f) * bloomIntensity
        var b = radiance[2] + (bloom?.get(2) ?: 0f) * bloomIntensity

        // 1. Exposure
        r *= exposure
        g *= exposure
        b *= exposure

        // 2. White balance
        r = (r * (1f + whiteBalanceTemperature * 0.2f + whiteBalanceTint * 0.1f)).coerceAtLeast(0f)
        g = (g * (1f - whiteBalanceTint * 0.2f)).coerceAtLeast(0f)
        b = (b * (1f - whiteBalanceTemperature * 0.2f + whiteBalanceTint * 0.1f)).coerceAtLeast(0f)

        // 3. Lift / Gamma / Gain
        r = ((r * (1f - lift[0]) + lift[0]).coerceAtLeast(0f) * gain[0]).coerceAtLeast(0f)
            .pow(1f / max(gamma[0], 0.001f))
        g = ((g * (1f - lift[1]) + lift[1]).coerceAtLeast(0f) * gain[1]).coerceAtLeast(0f)
            .pow(1f / max(gamma[1], 0.001f))
        b = ((b * (1f - lift[2]) + lift[2]).coerceAtLeast(0f) * gain[2]).coerceAtLeast(0f)
            .pow(1f / max(gamma[2], 0.001f))

        // 4. Contrast
        r = ((r - 0.18f) * contrast + 0.18f).coerceAtLeast(0f)
        g = ((g - 0.18f) * contrast + 0.18f).coerceAtLeast(0f)
        b = ((b - 0.18f) * contrast + 0.18f).coerceAtLeast(0f)

        // 5. Saturation
        val luma = r * 0.2126f + g * 0.7152f + b * 0.0722f
        r = (luma + (r - luma) * saturation).coerceAtLeast(0f)
        g = (luma + (g - luma) * saturation).coerceAtLeast(0f)
        b = (luma + (b - luma) * saturation).coerceAtLeast(0f)

        if (mode == ToneMappingMode.Passthrough) {
            return floatArrayOf(r, g, b, r * 0.2126f + g * 0.7152f + b * 0.0722f)
        }

        // 6. Khronos PBR Neutral curve
        val startCompression = 0.76f
        val desaturation = 0.15f
        val low = min(r, min(g, b))
        val offset = if (low < 0.08f) low - 6.25f * low * low else 0.04f
        var sr = max(r - offset, 0f)
        var sg = max(g - offset, 0f)
        var sb = max(b - offset, 0f)
        val peak = max(sr, max(sg, sb))
        if (peak > startCompression) {
            val shoulder = 1f - startCompression
            val newPeak = 1f - shoulder * shoulder / (peak + shoulder - startCompression)
            val fade = 1f - 1f / (desaturation * (peak - newPeak) + 1f)
            val factor = newPeak / peak
            sr = sr * factor * (1f - fade) + newPeak * fade
            sg = sg * factor * (1f - fade) + newPeak * fade
            sb = sb * factor * (1f - fade) + newPeak * fade
        }

        // 7. Gamma 2.2 encode
        val encR = max(sr, 0f).pow(1f / 2.2f)
        val encG = max(sg, 0f).pow(1f / 2.2f)
        val encB = max(sb, 0f).pow(1f / 2.2f)
        val finalLuma = encR * 0.2126f + encG * 0.7152f + encB * 0.0722f

        return floatArrayOf(encR, encG, encB, finalLuma)
    }

    /**
     * Pure Kotlin reference twin evaluating bloom bright-pass threshold with soft knee.
     */
    fun evaluateBloomBrightPassReference(
        radiance: FloatArray,
        threshold: Float,
        knee: Float,
    ): FloatArray {
        val luma = max(radiance[0], max(radiance[1], radiance[2]))
        val clampedKnee = max(knee, 0.0001f)
        val soft = (luma - threshold + clampedKnee).coerceIn(0f, 2f * clampedKnee)
        val softWeight = soft * soft / (4f * clampedKnee + 0.00001f)
        val hardWeight = max(0f, luma - threshold)
        val contribution = max(softWeight, hardWeight) / max(luma, 0.00001f)
        return floatArrayOf(
            max(0f, radiance[0] * contribution),
            max(0f, radiance[1] * contribution),
            max(0f, radiance[2] * contribution),
            1.0f,
        )
    }
}
