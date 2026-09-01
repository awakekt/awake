/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

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
    private val previous = SnapshotBuffer()
    private val latest = SnapshotBuffer()
    private var previousTime = -1f
    private var latestTime = -1f

    /** Reusable output; overwritten in place so rendering a frame allocates nothing. */
    val output: SnapshotBuffer = SnapshotBuffer()

    var hasBothEnds: Boolean = false
        private set

    /** Feeds a decoded snapshot, stamped with the client's own receive time. */
    fun accept(snapshot: SnapshotBuffer, receivedAtSeconds: Float) {
        // An out-of-order or duplicate snapshot must not become the newer end, or the
        // interpolation runs backwards. Over UDP this is the common case, not the rare one.
        if (latestTime >= 0f && snapshot.tick <= latest.tick) return
        previous.copyFrom(latest)
        previousTime = latestTime
        latest.copyFrom(snapshot)
        latestTime = receivedAtSeconds
        hasBothEnds = previousTime >= 0f
    }

    /**
     * Fills [output] for the given render time. Falls back to the newest snapshot until two
     * have arrived, so a fresh connection shows something rather than nothing.
     */
    fun sample(nowSeconds: Float): SnapshotBuffer {
        if (!hasBothEnds) {
            output.copyFrom(latest)
            return output
        }
        val target = nowSeconds - delaySeconds
        val span = latestTime - previousTime
        val alpha = if (span <= 0f) 1f else ((target - previousTime) / span).coerceIn(0f, 1f)

        output.reset(latest.tick, latest.count)
        for (index in 0 until latest.count) {
            val netId = latest.netIds[index]
            output.netIds[index] = netId
            val before = previous.indexOf(netId)
            if (before < 0) {
                // An entity that only exists in the newer snapshot has nothing to come from.
                // Snapping it in is correct; inventing a start position would be a guess.
                output.x[index] = latest.x[index]
                output.y[index] = latest.y[index]
            } else {
                output.x[index] = lerp(previous.x[before], latest.x[index], alpha)
                output.y[index] = lerp(previous.y[before], latest.y[index], alpha)
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
