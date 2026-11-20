/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

import com.awakekt.awake.engine.platform.quality.RenderQualityPersistence
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.renderer.AntiAliasing
import com.awakekt.awake.render.renderer.DynamicResolution
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityBudgets
import com.awakekt.awake.render.renderer.ShadowDepthFormat

/**
 * Configures the app's [RenderQualitySettings] in `app { renderQuality { ... } }`.
 */
@AwakeAppDsl
class RenderQualityDsl internal constructor() {
    /** Loads the saved preset and saves each change; `null` keeps the preset in memory only. */
    var persistence: RenderQualityPersistence? = null

    /** The short-edge budget behind each preset; the neutral defaults unless the app tunes them. */
    var budgets: RenderQualityBudgets = RenderQualityBudgets.Default

    /** The preset when nothing is saved. */
    var initial: RenderQuality = RenderQuality.Auto

    /**
     * How [RenderQuality.Auto] moves the resolution to hold a frame rate: on phones only by default,
     * `DynamicResolution(...)` on every device, or `null` for a fixed budget everywhere. See
     * [RenderQualitySettings.dynamicResolution].
     */
    var dynamicResolution: DynamicResolution? = DynamicResolution.Handhelds

    /**
     * The shadow maps' depth format, or `null` for the engine's own; see
     * [RenderQualitySettings.shadowDepthFormat]. Read once, when the engine starts.
     */
    var shadowDepthFormat: ShadowDepthFormat? = null

    /**
     * The scene's anti-aliasing, or `null` for the render plan's own; see
     * [RenderQualitySettings.antiAliasing]. Multisampled modes are read once, when the engine starts.
     */
    var antiAliasing: AntiAliasing? = null
}

/**
 * Turns on render quality for this app: registers one [RenderQualitySettings] as an app service,
 * which the graphics engine applies to the renderer before every frame. Opt in: an app that never
 * calls this renders exactly as it did. Settings UI reads it back through the app's services, or a
 * scene overlay through `LocalRenderQuality`.
 *
 * @param block Configures persistence, budgets and the starting preset.
 * @return The registered settings, for the app to keep or bind to.
 * @throws IllegalStateException If this app already registered render quality.
 */
fun AppSpecDsl.renderQuality(block: RenderQualityDsl.() -> Unit = {}): RenderQualitySettings {
    check(service(RenderQualitySettings::class) == null) { "renderQuality { } is already registered for this app." }
    val config = RenderQualityDsl().apply(block)
    val settings = RenderQualitySettings(config.persistence, config.budgets, config.initial)
    settings.dynamicResolution = config.dynamicResolution
    settings.shadowDepthFormat = config.shadowDepthFormat
    settings.antiAliasing = config.antiAliasing
    service(RenderQualitySettings::class, settings)
    return settings
}
