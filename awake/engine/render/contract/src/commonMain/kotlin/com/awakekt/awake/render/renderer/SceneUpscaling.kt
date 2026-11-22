/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * How the scale-up pass filters a scaled 3D scene (`Renderer.renderScale < 1`) onto the presented
 * surface (#1830). Runs inside the existing scale-up pass -- after anti-aliasing and
 * post-processing, before the UI -- with no extra render pass or intermediate target.
 *
 * Combining sharpening with [AntiAliasing.Fxaa] is discouraged: FXAA softens high-contrast edges
 * before the scale-up pass runs, and sharpening an already softened image exaggerates residual
 * stair-stepping and haloing instead of recovering crisp detail. Prefer [AntiAliasing.Off] or a
 * multisampled MSAA mode when enabling sharpening.
 */
enum class SceneUpscaleMode {
    /** Plain bilinear stretch (default). Output is unchanged until a consumer opts in. */
    Off,

    /**
     * Five-tap contrast-adaptive sharpening (AMD FidelityFX CAS non-scaling kernel) over the
     * bilinearly sampled scaled scene. Restores edge contrast across the bilinear ramp at minimal
     * texture-fetch cost.
     */
    Sharpen,

    /**
     * Single-pass twelve-tap edge-thinning spatial upscale and contrast-adaptive sharpening
     * (AMD FidelityFX CAS scaling kernel, up to 4x area / 50% render scale). Preserves diagonal
     * and silhouette edges better than plain bilinear + 5-tap sharpening in the same single pass.
     */
    UpscaleAndSharpen,
}

/**
 * Scale-up pass sharpening setting beside [AntiAliasing] on `RenderQualitySettings` (#1830).
 *
 * @property mode Which filter the scale-up pass runs; [SceneUpscaleMode.Off] keeps the plain
 *   bilinear copy.
 * @property strength Sharpening intensity in `0f..1f` (`0f` = subtle/lowest ringing, `1f` =
 *   strongest edge contrast). Ignored when [mode] is [SceneUpscaleMode.Off].
 */
data class SceneUpscaling(
    val mode: SceneUpscaleMode = SceneUpscaleMode.Off,
    val strength: Float = DEFAULT_STRENGTH,
) {
    init {
        require(strength.isFinite() && strength in 0f..1f) {
            "SceneUpscaling strength must be in 0..1: $strength"
        }
    }

    /** Common presets and defaults. */
    companion object {
        /** Balanced default sharpening strength (moderate contrast recovery with low ringing). */
        const val DEFAULT_STRENGTH: Float = 0.5f

        /** Plain bilinear stretch; the default on every renderer and settings instance. */
        val Off: SceneUpscaling = SceneUpscaling(SceneUpscaleMode.Off, DEFAULT_STRENGTH)

        /** 5-tap contrast-adaptive sharpening at [strength] (`0f..1f`). */
        fun sharpen(strength: Float = DEFAULT_STRENGTH): SceneUpscaling =
            SceneUpscaling(SceneUpscaleMode.Sharpen, strength)

        /** 12-tap edge-thinning upscale and sharpening at [strength] (`0f..1f`). */
        fun upscaleAndSharpen(strength: Float = DEFAULT_STRENGTH): SceneUpscaling =
            SceneUpscaling(SceneUpscaleMode.UpscaleAndSharpen, strength)
    }
}
