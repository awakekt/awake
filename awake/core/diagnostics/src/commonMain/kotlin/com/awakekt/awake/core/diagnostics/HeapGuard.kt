/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Hysteresis guard over heap usage ratios (`used / limit`) that advises when to trigger cache
 * eviction / collection and when sustained memory pressure warrants closing or tearing down heavy
 * scenes before an out-of-memory kill.
 *
 * - Below [collectAt]: resets the consecutive critical-breach streak and returns [Action.None].
 * - At or above [collectAt]: invokes [collect] (if provided) to reclaim memory and re-measure
 *   live usage immediately. If the resulting ratio is at or above [closeAt], increments
 *   [consecutiveBreaches] and returns [Action.Close] once [consecutiveBreaches] reaches
 *   [closeAfter]; otherwise resets [consecutiveBreaches] and returns [Action.Collect].
 * - Invalid samples (`limit <= 0` or `used < 0`) reset [consecutiveBreaches] and return
 *   [Action.None].
 *
 * @property collectAt Usage ratio (`0.0 < collectAt <= 1.0`) at or above which collection runs.
 * @property closeAt Usage ratio (`collectAt <= closeAt <= 1.0`) that counts toward [Action.Close].
 * @property closeAfter Number of consecutive samples at or above [closeAt] required before
 *   returning [Action.Close] (`>= 1`).
 */
class HeapGuard(
    val collectAt: Double = COLLECT_AT,
    val closeAt: Double = CLOSE_AT,
    val closeAfter: Int = CLOSE_AFTER,
) {
    /** Recommended response returned by [check]. */
    enum class Action {
        /** Heap usage is within budget; no action required. */
        None,

        /** Heap usage reached [collectAt]; evict optional caches or collect garbage. */
        Collect,

        /** Heap usage stayed at or above [closeAt] for [closeAfter] consecutive checks; close or shed heavy state. */
        Close,
    }

    init {
        require(collectAt.isFinite() && collectAt > 0.0 && collectAt <= 1.0) {
            "collectAt must be in (0.0, 1.0], was $collectAt."
        }
        require(closeAt.isFinite() && closeAt >= collectAt && closeAt <= 1.0) {
            "closeAt must be in [collectAt ($collectAt), 1.0], was $closeAt."
        }
        require(closeAfter >= 1) {
            "closeAfter must be >= 1, was $closeAfter."
        }
    }

    /** Number of consecutive checks whose post-collection ratio remained at or above [closeAt]. */
    var consecutiveBreaches: Int = 0
        private set

    /**
     * Evaluates heap usage [used] against [limit] and returns the recommended [Action].
     *
     * When `used / limit >= collectAt` and [collect] is supplied, [collect] is invoked synchronously
     * and its returned post-collection byte count is evaluated against [closeAt] so uncollected
     * garbage does not trip [Action.Close].
     *
     * @param used Current used heap bytes.
     * @param limit Heap ceiling in bytes.
     * @param collect Optional reclamation callback returning post-collection used heap bytes.
     * @return [Action.None], [Action.Collect], or [Action.Close].
     */
    fun check(used: Long, limit: Long, collect: (() -> Long)? = null): Action {
        if (limit <= 0L || used < 0L || used.toDouble() / limit.toDouble() < collectAt) {
            consecutiveBreaches = 0
            return Action.None
        }
        val effectiveUsed = (collect?.invoke() ?: used).coerceAtLeast(0L)
        return if (effectiveUsed.toDouble() / limit.toDouble() >= closeAt) {
            consecutiveBreaches++
            if (consecutiveBreaches >= closeAfter) Action.Close else Action.Collect
        } else {
            consecutiveBreaches = 0
            Action.Collect
        }
    }

    /** Resets [consecutiveBreaches] back to zero. */
    fun reset() {
        consecutiveBreaches = 0
    }

    /** Default threshold constants for [HeapGuard]. */
    companion object {
        /** Default heap ratio (`70%`) that triggers [Action.Collect]. */
        const val COLLECT_AT: Double = 0.70

        /** Default heap ratio (`80%`) that counts toward [Action.Close]. */
        const val CLOSE_AT: Double = 0.80

        /** Default consecutive checks at or above [CLOSE_AT] before returning [Action.Close]. */
        const val CLOSE_AFTER: Int = 3
    }
}
