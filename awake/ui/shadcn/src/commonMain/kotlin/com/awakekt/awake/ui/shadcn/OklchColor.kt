/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.core.color.Color

typealias OklchColor = com.awakekt.awake.tailwind.OklchColor

fun oklch(
    lightness: Float,
    chroma: Float,
    hueDegrees: Float = 0f,
    alpha: Float = 1f,
): Color = com.awakekt.awake.tailwind.oklch(lightness, chroma, hueDegrees, alpha)
