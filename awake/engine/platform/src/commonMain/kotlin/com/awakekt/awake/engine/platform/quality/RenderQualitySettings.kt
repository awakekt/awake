/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityBudgets
import com.awakekt.awake.render.renderer.RenderQualityPolicy
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Where an app keeps the chosen [RenderQuality] between runs. Core stores nothing itself: the app
 * passes this in `app { renderQuality { persistence = ... } }` and backs it with whatever storage it
 * already has.
 */
interface RenderQualityPersistence {
    /** The preset saved by an earlier run, or `null` when there is none. */
    fun load(): RenderQuality?

    /** Called each time the preset changes to a different value. */
    fun save(quality: RenderQuality)
}

/**
 * One app's graphics quality setting. Session scoped: each app registers its own instance as a
 * service, and two apps in one process never share one.
 *
 * `GraphicsEngine` looks this up and calls [applyTo] before every frame, so the preset reaches
 * [Renderer.renderScale] and [Renderer.effectQuality] on every backend, with or without a scene. A
 * host's explicit render scale (`GraphicsEngine.renderScaleOverride`) still wins over the scale; the
 * effect quality is then still this setting's.
 *
 * [quality] is observable, so a settings screen can bind to it and change it with [select] from any
 * thread; the next frame applies it.
 *
 * @param persistence Loads the starting preset and saves each change; `null` keeps it in memory.
 * @property budgets The short-edge budgets [RenderQualityPolicy] resolves each preset with.
 * @param initial The preset when [persistence] has none saved.
 */
class RenderQualitySettings(
    private val persistence: RenderQualityPersistence? = null,
    val budgets: RenderQualityBudgets = RenderQualityBudgets.Default,
    initial: RenderQuality = RenderQuality.Auto,
) {
    private val state = MutableStateFlow(persistence?.load() ?: initial)

    /** The selected preset. */
    val quality: StateFlow<RenderQuality> = state.asStateFlow()

    /** Selects [quality], saving it through the persistence callback when it changed. */
    fun select(quality: RenderQuality) {
        val previous = state.getAndUpdate { quality }
        if (previous != quality) persistence?.save(quality)
    }

    /**
     * Resolves the selected preset against [renderer]'s presented surface and device, and sets its
     * [Renderer.renderScale] and [Renderer.effectQuality]. Allocation free; called once per frame.
     *
     * @param renderer The renderer this frame draws with.
     * @param applyScale `false` leaves [Renderer.renderScale] to a host override.
     */
    fun applyTo(renderer: Renderer, applyScale: Boolean = true) {
        val current = state.value
        val device = renderer.deviceClass
        if (applyScale) {
            renderer.renderScale = RenderQualityPolicy.renderScale(
                current,
                renderer.outputWidth,
                renderer.outputHeight,
                device,
                budgets,
            )
        }
        val effects = RenderQualityPolicy.effectQuality(current, device)
        if (renderer.effectQuality != effects) renderer.effectQuality = effects
    }
}
