/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import android.app.Activity
import android.view.Surface
import java.lang.ref.WeakReference

/**
 * Android implementation of [FrameLoop] using `System.nanoTime()` and thread sleeping.
 *
 * A cap is slept out here unless the present call paces the frame ([presentPacing]); see
 * [FramePacing.pacer]. The mode a frame runs at is [frameRateOverride] when set, as on desktop.
 */
object AndroidFrameLoop : FrameLoop {
    /**
     * Optional explicit frame rate mode set at runtime (a debug option, a settings screen), read each
     * frame. When null, the caller-specified [FrameRateMode] is used.
     */
    @Volatile
    var frameRateOverride: FrameRateMode? = null

    /**
     * Whether the renderer's present call holds each frame to the mode's interval, as a frame-pacing
     * library does. While true the loop does not also sleep, so a frame is never paced twice.
     */
    @Volatile
    var presentPacing: Boolean = false

    /**
     * Which pacer an app asks for (#1699), [AndroidFramePacing.Sleep] by default. Read while the app
     * runs, so a settings screen can switch it live; [presentPacing] says whether Swappy took over.
     */
    @Volatile
    var framePacing: AndroidFramePacing = AndroidFramePacing.Sleep

    /**
     * The activity showing the frames, which Swappy needs to read the display; set by the view that
     * hosts the frames. Held weakly: the loop outlives any one activity.
     */
    @Volatile
    var pacingActivity: WeakReference<Activity>? = null

    /**
     * Whether frames run at one steady rate the panel divides evenly, chosen from what they cost
     * (#1699): [SteadyRate.Off] by default, which leaves every frame as it was. It applies while the
     * frame-rate mode is [FrameRateMode.Auto], and can change while the app runs.
     */
    @Volatile
    var steadyRate: SteadyRate = SteadyRate.Off

    /** The steady rate held now, why it was chosen and what holds it; null while [steadyRate] is not in effect. */
    val steadyRateStatus: SteadyRateStatus? get() = steady.status

    /**
     * The mode the last frame ran at: [frameRateOverride] or the app's mode, and the steady rate's when
     * it is in effect. A present-time pacer reads it, so Swappy holds the same rate the loop does.
     */
    @Volatile
    var frameRateModeInUse: FrameRateMode = FrameRateMode.Auto
        private set

    /** The surface the frames show on, which the steady rate is asked of; set by the view that hosts the frames. */
    @Volatile
    var pacingSurface: WeakReference<Surface>? = null

    private val steady = AndroidSteadyRate()
    private var previousFrameTime = System.nanoTime()

    override fun tick(
        mode: FrameRateMode,
        onUpdate: (deltaTime: Double) -> Unit,
    ) {
        val currentFrameTime = System.nanoTime()
        val rawDeltaTime = (currentFrameTime - previousFrameTime) / 1e9
        val deltaTime = clampFrameDelta(rawDeltaTime)
        previousFrameTime = currentFrameTime

        onUpdate(deltaTime)

        val workNanos = System.nanoTime() - currentFrameTime
        val effectiveMode = steady.mode(
            setting = steadyRate,
            configured = frameRateOverride ?: mode,
            costNanos = FrameCostReport.takeCost(workNanos),
            nowNanos = currentFrameTime + workNanos,
            presentPacing = presentPacing,
            activity = pacingActivity?.get(),
            surface = pacingSurface?.get(),
        )
        frameRateModeInUse = effectiveMode
        val pacer = FramePacing.pacer(effectiveMode, presentPacing)
        val sleepNanos = FramePacing.sleepNanos(effectiveMode, pacer, System.nanoTime() - currentFrameTime)
        if (sleepNanos > 0) {
            Thread.sleep(sleepNanos / NANOS_PER_MILLI, (sleepNanos % NANOS_PER_MILLI).toInt())
        }
    }

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        tick(FrameRateMode.Auto, onUpdate)
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
