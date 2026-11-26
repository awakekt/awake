/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

/**
 * Smooth per-edge alpha feathering for a textured 2D quad.
 *
 * Each inset is a normalized fraction (`0f..1f`) of the quad's width ([left], [right]) or height
 * ([top], [bottom]) over which opacity eases from `0` at that edge to `1` in the interior via a
 * C1 Hermite smoothstep curve (`t * t * (3 - 2 * t)`). Adjacent edges multiply at corners.
 */
data class EdgeFade(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "EdgeFade insets must lie in 0..1, got ($left, $top, $right, $bottom)."
        }
    }

    /** True when every edge inset is zero and the texture draws at uniform opacity. */
    val isNone: Boolean get() = left <= 0f && top <= 0f && right <= 0f && bottom <= 0f

    /** Smooth opacity multiplier in `0..1` at normalized quad coordinates ([u], [v]). */
    fun alphaAt(u: Float, v: Float): Float =
        smoothAxis(u, left, right) * smoothAxis(v, top, bottom)

    private fun smoothAxis(coord: Float, startFade: Float, endFade: Float): Float {
        var factor = 1f
        if (startFade > 0f && coord < startFade) {
            val t = (coord / startFade).coerceIn(0f, 1f)
            factor *= t * t * (3f - 2f * t)
        }
        if (endFade > 0f && coord > 1f - endFade) {
            val t = ((1f - coord) / endFade).coerceIn(0f, 1f)
            factor *= t * t * (3f - 2f * t)
        }
        return factor
    }

    /** Common [EdgeFade] constants. */
    companion object {
        /** No edge feathering. */
        val None: EdgeFade = EdgeFade()
    }
}
