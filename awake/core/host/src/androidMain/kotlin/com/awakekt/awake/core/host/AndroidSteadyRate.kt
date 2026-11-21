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
 * The steady rate an Android host holds, why, and how, for a frame-rate HUD (#1699).
 *
 * @property choice The rate, why it was chosen, the p90 cost that decided it, and the candidates.
 * @property recentCostP90Nanos The p90 cost of the last frames now, which the next decision reads.
 * @property displayRefreshHz The panel's refresh rate now: the rate itself once the panel switched.
 * @property heldBy What holds the rate.
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
        val applies = setting != SteadyRate.Off && configured == FrameRateMode.Auto
        val running = if (applies) selectorFor(activity, nowNanos, presentPacing) else null
        if (running == null) release(surface)
        val choice = running?.let { choose(it, setting, costNanos, nowNanos) }
        return if (running == null || choice == null) configured else hold(choice, running, presentPacing, surface)
    }

    /** The selector for the panel's candidates now, rebuilt when they change; null when the panel offers none. */
    private fun selectorFor(activity: Activity?, nowNanos: Long, presentPacing: Boolean): SteadyFrameRate? {
        if (readDisplay(activity, nowNanos) || presentPacing != wholeIntervals || selector == null) {
            wholeIntervals = presentPacing
            val candidates = SteadyFrameRate.candidates(rates, wholeIntervals)
            selector = when {
                candidates.isEmpty() -> null
                selector?.candidatesFps == candidates -> selector
                else -> SteadyFrameRate(candidates)
            }
        }
        return selector
    }

    /** The rate to hold: the selector's under Auto, the setting's under Fixed. Measured either way, so the HUD shows the cost. */
    private fun choose(running: SteadyFrameRate, setting: SteadyRate, costNanos: Long, nowNanos: Long): SteadyRateChoice? {
        val measured = running.onFrame(costNanos, nowNanos)
        return if (setting is SteadyRate.Fixed) fixedChoice(setting.fps, running.candidatesFps) else measured
    }

    private fun fixedChoice(fps: Int, candidates: List<Int>): SteadyRateChoice =
        status?.choice?.takeIf { it.reason == SteadyRateReason.Fixed && it.targetFps == fps && it.candidatesFps == candidates }
            ?: SteadyRateChoice(fps, SteadyRateReason.Fixed, 0L, candidates)

    /** Asks the window for [choice]'s rate, publishes what holds it, and returns the mode that does. */
    private fun hold(choice: SteadyRateChoice, running: SteadyFrameRate, presentPacing: Boolean, surface: Surface?): FrameRateMode {
        vote(surface, choice.targetFps.toFloat())
        val heldBy = when {
            presentPacing -> SteadyRateHold.Swappy
            abs(refreshHz - choice.targetFps) < 1f -> SteadyRateHold.Vsync
            else -> SteadyRateHold.Sleep
        }
        publish(SteadyRateStatus(choice, running.recentCostP90Nanos, refreshHz, heldBy))
        // On vsync the panel already runs at the rate: sleeping as well would pace the frame twice.
        return if (heldBy == SteadyRateHold.Vsync) FrameRateMode.Auto else cappedAt(choice.targetFps)
    }

    private fun cappedAt(fps: Int): FrameRateMode.Capped =
        capped?.takeIf { it.targetFps == fps } ?: FrameRateMode.Capped(fps).also { capped = it }

    /** Takes the rate away from the window, once, and forgets the choice. */
    private fun release(surface: Surface?) {
        if (votedFps != 0f) vote(surface, 0f)
        selector = null
        status = null
    }

    private fun vote(surface: Surface?, fps: Float) {
        val unchanged = fps == votedFps && surface === votedOn
        if (surface?.isValid != true || unchanged) return
        surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        votedFps = fps
        votedOn = surface
    }

    /**
     * The panel's rates at its current resolution, and its refresh rate now, read at most once a
     * second; true when the rates changed.
     */
    private fun readDisplay(activity: Activity?, nowNanos: Long): Boolean {
        val due = readAt == Long.MIN_VALUE || nowNanos - readAt >= READ_EVERY_NANOS
        val display = activity?.display?.takeIf { due }
        var changed = false
        if (display != null) {
            readAt = nowNanos
            val mode = display.mode
            refreshHz = display.refreshRate
            val read = display.supportedModes
                .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                .map { it.refreshRate }
            changed = read != rates
            rates = read
        }
        return changed
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
