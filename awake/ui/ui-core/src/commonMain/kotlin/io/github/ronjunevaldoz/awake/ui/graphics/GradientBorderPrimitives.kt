// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.graphics

import io.github.ronjunevaldoz.awake.core.graphics2d.UiLinearGradient
import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.core.math2d.Dp
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.core.math2d.toPx

internal fun UiPrimitiveScope.gradientBorder(
    slot: Rectangle,
    width: Dp = 1f.dp,
    gradient: UiLinearGradient,
    overlay: Boolean = false,
) {
    val borderPx = width.toPx()
    if (borderPx <= 0f) return
    gradientRect(Rectangle(slot.x, slot.y, slot.width, borderPx), gradient, overlay)
    gradientRect(
        Rectangle(slot.x, slot.y + slot.height - borderPx, slot.width, borderPx),
        gradient,
        overlay,
    )
    gradientRect(Rectangle(slot.x, slot.y, borderPx, slot.height), gradient, overlay)
    gradientRect(
        Rectangle(slot.x + slot.width - borderPx, slot.y, borderPx, slot.height),
        gradient,
        overlay,
    )
}
