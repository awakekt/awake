/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

/**
 * What [AdaptiveSnapshotTimeline] does with a time or delta it can't use: a negative or non-finite
 * frame delta, a non-finite timestamp, or a negative lead.
 */
enum class TimelineInputMode {
    /** Throws [IllegalArgumentException], so a caller's bug surfaces where it happens. The default. */
    Reject,

    /**
     * Absorbs the bad value and counts it in [AdaptiveSnapshotTimeline.clampedInputs], for a frame loop
     * that would rather drop one bad delta than crash: a negative or non-finite delta, or a non-finite
     * timestamp, skips the call; an infinite lead skips the hold, and a negative or NaN lead becomes 0.
     */
    Clamp,
}
