/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

/**
 * Tracks finite-duration one-shot [SoundHandle] instances and guarantees bounded concurrency and
 * timely release on backends whose static tracks remain in a playing state after buffer completion.
 */
internal class OneShotTrackReaper(
    private val nowMs: () -> Long,
    private val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT_ONE_SHOTS,
    private val reapMarginMs: Long = DEFAULT_REAP_MARGIN_MS,
) {
    init {
        require(maxConcurrent > 0) { "maxConcurrent must be positive, got $maxConcurrent." }
        require(reapMarginMs >= 0L) { "reapMarginMs must be non-negative, got $reapMarginMs." }
    }

    private data class TrackedHandle(
        val handle: SoundHandle,
        val expiresAtMs: Long,
    )

    private val tracked = ArrayDeque<TrackedHandle>()

    val activeCount: Int
        get() = tracked.size

    fun reap() {
        if (tracked.isEmpty()) return
        val now = nowMs()
        var index = 0
        while (index < tracked.size) {
            val entry = tracked[index]
            if (now >= entry.expiresAtMs || !entry.handle.isPlaying) {
                tracked.removeAt(index)
                entry.handle.stop()
            } else {
                index++
            }
        }
    }

    fun reserveSlot() {
        reap()
        while (tracked.size >= maxConcurrent && tracked.isNotEmpty()) {
            tracked.removeFirst().handle.stop()
        }
    }

    fun track(handle: SoundHandle, durationMillis: Long) {
        val expiresAtMs = nowMs() + durationMillis.coerceAtLeast(0L) + reapMarginMs
        tracked.addLast(TrackedHandle(handle = handle, expiresAtMs = expiresAtMs))
    }

    fun stopAll() {
        while (tracked.isNotEmpty()) {
            tracked.removeFirst().handle.stop()
        }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_ONE_SHOTS: Int = 16
        const val DEFAULT_REAP_MARGIN_MS: Long = 150L
    }
}
