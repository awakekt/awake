/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Decides which errors are worth sending, so an error that repeats every frame sends once rather than
 * flooding a report service: an error is [admitted][admit] unless the same key was admitted within
 * [dedupeMillis], or [maxPerWindow] errors were already admitted in the current [windowMillis].
 *
 * The key is the app's choice, such as the error's source and message without its numbers. At most
 * [maxKeys] keys are remembered; past that, the oldest are forgotten first, so memory stays bounded
 * however many distinct errors arrive. Not thread-safe; call from one thread, as [CrashRecorder].
 *
 * @param now Returns the current time in milliseconds.
 * @property maxPerWindow How many errors may be admitted in one window (`>= 1`).
 * @property windowMillis The length of a rate-limit window in milliseconds (`>= 1`).
 * @property dedupeMillis How long a key, once admitted, is refused again, in milliseconds (`>= 0`).
 * @property maxKeys How many recently admitted keys are remembered for deduplication (`>= 1`).
 */
class ErrorSampler(
    private val now: () -> Long,
    val maxPerWindow: Int = DEFAULT_MAX_PER_WINDOW,
    val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    val dedupeMillis: Long = DEFAULT_DEDUPE_MILLIS,
    val maxKeys: Int = DEFAULT_MAX_KEYS,
) {
    init {
        require(maxPerWindow >= 1) { "maxPerWindow must be >= 1, was $maxPerWindow." }
        require(windowMillis >= 1) { "windowMillis must be >= 1, was $windowMillis." }
        require(dedupeMillis >= 0) { "dedupeMillis must be >= 0, was $dedupeMillis." }
        require(maxKeys >= 1) { "maxKeys must be >= 1, was $maxKeys." }
    }

    // Insertion order is admission order, so the first entry is the oldest key.
    private val admittedAt = LinkedHashMap<String, Long>()
    private var windowStart = Long.MIN_VALUE
    private var inWindow = 0

    /** How many errors [admit] has refused since construction or [clear]. */
    var refused: Long = 0L
        private set

    /**
     * Whether an error with [key] should be sent now; when it should, it counts against the window and
     * its key is remembered.
     */
    fun admit(key: String): Boolean {
        val at = now()
        if (windowStart == Long.MIN_VALUE || at - windowStart >= windowMillis) {
            windowStart = at
            inWindow = 0
        }
        val last = admittedAt[key]
        if ((last != null && at - last < dedupeMillis) || inWindow >= maxPerWindow) {
            refused++
            return false
        }
        inWindow++
        admittedAt.remove(key)
        admittedAt[key] = at
        while (admittedAt.size > maxKeys) admittedAt.remove(admittedAt.keys.first())
        return true
    }

    /** Forgets every key, the window and the [refused] count. */
    fun clear() {
        admittedAt.clear()
        windowStart = Long.MIN_VALUE
        inWindow = 0
        refused = 0L
    }

    /** Default limits for [ErrorSampler]. */
    companion object {
        /** Default errors admitted per window: 10. */
        const val DEFAULT_MAX_PER_WINDOW: Int = 10

        /** Default rate-limit window: one minute. */
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L

        /** Default time a key is refused after it is admitted: five minutes. */
        const val DEFAULT_DEDUPE_MILLIS: Long = 300_000L

        /** Default number of keys remembered: 256. */
        const val DEFAULT_MAX_KEYS: Int = 256
    }
}
