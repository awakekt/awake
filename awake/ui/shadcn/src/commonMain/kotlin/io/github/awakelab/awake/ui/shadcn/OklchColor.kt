/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.core.color.Color

typealias OklchColor = io.github.awakelab.awake.tailwind.OklchColor

fun oklch(
    lightness: Float,
    chroma: Float,
    hueDegrees: Float = 0f,
    alpha: Float = 1f,
): Color = io.github.awakelab.awake.tailwind.oklch(lightness, chroma, hueDegrees, alpha)
