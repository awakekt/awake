/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

/**
 * Renders remote entities slightly in the past, between the two most recent snapshots.
 *
 * Snapshots arrive at the tick rate (20 Hz) while frames render far more often, so drawing the
 * newest snapshot directly makes every entity jump twenty times a second. Holding a small delay
 * and interpolating across it turns that into continuous motion, and it fixes most of the
 * visible jitter on its own -- which is why it comes before prediction rather than after.
 *
 * The delay is the cost: one buffer period of extra latency, paid so that a late packet has
 * somewhere to land instead of causing a visible stall. [delaySeconds] should be at least one
 * snapshot interval; below that the buffer runs dry and interpolation degrades to a hold.
 */
class SnapshotInterpolator(
    private val delaySeconds: Float = DEFAULT_DELAY_SECONDS,
) {
    private val history = Array(HISTORY_CAPACITY) { SnapshotBuffer() }
    private val historyTimes = FloatArray(HISTORY_CAPACITY) { -1f }
    private var historyCount = 0

    /** Reusable output; overwritten in place so rendering a frame allocates nothing. */
    val output: SnapshotBuffer = SnapshotBuffer()

    val hasBothEnds: Boolean
        get() = historyCount >= 2

    /** Feeds a decoded snapshot, stamped with the client's own receive time. */
    fun accept(snapshot: SnapshotBuffer, receivedAtSeconds: Float) {
        // An out-of-order or duplicate snapshot must not become the newer end, or the
        // interpolation runs backwards. Over UDP this is the common case, not the rare one.
        if (historyCount > 0 && snapshot.tick <= history[historyCount - 1].tick) return

        if (historyCount < HISTORY_CAPACITY) {
            history[historyCount].copyFrom(snapshot)
            historyTimes[historyCount] = receivedAtSeconds
            historyCount += 1
        } else {
            val oldest = history[0]
            for (i in 0 until HISTORY_CAPACITY - 1) {
                history[i] = history[i + 1]
                historyTimes[i] = historyTimes[i + 1]
            }
            oldest.copyFrom(snapshot)
            history[HISTORY_CAPACITY - 1] = oldest
            historyTimes[HISTORY_CAPACITY - 1] = receivedAtSeconds
        }
    }

    /**
     * Fills [output] for the given render time. Falls back to the newest snapshot until two
     * have arrived, so a fresh connection shows something rather than nothing.
     */
    fun sample(nowSeconds: Float): SnapshotBuffer {
        if (!hasBothEnds) {
            if (historyCount > 0) {
                output.copyFrom(history[historyCount - 1])
            }
            return output
        }

        val target = nowSeconds - delaySeconds
        val newestIndex = historyCount - 1
        val oldestTime = historyTimes[0]
        val newestTime = historyTimes[newestIndex]

        val before: SnapshotBuffer
        val after: SnapshotBuffer
        val alpha: Float

        when {
            target <= oldestTime -> {
                before = history[0]
                after = history[1]
                alpha = 0f
            }

            target >= newestTime -> {
                before = history[newestIndex - 1]
                after = history[newestIndex]
                alpha = 1f
            }

            else -> {
                var index = 0
                while (index < newestIndex - 1 && historyTimes[index + 1] <= target) {
                    index += 1
                }
                before = history[index]
                after = history[index + 1]
                val span = historyTimes[index + 1] - historyTimes[index]
                alpha = if (span <= 0f) 1f else ((target - historyTimes[index]) / span).coerceIn(0f, 1f)
            }
        }

        output.reset(after.tick, after.count)
        for (index in 0 until after.count) {
            val netId = after.netIds[index]
            output.netIds[index] = netId
            val beforeIndex = before.indexOf(netId)
            if (beforeIndex < 0) {
                // An entity that only exists in the newer snapshot has nothing to come from.
                // Snapping it in is correct; inventing a start position would be a guess.
                output.x[index] = after.x[index]
                output.y[index] = after.y[index]
            } else {
                output.x[index] = lerp(before.x[beforeIndex], after.x[index], alpha)
                output.y[index] = lerp(before.y[beforeIndex], after.y[index], alpha)
            }
        }
        return output
    }

    private fun lerp(from: Float, to: Float, alpha: Float): Float = from + (to - from) * alpha

    private companion object {
        /** Two snapshot intervals at 20 Hz: enough slack for one late packet. */
        const val DEFAULT_DELAY_SECONDS = 0.1f
    }
}

private const val HISTORY_CAPACITY = 16
