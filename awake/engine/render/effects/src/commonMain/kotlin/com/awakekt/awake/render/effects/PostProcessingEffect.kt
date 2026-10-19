/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.pipeline.EffectQuality

/**
 * Common interface for post-processing stack effect stages.
 */
sealed interface PostProcessingEffect {
    /** Whether this effect is active and should execute. */
    val enabled: Boolean
}

/**
 * Tone-mapping and color grading stage.
 * - Exposure scaling
 * - White balance (temperature and tint)
 * - Lift / Gamma / Gain shadow, midtone, and highlight controls
 * - Contrast and saturation adjustments
 * - Khronos PBR Neutral tone curve
 * - sRGB / Gamma 2.2 encode
 *
 * @property exposure Radiance multiplier, must be > 0.
 * @property whiteBalanceTemperature Cool (-1) to warm (+1) temperature shift.
 * @property whiteBalanceTint Green (-1) to magenta (+1) tint shift.
 * @property lift Shadow adjustment (rgb offset).
 * @property gamma Midtone adjustment (rgb exponent).
 * @property gain Highlight adjustment (rgb multiplier).
 * @property contrast Contrast curve slope centered at mid-gray 0.18.
 * @property saturation Colorfulness scale (0 = monochrome, 1 = authored, >1 = vibrant).
 * @property enabled Whether tone mapping and grading is active.
 */
data class ToneMappingGradeEffect(
    val exposure: Float = 1.0f,
    val whiteBalanceTemperature: Float = 0.0f,
    val whiteBalanceTint: Float = 0.0f,
    val lift: FloatArray = floatArrayOf(0f, 0f, 0f),
    val gamma: FloatArray = floatArrayOf(1f, 1f, 1f),
    val gain: FloatArray = floatArrayOf(1f, 1f, 1f),
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    override val enabled: Boolean = true,
) : PostProcessingEffect {
    init {
        require(exposure > 0f) { "exposure must be > 0, was $exposure." }
        require(contrast >= 0f) { "contrast must be >= 0, was $contrast." }
        require(saturation >= 0f) { "saturation must be >= 0, was $saturation." }
        require(lift.size == 3) { "lift must have 3 components." }
        require(gamma.size == 3) { "gamma must have 3 components." }
        require(gain.size == 3) { "gain must have 3 components." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToneMappingGradeEffect) return false
        return exposure == other.exposure &&
            whiteBalanceTemperature == other.whiteBalanceTemperature &&
            whiteBalanceTint == other.whiteBalanceTint &&
            lift.contentEquals(other.lift) &&
            gamma.contentEquals(other.gamma) &&
            gain.contentEquals(other.gain) &&
            contrast == other.contrast &&
            saturation == other.saturation &&
            enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = exposure.hashCode()
        result = 31 * result + whiteBalanceTemperature.hashCode()
        result = 31 * result + whiteBalanceTint.hashCode()
        result = 31 * result + lift.contentHashCode()
        result = 31 * result + gamma.contentHashCode()
        result = 31 * result + gain.contentHashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}

/**
 * Bloom bright-glow stage.
 * - Bright-pass threshold with soft knee
 * - Multi-scale dual-filter (Kawase) downsample/upsample chain
 * - Additive composite into linear HDR scene radiance during tone-mapping
 *
 * @property intensity Multiplier applied to upsampled bloom before adding to scene radiance.
 * @property threshold Minimum radiance required to trigger bloom.
 * @property knee Soft knee width in [0..1] range for gradual bloom threshold transition.
 * @property radius Blur spread radius in pixels.
 * @property enabled Whether bloom is active.
 */
data class BloomEffect(
    val intensity: Float = 0.5f,
    val threshold: Float = 1.0f,
    val knee: Float = 0.5f,
    val radius: Float = 16.0f,
    override val enabled: Boolean = true,
) : PostProcessingEffect {
    init {
        require(intensity >= 0f) { "intensity must be >= 0, was $intensity." }
        require(threshold >= 0f) { "threshold must be >= 0, was $threshold." }
        require(knee in 0f..1f) { "knee must be in [0, 1], was $knee." }
        require(radius >= 0f) { "radius must be >= 0, was $radius." }
    }
}

/**
 * Fast Approximate Anti-Aliasing (FXAA 3.11) stage:
 * Runs post tone-mapping on display-referred LDR color.
 *
 * @property quality Anti-aliasing quality preset.
 * @property enabled Whether FXAA is active.
 */
data class FxaaEffect(
    val quality: EffectQuality = EffectQuality.High,
    override val enabled: Boolean = true,
) : PostProcessingEffect

/**
 * Data-driven custom image-effect stage referencing a registered shader in [ImagePassShaderRegistry].
 *
 * @property shaderId Identifier of the registered [ImagePassShaderSpec].
 * @property uniforms Packed float uniform block for the shader.
 * @property enabled Whether this custom effect is active.
 */
data class CustomPostEffect(
    val shaderId: String,
    val uniforms: FloatArray = FloatArray(0),
    override val enabled: Boolean = true,
) : PostProcessingEffect {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomPostEffect) return false
        return shaderId == other.shaderId &&
            uniforms.contentEquals(other.uniforms) &&
            enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = shaderId.hashCode()
        result = 31 * result + uniforms.contentHashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}
