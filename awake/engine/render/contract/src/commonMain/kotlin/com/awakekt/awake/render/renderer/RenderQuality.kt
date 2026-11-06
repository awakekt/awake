/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.pipeline.recommendedEffectQuality

/**
 * A graphics quality preset: how many physical pixels the 3D scene may cost, and which
 * [EffectQuality] its image effects run at. [RenderQualityPolicy] turns a preset into a
 * [Renderer.renderScale] for the presented surface.
 */
enum class RenderQuality {
    /** A short-edge budget chosen by the device's [GpuDeviceClass], with its recommended effects. */
    Auto,

    /** The full native physical buffer, at [EffectQuality.High]. */
    High,

    /** A mid short-edge budget, at [EffectQuality.Medium]. */
    Balanced,

    /** The smallest short-edge budget, at [EffectQuality.Low]. */
    Performance,
}

/**
 * The tuning behind [RenderQuality]: the most physical pixels the scene target's short edge may have
 * for each preset that caps it. [RenderQuality.High] has no cap. These are data, not policy: an app
 * passes its own to [RenderQualityPolicy] to tune them.
 *
 * @property balancedShortEdge The short-edge cap for [RenderQuality.Balanced].
 * @property performanceShortEdge The short-edge cap for [RenderQuality.Performance].
 * @property autoShortEdge The short-edge cap for [RenderQuality.Auto], by device class.
 */
data class RenderQualityBudgets(
    val balancedShortEdge: Int = 720,
    val performanceShortEdge: Int = 540,
    val autoShortEdge: Map<GpuDeviceClass, Int> = mapOf(
        GpuDeviceClass.Discrete to 1440,
        GpuDeviceClass.Integrated to 1080,
        GpuDeviceClass.Mobile to 900,
        GpuDeviceClass.Software to 540,
        GpuDeviceClass.Unknown to 1080,
    ),
) {
    init {
        require(balancedShortEdge > 0 && performanceShortEdge > 0) { "A short-edge budget must be positive." }
        require(autoShortEdge.values.all { it > 0 }) { "A short-edge budget must be positive." }
    }

    /**
     * The short-edge cap for [quality] on a [deviceClass] device, or `null` when the preset renders
     * the full surface: always for [RenderQuality.High], and for [RenderQuality.Auto] on a device
     * class [autoShortEdge] has no entry for.
     */
    fun shortEdge(quality: RenderQuality, deviceClass: GpuDeviceClass): Int? = when (quality) {
        RenderQuality.High -> null
        RenderQuality.Balanced -> balancedShortEdge
        RenderQuality.Performance -> performanceShortEdge
        RenderQuality.Auto -> autoShortEdge[deviceClass]
    }

    /** The defaults: the numbers documented on each [RenderQuality]. */
    companion object {
        /** One shared instance of the default budgets. */
        val Default: RenderQualityBudgets = RenderQualityBudgets()
    }
}

/**
 * Resolves a [RenderQuality] against the presented surface, in physical pixels.
 *
 * The scale comes from the physical buffer ([Renderer.outputWidth] by [Renderer.outputHeight]), never
 * from `1 / devicePixelRatio`: a phone whose 844 by 390 CSS-pixel viewport has a device pixel ratio of
 * 3 presents a 2532 by 1170 buffer, so [RenderQuality.Auto] on a [GpuDeviceClass.Mobile] device
 * renders its scene about 900 pixels tall, not 390. The scale never upsamples: a surface already
 * within its budget renders at [RenderScale.FULL].
 *
 * Pure and allocation free, so a host can call it every frame.
 */
object RenderQualityPolicy {
    /**
     * The [Renderer.renderScale] for [quality] on an [outputWidth] by [outputHeight] physical
     * surface of a [deviceClass] device: the short-edge budget over the short edge, in
     * `[RenderScale.MIN, RenderScale.FULL]`. [RenderScale.FULL] when the preset has no cap or the
     * surface has no size yet.
     */
    fun renderScale(
        quality: RenderQuality,
        outputWidth: Int,
        outputHeight: Int,
        deviceClass: GpuDeviceClass,
        budgets: RenderQualityBudgets = RenderQualityBudgets.Default,
    ): Float {
        val budget = budgets.shortEdge(quality, deviceClass) ?: return RenderScale.FULL
        val shortEdge = minOf(outputWidth, outputHeight)
        if (shortEdge <= 0 || shortEdge <= budget) return RenderScale.FULL
        return RenderScale.coerce(budget.toFloat() / shortEdge)
    }

    /**
     * The [EffectQuality] for [quality]: the device's recommendation on [RenderQuality.Auto], and a
     * fixed tier for each explicit preset.
     */
    fun effectQuality(quality: RenderQuality, deviceClass: GpuDeviceClass): EffectQuality = when (quality) {
        RenderQuality.Auto -> deviceClass.recommendedEffectQuality()
        RenderQuality.High -> EffectQuality.High
        RenderQuality.Balanced -> EffectQuality.Medium
        RenderQuality.Performance -> EffectQuality.Low
    }
}
