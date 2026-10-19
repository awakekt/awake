/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color

/** The fallback alpha for a fully transparent tint; an opaque tint stays opaque. */
private const val FALLBACK_MIN_ALPHA = 0.6f

/**
 * Computes the fallback color used when a backdrop blur cannot be executed (e.g. quality is Off or
 * hardware cannot snapshot the destination).
 *
 * @param tint The glass tint.
 * @return The fallback color with boosted alpha.
 */
fun backdropFallbackColor(tint: Color): Color =
    tint.withAlpha(FALLBACK_MIN_ALPHA + (1f - FALLBACK_MIN_ALPHA) * tint.a)
