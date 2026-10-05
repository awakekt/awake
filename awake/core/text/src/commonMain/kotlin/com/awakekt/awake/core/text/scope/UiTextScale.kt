/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.scope

import kotlin.math.roundToInt

/**
 * Snaps a requested floating-point text scale to the nearest discrete step to preserve sharp pixel alignment.
 *
 * @param requestedScale The desired arbitrary text scaling factor.
 * @param step The quantization step size, defaulting to `0.25f`.
 * @return The snapped text scale, coerced to at least `1f`.
 */
fun pixelPerfectTextScale(requestedScale: Float, step: Float = 0.25f): Float {
    val safeStep = step.takeIf { it.isFinite() && it > 0f } ?: 0.25f
    val snapped = (requestedScale / safeStep).roundToInt()
        .coerceAtLeast((1f / safeStep).roundToInt()) * safeStep
    return snapped.coerceAtLeast(1f)
}
