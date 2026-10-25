/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.postprocessing

import com.awakekt.awake.render.effects.ToneMappingMode
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable post-processing effect descriptor for camera pipelines.
 */
@Serializable
sealed interface ScenePostEffect {
    /** Whether this effect is active in the post-processing stack. */
    val enabled: Boolean
}

/**
 * Single-pass colour grading stage, followed by the Khronos PBR Neutral tone curve and the gamma
 * 2.2 encode unless [mode] is [ToneMappingMode.Passthrough].
 *
 * A scene camera's colour is already display-ready: every lit shader applies exposure, the curve
 * and the encode itself. In a scene, use [ToneMappingMode.Passthrough] to grade it;
 * [ScenePostProcessing.validate] rejects [ToneMappingMode.PbrNeutral], which would encode it twice.
 */
@Serializable
@SerialName("tone_mapping_grade")
data class SceneToneMappingGradeEffect(
    /** Whether tone mapping and color grading are enabled. */
    override val enabled: Boolean = true,
    /** Linear exposure multiplier before tone mapping. */
    val exposure: Float = 1f,
    /** Color contrast adjustment factor. */
    val contrast: Float = 1f,
    /** Color saturation adjustment factor. */
    val saturation: Float = 1f,
    /** Color temperature shift (-1 to 1). */
    val whiteBalanceTemperature: Float = 0f,
    /** Color tint shift (-1 to 1). */
    val whiteBalanceTint: Float = 0f,
    /** RGB shadow lift adjustments. */
    val lift: List<Float> = listOf(0f, 0f, 0f),
    /** RGB midtone gamma adjustments. */
    val gamma: List<Float> = listOf(1f, 1f, 1f),
    /** RGB highlight gain adjustments. */
    val gain: List<Float> = listOf(1f, 1f, 1f),
    /** Whether the tone curve and gamma encode follow grading. */
    val mode: ToneMappingMode = ToneMappingMode.PbrNeutral,
) : ScenePostEffect

/**
 * Bloom bright-pass extraction with soft-knee and dual-filter downsample/upsample blur chain.
 */
@Serializable
@SerialName("bloom")
data class SceneBloomEffect(
    /** Whether bloom bright extraction and blur are enabled. */
    override val enabled: Boolean = true,
    /** Bloom intensity multiplier. */
    val intensity: Float = 0.5f,
    /** Bright-pass threshold luminance. */
    val threshold: Float = 1.0f,
    /** Soft-knee transition width for bright-pass thresholding. */
    val knee: Float = 0.5f,
    /** Blur filter spread radius in pixels. */
    val radius: Float = 8.0f,
) : ScenePostEffect

/**
 * Fast Approximate Anti-Aliasing (FXAA 3.11) post-processing pass on LDR scene colour.
 */
@Serializable
@SerialName("fxaa")
data class SceneFxaaEffect(
    /** Whether FXAA antialiasing is enabled. */
    override val enabled: Boolean = true,
    /** FXAA quality tier. */
    val quality: EffectQuality = EffectQuality.High,
) : ScenePostEffect

/**
 * Custom post-processing shader pass.
 */
@Serializable
@SerialName("custom")
data class SceneCustomPostEffect(
    /** Whether this custom post-processing effect is enabled. */
    override val enabled: Boolean = true,
    /** Registered shader identifier. */
    val shaderId: String,
    /** Custom floating-point parameters uploaded to the shader uniform buffer. */
    val parameters: List<Float> = emptyList(),
) : ScenePostEffect

/**
 * Serializable post-processing stack component attached to a camera.
 *
 * @property enabled Whether this camera's post-processing stack is active.
 * @property effects Ordered list of post-processing effects executed per frame.
 */
@Serializable
@SerialName("post_processing")
data class ScenePostProcessing(
    /** Whether post-processing is enabled for this camera. */
    val enabled: Boolean = true,
    /** Ordered list of post-processing effects executed per frame. */
    val effects: List<ScenePostEffect> = emptyList(),
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        effects.forEachIndexed { index, effect ->
            when (effect) {
                is SceneToneMappingGradeEffect -> addAll(validateToneMapping(index, effect, path))
                is SceneBloomEffect -> addAll(validateBloom(index, effect, path))
                is SceneCustomPostEffect -> addAll(validateCustom(index, effect, path))
                is SceneFxaaEffect -> Unit
            }
        }
        addAll(validateEncoding(path))
    }

    /**
     * Rejects a stack that would tone map and encode colour twice.
     *
     * A scene camera renders display-referred colour: every lit shader already applies exposure,
     * the PBR Neutral curve and the gamma 2.2 encode, and the stack reads that colour back. A
     * `PbrNeutral` step applies both again, lifting and washing out the whole frame. A second
     * enabled tone-mapping step would be ignored, and reads as if it applied too.
     */
    private fun validateEncoding(path: String): List<SceneValidationIssue> = buildList {
        val toneMapping = effects.withIndex().filter { (_, effect) -> effect is SceneToneMappingGradeEffect && effect.enabled }
        toneMapping.forEach { (index, effect) ->
            if ((effect as SceneToneMappingGradeEffect).mode == ToneMappingMode.PbrNeutral) {
                add(
                    SceneValidationIssue(
                        "$path.effects[$index]",
                        "tone_mapping_grade with mode PbrNeutral would encode the frame twice: the camera's colour " +
                            "is already tone mapped and gamma encoded by the scene's lit shaders. Set \"mode\": " +
                            "\"Passthrough\" to grade without the curve, or remove the effect; use the tone_mapping " +
                            "component's exposure for brightness",
                    ),
                )
            }
        }
        toneMapping.drop(1).forEach { (index, _) ->
            add(
                SceneValidationIssue(
                    "$path.effects[$index]",
                    "only one tone_mapping_grade may be enabled; this one would be ignored, and applying it as well " +
                        "would grade and encode the frame a second time",
                ),
            )
        }
    }

    private fun validateToneMapping(
        index: Int,
        effect: SceneToneMappingGradeEffect,
        path: String,
    ): List<SceneValidationIssue> = buildList {
        if (!effect.exposure.isFinite() || effect.exposure <= 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "exposure must be finite and > 0"))
        }
        if (!effect.contrast.isFinite() || effect.contrast < 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "contrast must be finite and >= 0"))
        }
        if (!effect.saturation.isFinite() || effect.saturation < 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "saturation must be finite and >= 0"))
        }
    }

    private fun validateBloom(
        index: Int,
        effect: SceneBloomEffect,
        path: String,
    ): List<SceneValidationIssue> = buildList {
        if (!effect.intensity.isFinite() || effect.intensity < 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "bloom intensity must be finite and >= 0"))
        }
        if (!effect.threshold.isFinite() || effect.threshold < 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "bloom threshold must be finite and >= 0"))
        }
        if (!effect.radius.isFinite() || effect.radius <= 0f) {
            add(SceneValidationIssue("$path.effects[$index]", "bloom radius must be finite and > 0"))
        }
    }

    private fun validateCustom(
        index: Int,
        effect: SceneCustomPostEffect,
        path: String,
    ): List<SceneValidationIssue> = buildList {
        if (effect.shaderId.isBlank()) {
            add(SceneValidationIssue("$path.effects[$index]", "shaderId must not be blank"))
        }
    }
}
