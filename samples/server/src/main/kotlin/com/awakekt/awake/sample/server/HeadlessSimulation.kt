/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.server

/**
 * Consumer-side placeholder for an authoritative, headless simulation loop.
 *
 * It deliberately owns no ECS world, network transport, persistence format, or gameplay
 * policy. A future MMORPG server can adapt its own world to [SimulationStep] while proving
 * fixed-tick ordering and replaying a known number of ticks in tests. Promote a smaller,
 * backend-neutral contract to Awake only after another consumer needs the same seam.
 *
 * @param fixedDeltaSeconds The fixed simulation time delta per tick in seconds (e.g. `1 / 60`).
 * @param step The consumer-owned world step handler.
 */
class HeadlessSimulation(
    private val fixedDeltaSeconds: Float = DEFAULT_FIXED_DELTA_SECONDS,
    private val step: SimulationStep,
) {
    init {
        require(fixedDeltaSeconds > 0f) { "fixedDeltaSeconds must be positive" }
    }

    /** The current monotonically increasing simulation tick count. */
    var tick: Long = 0
        private set

    /** Runs time-stepping for exactly one authoritative simulation tick. */
    fun step(): SimulationTick {
        val current = SimulationTick(number = tick, deltaSeconds = fixedDeltaSeconds)
        step.update(current)
        tick += 1
        return current
    }

    /** Runs a deterministic count of ticks; useful for replay and server-side simulation tests. */
    fun runTicks(count: Int) {
        require(count >= 0) { "count must not be negative" }
        repeat(count) { step() }
    }

    private companion object {
        const val DEFAULT_FIXED_DELTA_SECONDS = 1f / 60f
    }
}

/**
 * The neutral input to one fixed simulation update.
 *
 * @property number The 0-based tick index.
 * @property deltaSeconds The fixed time step interval in seconds.
 */
data class SimulationTick(
    val number: Long,
    val deltaSeconds: Float,
)

/** A consumer-owned world adapter invoked once per authoritative simulation tick. */
fun interface SimulationStep {
    /** Executes world updates for [tick]. */
    fun update(tick: SimulationTick)
}
