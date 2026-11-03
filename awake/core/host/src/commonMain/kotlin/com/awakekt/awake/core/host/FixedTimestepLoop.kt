/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Decouples simulation from rendering: [advance] accumulates the variable, measured
 * per-frame [FrameLoop] delta and drains it in fixed-size [fixedDelta] steps (calling
 * [FixedUpdate] once per step), then calls [Render] exactly once per frame regardless of
 * how many (including zero) fixed steps ran.
 *
 * This matters once simulation does velocity/force integration (physics, Phase 8) -- a
 * variable step size there makes the simulation's behavior depend on framerate (and can
 * destabilize spring/collision integrators entirely). It's a no-op simplification today:
 * [com.awakekt.awake.scene.systems.TransformSystem] only recomputes matrices
 * from the current position/rotation/scale, it doesn't integrate anything, so it would be
 * correct either way. This class exists so that guarantee holds once something *does* need
 * it, without every call site having to know or care.
 *
 * Deliberately does not interpolate rendered state between the last two fixed steps (the
 * usual pairing for this pattern, using the leftover `accumulator / fixedDelta` fraction to
 * blend the last two simulation states) -- there are no stored "previous" component values
 * to blend from yet. [render]'s `alpha` parameter is provided for a future render system
 * that wants it; today's [com.awakekt.awake.scene.systems.RenderSystem3D] ignores
 * it and just draws whatever [FixedUpdate] last wrote.
 *
 * A frame that brings more time than [maxStepsPerFrame] steps runs only those and drops the
 * rest, so a simulation that can't keep up slows down instead of freezing the render. What it
 * dropped is reported, as [FixedStepReport] describes: a game that keeps a clock of its own in
 * step with the fixed steps, such as a networked game predicting its local player, reads it
 * instead of working it out.
 *
 * @property fixedDelta Duration in seconds of each fixed simulation step.
 * @param maxStepsPerFrame Maximum number of fixed steps executed in a single frame to prevent spiral-of-death.
 */
class FixedTimestepLoop(
    override val fixedDelta: Float = DEFAULT_FIXED_DELTA,
    maxStepsPerFrame: Int = DEFAULT_MAX_STEPS_PER_FRAME,
) : FixedStepReport {
    private var accumulator = 0f

    init {
        require(fixedDelta > 0f) { "fixedDelta must be positive, was $fixedDelta" }
        require(maxStepsPerFrame >= 1) { "maxStepsPerFrame must be at least 1, was $maxStepsPerFrame" }
    }

    /**
     * The most fixed steps one [advance] runs; the time past them is dropped. Takes effect from the
     * next [advance]. At least 1.
     */
    override var maxStepsPerFrame: Int = maxStepsPerFrame
        set(value) {
            require(value >= 1) { "maxStepsPerFrame must be at least 1, was $value" }
            field = value
        }

    override var stepsThisFrame: Int = 0
        private set

    override var droppedSeconds: Float = 0f
        private set

    override var droppedSteps: Int = 0
        private set

    override var droppedStepsTotal: Long = 0L
        private set

    // Replaced, never changed in place, when a listener is added or removed: advance reads the array it
    // holds, so a listener that adds or removes one while it runs changes the next frame's, not this one's.
    private var listeners: Array<TimeDroppedListener> = NO_LISTENERS

    override fun addTimeDroppedListener(listener: TimeDroppedListener) {
        if (listener !in listeners) listeners += listener
    }

    override fun removeTimeDroppedListener(listener: TimeDroppedListener) {
        if (listener in listeners) listeners = listeners.filter { it != listener }.toTypedArray()
    }

    /**
     * Advances the accumulator by [frameDelta], running [fixedUpdate] steps and concluding with [render].
     *
     * [stepsThisFrame], [droppedSeconds] and [droppedSteps] describe this frame by the time [render]
     * runs, and the [TimeDroppedListener]s have heard of any time dropped, so a system that runs in
     * [render] reads this frame's.
     *
     * @param frameDelta The real, measured elapsed time since the last call (from
     *   [FrameLoop.tick]'s `deltaTime`, cast to `Float`).
     * @param fixedUpdate Called zero or more times, always with exactly [fixedDelta].
     * @param render Called exactly once, with `alpha` in `[0, 1)`: how far the accumulator
     *   is into the *next* fixed step that hasn't run yet.
     */
    fun advance(frameDelta: Float, fixedUpdate: FixedUpdate, render: Render) {
        accumulator += frameDelta
        val cap = maxStepsPerFrame
        var steps = 0
        while (accumulator >= fixedDelta && steps < cap) {
            fixedUpdate(fixedDelta)
            accumulator -= fixedDelta
            steps += 1
        }
        stepsThisFrame = steps
        droppedSeconds = 0f
        droppedSteps = 0
        if (steps == cap) {
            // Spiral-of-death guard: if fixedUpdate can't keep up with real time, draining
            // the full accumulator would fall further behind every frame. Drop the
            // remainder instead so the sim slows down visibly rather than freezing render.
            dropRemainder()
        }
        render(accumulator / fixedDelta)
    }

    private fun dropRemainder() {
        val dropped = accumulator
        accumulator = 0f
        if (dropped <= 0f) return
        droppedSeconds = dropped
        // The accumulator is a Float drained one step at a time, so six steps' worth less five steps can
        // come out a hair under one step: whole steps are counted to within STEP_ROUNDING of one.
        droppedSteps = (dropped / fixedDelta + STEP_ROUNDING).toInt()
        droppedStepsTotal += droppedSteps
        val heard = listeners
        for (index in heard.indices) heard[index].onTimeDropped(dropped, droppedSteps)
    }

    /** The loop's defaults. */
    companion object {
        /** [fixedDelta] when none is given: 60 steps a second. */
        const val DEFAULT_FIXED_DELTA = 1f / 60f

        /** [maxStepsPerFrame] when none is given: five steps, about 83 ms at 60 steps a second. */
        const val DEFAULT_MAX_STEPS_PER_FRAME = 5

        private const val STEP_ROUNDING = 1e-3f
        private val NO_LISTENERS = emptyArray<TimeDroppedListener>()
    }
}

/**
 * What a fixed-step loop did in its last frame, for the systems that run in it: the steps it ran, and
 * the time it dropped because the frame brought more than [maxStepsPerFrame] steps of it.
 *
 * Dropping time keeps a slow frame from making the next one slower still, and is what most games want:
 * the simulation slows down for that frame. A game that keeps a clock of its own in step with the fixed
 * steps has to know, though. A networked game that predicts its local player is one: after a long frame
 * its player has walked less than the time that passed while the server walked on, and the two drift
 * apart until a correction puts the player back. Such a game reads [droppedSteps] each frame, or adds a
 * [TimeDroppedListener], and sends or replays the dropped steps, or raises the cap where it configures
 * its scene.
 *
 * Read on the frame thread. Every value describes the last frame whole, and none allocates.
 */
interface FixedStepReport {
    /** Seconds of one fixed step. */
    val fixedDelta: Float

    /** The most fixed steps one frame runs; the time past them is dropped. */
    val maxStepsPerFrame: Int

    /** The fixed steps the last frame ran, from 0 to [maxStepsPerFrame]. */
    val stepsThisFrame: Int

    /**
     * The seconds the last frame dropped, 0 when it dropped none: what was left once it had run
     * [maxStepsPerFrame] steps, including the part of a step that would have carried to the next frame.
     */
    val droppedSeconds: Float

    /**
     * The whole fixed steps in [droppedSeconds]: how many more steps the last frame would have run with no
     * cap. The part of a step left over is in [droppedSeconds] only.
     */
    val droppedSteps: Int

    /** Every [droppedSteps] since the loop was made: how far its fixed steps have fallen behind real time. */
    val droppedStepsTotal: Long

    /**
     * Calls [listener] once for each frame that drops time, with [droppedSeconds] and [droppedSteps],
     * before the frame's systems run. Adding one twice adds it once.
     */
    fun addTimeDroppedListener(listener: TimeDroppedListener)

    /** Stops calling [listener]. */
    fun removeTimeDroppedListener(listener: TimeDroppedListener)
}

/** Told when a fixed-step loop drops time; see [FixedStepReport.addTimeDroppedListener]. */
fun interface TimeDroppedListener {
    /**
     * The frame that just ran its fixed steps dropped [seconds] of time, [steps] whole fixed steps of it.
     * [steps] is 0 when less than a step was dropped.
     */
    fun onTimeDropped(seconds: Float, steps: Int)
}

/** Callback invoked for each fixed simulation step with the step duration in seconds. */
typealias FixedUpdate = (fixedDelta: Float) -> Unit

/** Callback invoked once per frame to render the scene with interpolation factor `alpha`. */
typealias Render = (alpha: Float) -> Unit
