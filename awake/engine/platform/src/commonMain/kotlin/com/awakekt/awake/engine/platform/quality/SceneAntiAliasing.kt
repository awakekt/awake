/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.AntiAliasing

/**
 * The anti-aliasing an engine built its scene pass for (#1791): what it resolved, what the device
 * offers, and the raster state its scene pipelines and attachments use. Both backends make it through
 * [of], so a mode resolves the same way on each; a renderer reports [mode] and [supported] back.
 *
 * @property mode The mode in effect, after [AntiAliasing.resolve].
 * @property supported The modes the device offers.
 * @property rasterState The scene pass's raster state: [mode]'s sample count and the plan's coverage.
 */
data class SceneAntiAliasing(
    val mode: AntiAliasing,
    val supported: Set<AntiAliasing>,
    val rasterState: MultisampleState,
) {
    /** The single place a requested mode becomes a device's scene pass. */
    companion object {
        /**
         * Resolves [requested] against a device whose scene pass rasterizes at [sampleCounts], keeping
         * [plan]'s alpha to coverage while the pass stays multisampled, and reports a fallback once.
         */
        fun of(
            requested: AntiAliasing,
            plan: MultisampleState,
            sampleCounts: Set<Int>,
            report: (String) -> Unit,
        ): SceneAntiAliasing {
            val mode = AntiAliasing.resolve(requested, sampleCounts, report)
            return SceneAntiAliasing(mode, AntiAliasing.supported(sampleCounts), mode.rasterState(plan))
        }
    }
}
