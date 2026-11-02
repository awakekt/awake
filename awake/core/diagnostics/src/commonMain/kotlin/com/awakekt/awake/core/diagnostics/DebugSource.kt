/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f

/**
 * Something a game can show about itself while it plays: a few labelled readings for an overlay ([rows]) and
 * world-space lines to draw over the scene ([lines]).
 *
 * A source is a view of a probe: plain counters a system keeps whether or not anyone is looking. The source measures
 * nothing itself, so turning it off never changes what the probe counts, and a test asserts on the probe directly.
 *
 * Only primitives, [Vec3f] and [Color] cross this contract, so a source depends on nothing that draws. Hosts own the
 * sinks and reuse them from frame to frame, so writing a row or a line allocates nothing. A source that is off is
 * never called ([DebugRegistry]).
 */
interface DebugSource {
    /** What turns it on: `?debug=<id>` on the web and `--debug=<id>` on desktop ([DebugFlags]). Lower case. */
    val id: String

    /** The heading its rows go under. */
    val title: String

    /** Writes its readings into [out]. Called at the overlay's rate, and only while the source is on. */
    fun rows(out: DebugRows)

    /**
     * Writes its lines into [out]. Called once a frame, and only while the source is on and something draws.
     * A source with nothing to draw leaves this empty.
     */
    fun lines(out: DebugLines) = Unit
}

/** Where a [DebugSource] writes its readings. The host reuses what it shows, so a row costs no allocation of its own. */
interface DebugRows {
    /** An integer reading; [warn] shows it as something to look at. */
    fun row(label: String, value: Int, warn: Boolean = false)

    /** [value] shown to [decimals] places. */
    fun row(label: String, value: Float, decimals: Int = 1, warn: Boolean = false)

    /** A reading that is already text, such as a state's name. Pass the same `String` instance while it is unchanged. */
    fun row(label: String, value: String, warn: Boolean = false)
}

/** Where a [DebugSource] writes its lines. The host copies [a] and [b], so a source may reuse its own vectors. */
fun interface DebugLines {
    /** A line from [a] to [b] in world space, in [color]. */
    fun line(a: Vec3f, b: Vec3f, color: Color)
}
