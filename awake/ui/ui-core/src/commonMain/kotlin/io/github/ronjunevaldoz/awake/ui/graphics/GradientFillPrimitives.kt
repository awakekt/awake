// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.graphics

import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.graphics2d.UiLinearGradient
import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

fun UiPrimitiveScope.gradientRect(
    slot: Rectangle,
    gradient: UiLinearGradient,
    overlay: Boolean = false,
) {
    dispatchPrimitive(
        UiDrawPrimitive.GradientQuad(
            x = slot.x,
            y = slot.y,
            w = slot.width,
            h = slot.height,
            gradient = gradient,
        ),
        overlay,
    )
}
