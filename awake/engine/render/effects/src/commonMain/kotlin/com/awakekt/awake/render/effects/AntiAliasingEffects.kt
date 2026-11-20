/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.AntiAliasing

/**
 * The post-processing effects a camera runs when the renderer's anti-aliasing is [antiAliasing]
 * (#1791): [effects] as listed, plus an [FxaaEffect] at [quality] when the mode is
 * [AntiAliasing.Fxaa] and [effects] have no FXAA stage that runs.
 *
 * The mode is an app setting and [effects] are scene content, so the setting can add the stage but
 * never removes or retunes one the scene lists. [EffectQuality.Off] still adds FXAA, at
 * [EffectQuality.Low]: the app asked for FXAA by name, and Off would drop the stage it asked for.
 *
 * Returns [effects] itself when nothing is added, so a camera with no FXAA mode allocates nothing.
 */
fun effectsForAntiAliasing(
    effects: List<PostProcessingEffect>,
    antiAliasing: AntiAliasing,
    quality: EffectQuality,
): List<PostProcessingEffect> {
    if (antiAliasing != AntiAliasing.Fxaa || effects.runsFxaa()) return effects
    return effects + FxaaEffect(quality = if (quality == EffectQuality.Off) EffectQuality.Low else quality)
}

/** Whether one of these effects is an FXAA stage that runs. By index: called every frame. */
private fun List<PostProcessingEffect>.runsFxaa(): Boolean {
    for (index in indices) {
        val effect = this[index]
        if (effect is FxaaEffect && effect.enabled && effect.quality != EffectQuality.Off) return true
    }
    return false
}
