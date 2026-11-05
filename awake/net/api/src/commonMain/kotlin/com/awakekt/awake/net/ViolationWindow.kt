/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Tick-based decaying violation counter for authoritative servers.
 *
 * Transient network bursts (such as a client resuming after a window stall or Wi-Fi handoff) can
 * produce isolated tick-window or rate violations without indicating sustained abuse. When
 * [decayTicks] is positive, each quiet span of [decayTicks] simulation ticks since
 * [lastViolationTick] sheds one accumulated violation down to zero before a new violation is
 * counted, so occasional hiccups heal over time while sustained floods still cross a kick threshold.
 * When [decayTicks] is `0`, violations never decay until [reset] is called.
 *
 * @property decayTicks Simulation ticks of quiet required to decay [count] by one (`0` disables decay).
 */
class ViolationWindow(val decayTicks: Long = 0L) {
    init {
        require(decayTicks >= 0L) { "decayTicks must not be negative, was $decayTicks" }
    }

    /** Stored violation count as of [lastViolationTick]; call [countAt] to include elapsed decay. */
    var count: Int = 0
        private set

    /** Highest simulation tick at which a violation was recorded, or `-1` when empty. */
    var lastViolationTick: Long = NONE_TICK
        private set

    /**
     * Records a violation at [atTick], applying any decay accrued since [lastViolationTick] first.
     *
     * Out-of-order or equal ticks (`atTick <= lastViolationTick`) never produce negative decay and
     * never move [lastViolationTick] backwards.
     *
     * @param atTick Simulation tick at which the violation was observed.
     * @return Updated [count] after decay and increment.
     */
    fun record(atTick: Long): Int {
        val base = countAt(atTick)
        if (count == 0 || atTick > lastViolationTick) {
            lastViolationTick = atTick
        }
        count = if (base == Int.MAX_VALUE) Int.MAX_VALUE else base + 1
        return count
    }

    /**
     * Returns the effective violation count at [atTick] after decay without mutating this window.
     *
     * @param atTick Simulation tick at which to evaluate decay.
     * @return Decayed violation count (`0..count`).
     */
    fun countAt(atTick: Long): Int {
        if (decayTicks <= 0L || count == 0 || atTick <= lastViolationTick) return count
        val decayedSteps = (atTick.toULong() - lastViolationTick.toULong()) / decayTicks.toULong()
        return if (decayedSteps >= count.toULong()) 0 else count - decayedSteps.toInt()
    }

    /** Clears [count] to `0` and resets [lastViolationTick] to `-1`. */
    fun reset() {
        count = 0
        lastViolationTick = NONE_TICK
    }

    private companion object {
        const val NONE_TICK = -1L
    }
}
