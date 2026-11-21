/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import android.app.Activity
import android.view.Surface
import kotlin.math.abs

/** What holds the Android host's steady rate (#1699). */
enum class SteadyRateHold {
    /** The panel runs at the rate, so the FIFO swapchain's vsync paces every frame. */
    Vsync,

    /** Swappy holds each frame for a whole number of refreshes. */
    Swappy,

    /** The panel hasn't switched to the rate, so the loop sleeps the cap out: steadier than nothing, not even. */
    Sleep,
}

/**
 * The steady rate an Android host holds, why, and how, for a frame-rate HUD (#1699): the choice with
 * its p90 cost and candidates, the p90 of the last frames now, the panel's refresh rate, and what holds it.
 */
data class SteadyRateStatus(
    val choice: SteadyRateChoice,
    val recentCostP90Nanos: Long,
    val displayRefreshHz: Float,
    val heldBy: SteadyRateHold,
)

/**
 * Runs [SteadyFrameRate] for [AndroidFrameLoop]: reads the panel's refresh rates, asks the window for
 * the chosen rate (`Surface.setFrameRate`, `FRAME_RATE_COMPATIBILITY_FIXED_SOURCE`) so the panel can
 * switch to it, and turns the choice into the mode frames run at. Render thread only.
 */
internal class AndroidSteadyRate {
    private var selector: SteadyFrameRate? = null
    private var rates: List<Float> = emptyList()
    private var refreshHz = 0f
    private var readAt = Long.MIN_VALUE
    private var wholeIntervals = false
    private var votedFps = 0f
    private var votedOn: Surface? = null
    private var capped: FrameRateMode.Capped? = null

    /** The status last published, kept so a frame that changes nothing publishes nothing. */
    var status: SteadyRateStatus? = null
        private set

    /** The mode a frame runs at under [setting], [configured] being the app's own mode. */
    fun mode(
        setting: SteadyRate,
        configured: FrameRateMode,
        costNanos: Long,
        nowNanos: Long,
        presentPacing: Boolean,
        activity: Activity?,
        surface: Surface?,
    ): FrameRateMode {
        if (setting == SteadyRate.Off || configured != FrameRateMode.Auto) return release(surface, configured)
        if (readDisplay(activity, nowNanos) || presentPacing != wholeIntervals || selector == null) {
            wholeIntervals = presentPacing
            val candidates = SteadyFrameRate.candidates(rates, wholeIntervals)
            if (candidates.isEmpty()) return release(surface, configured)
            if (selector?.candidatesFps != candidates) selector = SteadyFrameRate(candidates)
        }
        val running = selector ?: return release(surface, configured)
        // Measured under Fixed too, so the HUD shows what the frames cost against the rate held.
        val measured = running.onFrame(costNanos, nowNanos)
        val choice = when (setting) {
            is SteadyRate.Fixed -> status?.choice?.takeIf { it.reason == SteadyRateReason.Fixed && it.targetFps == setting.fps }
                ?: SteadyRateChoice(setting.fps, SteadyRateReason.Fixed, 0L, running.candidatesFps)
            else -> measured
        } ?: return configured
        vote(surface, choice.targetFps.toFloat())
        val heldBy = when {
            presentPacing -> SteadyRateHold.Swappy
            abs(refreshHz - choice.targetFps) < 1f -> SteadyRateHold.Vsync
            else -> SteadyRateHold.Sleep
        }
        publish(SteadyRateStatus(choice, running.recentCostP90Nanos, refreshHz, heldBy))
        // On vsync the panel already runs at the rate: sleeping as well would pace the frame twice.
        if (heldBy == SteadyRateHold.Vsync) return FrameRateMode.Auto
        return capped?.takeIf { it.targetFps == choice.targetFps } ?: FrameRateMode.Capped(choice.targetFps).also { capped = it }
    }

    /** Takes the rate away from the window, once, and forgets the choice. */
    private fun release(surface: Surface?, configured: FrameRateMode): FrameRateMode {
        if (votedFps != 0f) vote(surface, 0f)
        selector = null
        readAt = Long.MIN_VALUE
        status = null
        return configured
    }

    private fun vote(surface: Surface?, fps: Float) {
        if (surface == null || !surface.isValid || (fps == votedFps && surface === votedOn)) return
        surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        votedFps = fps
        votedOn = surface
    }

    /**
     * The panel's rates at its current resolution, and its refresh rate now, read at most once a
     * second; true when the rates changed.
     */
    private fun readDisplay(activity: Activity?, nowNanos: Long): Boolean {
        if (readAt != Long.MIN_VALUE && nowNanos - readAt < READ_EVERY_NANOS) return false
        readAt = nowNanos
        val display = activity?.display ?: return false
        val mode = display.mode
        refreshHz = display.refreshRate
        val read = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }
        if (read == rates) return false
        rates = read
        return true
    }

    private fun publish(next: SteadyRateStatus) {
        val last = status
        val same = last != null && last.choice == next.choice && last.heldBy == next.heldBy &&
            last.displayRefreshHz == next.displayRefreshHz && abs(last.recentCostP90Nanos - next.recentCostP90Nanos) < COST_STEP_NANOS
        if (!same) status = next
    }

    private companion object {
        const val READ_EVERY_NANOS = 1_000_000_000L

        /** The live cost the HUD shows moves in 0.25 ms steps, so a frame that changes nothing allocates nothing. */
        const val COST_STEP_NANOS = 250_000L
    }
}
