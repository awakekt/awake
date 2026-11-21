/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.pipeline.MultisampleState

/**
 * How the 3D scene's edges are smoothed: the anti-aliasing a settings screen offers. Entries run
 * from the cheapest to the most expensive, and a mode the device lacks resolves to the nearest
 * cheaper one it has ([resolve]).
 *
 * Only the scene is smoothed. Shadow maps and UI stay single-sample, and the multisampled scene is
 * resolved before post-processing and UI draw, so text and UI edges stay crisp in every mode.
 *
 * @property sampleCount The scene pass's raster samples per pixel in this mode.
 */
enum class AntiAliasing(val sampleCount: Int) {
    /** One sample and no filter. */
    Off(1),

    /** One sample, then the post-processing stack's FXAA stage on the scene's colour, before UI. */
    Fxaa(1),

    /** Two samples per pixel in the scene pass. WebGPU offers no 2x, so it resolves to [Fxaa] there. */
    Msaa2x(2),

    /** Four samples per pixel in the scene pass, which both WebGPU and Vulkan devices offer. */
    Msaa4x(4),

    /** Eight samples per pixel in the scene pass, on a Vulkan device that offers it. */
    Msaa8x(8),
    ;

    /** Whether this mode multisamples the scene pass. */
    val isMultisampled: Boolean get() = sampleCount > 1

    /**
     * The scene pass's raster state in this mode, from the render plan's [plan] state: this mode's
     * sample count, keeping the plan's alpha to coverage while the pass stays multisampled.
     */
    fun rasterState(plan: MultisampleState): MultisampleState =
        if (isMultisampled) plan.copy(count = sampleCount) else MultisampleState.Single

    /** The modes a device offers, and how a request resolves against them. */
    companion object {
        /**
         * The modes a device whose scene pass can rasterize at [supportedSampleCounts] offers. [Off]
         * and [Fxaa] need one sample, which every device has, so they are always offered.
         */
        fun supported(supportedSampleCounts: Set<Int>): Set<AntiAliasing> =
            entries.filterTo(LinkedHashSet()) { !it.isMultisampled || it.sampleCount in supportedSampleCounts }

        /**
         * [requested] when the device offers it, else the most expensive mode cheaper than it that the
         * device offers, reporting the change once through [report]. Both backends resolve through this,
         * so a mode falls back the same way everywhere.
         */
        fun resolve(
            requested: AntiAliasing,
            supportedSampleCounts: Set<Int>,
            report: (String) -> Unit = ::println,
        ): AntiAliasing {
            val offered = supported(supportedSampleCounts)
            val selected = entries.last { it.ordinal <= requested.ordinal && it in offered }
            if (selected != requested) {
                report("Anti-aliasing requested=$requested selected=$selected (sample counts=${supportedSampleCounts.sorted()}).")
            }
            return selected
        }

        /**
         * The mode a renderer whose scene pass is built for [current] is in when asked for
         * [requested] without rebuilding it (#1791): [requested] when both need one sample and
         * [offered] holds it, so [Off] and [Fxaa] switch between frames, and [current] otherwise.
         * A multisampled mode changes the scene pass's attachments and pipelines, which the engine
         * builds once, at start.
         */
        fun switchWithoutRebuild(current: AntiAliasing, requested: AntiAliasing, offered: Set<AntiAliasing>): AntiAliasing =
            if (!current.isMultisampled && !requested.isMultisampled && requested in offered) requested else current

        /** The multisampled mode a scene pass at [sampleCount] samples is in, or [Off] at one sample. */
        fun forSampleCount(sampleCount: Int): AntiAliasing =
            requireNotNull(entries.firstOrNull { it.sampleCount == sampleCount && it != Fxaa }) {
                "Sample count must be 1, 2, 4 or 8; got $sampleCount."
            }
    }
}
