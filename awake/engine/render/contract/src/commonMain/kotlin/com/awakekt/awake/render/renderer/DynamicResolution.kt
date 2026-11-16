/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * How [RenderQuality.Auto] moves the scene's resolution to hold a frame rate (#1688), instead of
 * keeping one short-edge budget per device class.
 *
 * The scene target's short edge moves between [floorShortEdge] and the ceiling: [ceilingShortEdge],
 * or Auto's budget for the device class when that is null, so by default Auto never draws more than
 * it did with a fixed budget, only less while frames run long. A surface smaller than the floor
 * renders at its own size.
 *
 * @property targetFps The frame rate to hold.
 * @property floorShortEdge The fewest physical pixels the short edge drops to.
 * @property ceilingShortEdge The most it rises to, or null for Auto's device-class budget. A value
 * above the surface's short edge means the full surface.
 */
data class DynamicResolution(
    val targetFps: Int = 60,
    val floorShortEdge: Int = 540,
    val ceilingShortEdge: Int? = null,
) {
    init {
        require(targetFps > 0) { "targetFps must be positive: $targetFps" }
        require(floorShortEdge > 0) { "floorShortEdge must be positive: $floorShortEdge" }
        require(ceilingShortEdge == null || ceilingShortEdge >= floorShortEdge) {
            "ceilingShortEdge $ceilingShortEdge is below floorShortEdge $floorShortEdge"
        }
    }

    /** A frame's budget at [targetFps], in milliseconds. */
    val frameBudgetMs: Float get() = MS_PER_SECOND / targetFps

    private companion object {
        const val MS_PER_SECOND = 1000f
    }
}

/**
 * Holds [DynamicResolution.targetFps] by moving a short edge between a floor and a ceiling, one frame
 * at a time. Pure and allocation free, so a host calls [update] every frame.
 *
 * Two signals, the better first:
 * - **GPU time**, where the renderer measures it ([RenderFrameStats.gpuTimeMs]). GPU cost follows the
 *   pixel count, the square of the edge, so the edge moves by the square root of how far the smoothed
 *   GPU time is from the budget, with a dead band and a slower climb than descent. A frame held back
 *   by the display or by the CPU leaves GPU time alone, so it never lowers the resolution.
 * - **Missed frames**, everywhere else: it steps down while frames miss,
 *   probes up cautiously and backs off a probe that missed, and stops answering misses that a lower
 *   resolution does not fix, such as a display capped below the target.
 *
 * Resolution gives way before a present-time pacer halves the rate (#1699): the pacer halves only
 * once the edge sits at its floor, as GPU time keeps telling this controller the frame is too heavy.
 */
class DynamicResolutionController(
    /** The target and the edge's range. */
    val config: DynamicResolution,
    /** The edge's ceiling, resolved: [DynamicResolution.ceilingShortEdge] or Auto's class budget. */
    ceilingShortEdge: Int,
) {
    private val ceiling = maxOf(ceilingShortEdge, config.floorShortEdge).toFloat()
    private val floor = config.floorShortEdge.toFloat()
    private val gpuTarget = config.frameBudgetMs * GPU_HEADROOM
    private val missed = MissedFrameScaler(config.frameBudgetMs)
    private var smoothedGpuMs = -1f
    private var cooldown = 0

    /** The short edge, in physical pixels, the scene renders at; starts at the ceiling. */
    var shortEdge: Float = ceiling
        private set

    /**
     * Takes a frame that took [frameSeconds] from start to start, with [gpuTimeMs] of GPU work when
     * measured, and returns the [Renderer.renderScale] for an [outputShortEdge]-pixel surface.
     */
    fun update(frameSeconds: Float, gpuTimeMs: Float?, outputShortEdge: Int): Float {
        if (gpuTimeMs != null && gpuTimeMs > 0f) updateFromGpu(gpuTimeMs) else updateFromFrames(frameSeconds)
        return scaleFor(outputShortEdge)
    }

    /** The [Renderer.renderScale] the current edge gives an [outputShortEdge]-pixel surface. */
    fun scaleFor(outputShortEdge: Int): Float =
        if (outputShortEdge <= 0 || shortEdge >= outputShortEdge) RenderScale.FULL else RenderScale.coerce(shortEdge / outputShortEdge)

    private fun updateFromGpu(gpuTimeMs: Float) {
        smoothedGpuMs = if (smoothedGpuMs < 0f) gpuTimeMs else smoothedGpuMs + GPU_SMOOTHING * (gpuTimeMs - smoothedGpuMs)
        if (cooldown > 0) {
            cooldown -= 1
            return
        }
        val ratio = kotlin.math.sqrt(gpuTarget / smoothedGpuMs)
        if (ratio > 1f - DEAD_BAND && ratio < 1f + DEAD_BAND) return
        val step = ratio.coerceIn(MAX_STEP_DOWN, MAX_STEP_UP)
        moveTo(shortEdge * step)
    }

    private fun updateFromFrames(frameSeconds: Float) {
        val step = missed.update(frameSeconds * MS_PER_SECOND, atFloor = shortEdge <= floor, atCeiling = shortEdge >= ceiling)
        if (step != 1f) moveTo(shortEdge * step)
    }

    private fun moveTo(edge: Float) {
        val clamped = edge.coerceIn(floor, ceiling)
        if (clamped != shortEdge) {
            shortEdge = clamped
            cooldown = GPU_COOLDOWN_FRAMES
        }
    }

    private companion object {
        const val MS_PER_SECOND = 1000f

        /** GPU time aimed for, as a share of the frame budget: room for the CPU and a spike. */
        const val GPU_HEADROOM = 0.85f

        /** Exponential smoothing of GPU time: about the last ten frames. */
        const val GPU_SMOOTHING = 0.2f

        /** Within 5% of the target edge, nothing moves. */
        const val DEAD_BAND = 0.05f

        /** At most 15% down in one step, and 5% up: a heavy frame is answered quickly, a light one slowly. */
        const val MAX_STEP_DOWN = 0.85f
        const val MAX_STEP_UP = 1.05f

        /** Frames to wait after a change: GPU timestamps come back a few frames late. */
        const val GPU_COOLDOWN_FRAMES = 10
    }
}
