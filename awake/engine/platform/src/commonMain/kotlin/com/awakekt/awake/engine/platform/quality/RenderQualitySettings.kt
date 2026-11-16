/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.render.renderer.DynamicResolution
import com.awakekt.awake.render.renderer.DynamicResolutionController
import com.awakekt.awake.render.renderer.DynamicResolutionStats
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityBudgets
import com.awakekt.awake.render.renderer.RenderQualityPolicy
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.ShadowBudget
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

    /**
     * The shadow budget [applyTo] sets instead of the preset's, or `null` (the default) for
     * [RenderQualityPolicy.shadowBudget]'s. `ShadowBudget.Default` keeps what every preset drew before
     * shadow budgets (#1688).
     */
    var shadowBudgetOverride: ShadowBudget? = null

    /**
     * How [RenderQuality.Auto] moves the scene's resolution to hold a frame rate (#1688), or `null` for
     * Auto's fixed device-class budget everywhere. [DynamicResolution.Handhelds] by default: on phones
     * only, with Auto's budget as the ceiling, so Auto never draws more than that budget there and
     * draws less while frames run long; a desktop keeps its fixed budget. A desktop opts in with its
     * own `DynamicResolution(...)`, which runs on every device class. Read every frame; a new value
     * restarts the controller at its ceiling.
     */
    var dynamicResolution: DynamicResolution? = DynamicResolution.Handhelds

    /**
     * Where dynamic resolution stood after the last frame: the short edge it asked for and the signal
     * it read. Null while it does not run: another preset, a device class it is off for, or no frame yet.
     */
    val dynamicResolutionStats: DynamicResolutionStats?
        get() = controller?.let { active ->
            val edge = active.shortEdge.toInt()
            DynamicResolutionStats(if (lastOutputShortEdge in 1 until edge) lastOutputShortEdge else edge, active.signal)
        }

    private var lastOutputShortEdge = 0

    private var controller: DynamicResolutionController? = null
    private var controllerConfig: DynamicResolution? = null
    private var controllerCeiling = 0

    /** Selects [quality], saving it through the persistence callback when it changed. */
    fun select(quality: RenderQuality) {
        val previous = state.getAndUpdate { quality }
        if (previous != quality) persistence?.save(quality)
    }

    /**
     * Resolves the selected preset against [renderer]'s presented surface and device, and sets its
     * [Renderer.renderScale], [Renderer.effectQuality] and [Renderer.shadowBudget]. Allocation free;
     * called once per frame.
     *
     * @param renderer The renderer this frame draws with.
     * @param applyScale `false` leaves [Renderer.renderScale] to a host override.
     */
    fun applyTo(renderer: Renderer, applyScale: Boolean = true) {
        applyTo(renderer, applyScale, frameSeconds = Float.NaN)
    }

    /**
     * [applyTo] for a frame that took [frameSeconds], which [dynamicResolution] reads where the
     * renderer does not time the GPU. `Float.NaN` applies the current scale without a sample.
     */
    fun applyTo(renderer: Renderer, applyScale: Boolean, frameSeconds: Float) {
        val current = state.value
        val device = renderer.deviceClass
        val dynamic = dynamicResolution?.takeIf { current == RenderQuality.Auto && it.runsOn(device) }
        if (dynamic == null || !applyScale) controller = null
        if (applyScale) {
            renderer.renderScale = if (dynamic != null) {
                dynamicScale(renderer, dynamic, frameSeconds)
            } else {
                RenderQualityPolicy.renderScale(current, renderer.outputWidth, renderer.outputHeight, device, budgets)
            }
        }
        val effects = RenderQualityPolicy.effectQuality(current, device)
        if (renderer.effectQuality != effects) renderer.effectQuality = effects
        val shadows = shadowBudgetOverride ?: RenderQualityPolicy.shadowBudget(current, device)
        if (renderer.shadowBudget != shadows) renderer.shadowBudget = shadows
    }

    private fun dynamicScale(renderer: Renderer, config: DynamicResolution, frameSeconds: Float): Float {
        val outputShortEdge = minOf(renderer.outputWidth, renderer.outputHeight)
        lastOutputShortEdge = outputShortEdge
        val ceiling = config.ceilingShortEdge
            ?: budgets.shortEdge(RenderQuality.Auto, renderer.deviceClass)
            ?: outputShortEdge
        var active = controller
        if (active == null || controllerConfig !== config || controllerCeiling != ceiling) {
            active = DynamicResolutionController(config, ceiling)
            controller = active
            controllerConfig = config
            controllerCeiling = ceiling
        }
        return when {
            outputShortEdge <= 0 -> RenderScale.FULL
            frameSeconds.isNaN() -> active.scaleFor(outputShortEdge)
            else -> active.update(frameSeconds, renderer.frameStats?.gpuTimeMs, outputShortEdge)
        }
    }
}
