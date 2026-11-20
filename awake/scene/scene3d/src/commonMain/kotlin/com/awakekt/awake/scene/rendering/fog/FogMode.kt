/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import kotlinx.serialization.Serializable

/** How distance fog grows with distance from the eye (#1807). */
@Serializable
enum class FogMode {
    /** `1 - exp(-density * distance)`: thickens forever, never reaching the fog colour. */
    Exponential,

    /** None nearer than `start`, rising evenly to the full fog colour at `end`. */
    Linear,
}

/** The nearest a linear fog may start, in metres from the eye. */
internal const val MIN_FOG_START = 0f

/** Why a linear range the shaders cannot ramp across is refused, or null when [start] and [end] can be ramped. */
internal fun fogRangeProblem(start: Float, end: Float): String? = when {
    !start.isFinite() || start < MIN_FOG_START -> "start must be finite and >= 0 metres, was $start"
    !end.isFinite() || end <= start -> "end must be finite and beyond start ($start), was $end"
    else -> null
}

/** Refuses a linear range the shaders cannot ramp across: [start] from the eye, below a farther [end]. */
internal fun requireFogRange(start: Float, end: Float) {
    fogRangeProblem(start, end)?.let { throw IllegalArgumentException("Fog $it") }
}
