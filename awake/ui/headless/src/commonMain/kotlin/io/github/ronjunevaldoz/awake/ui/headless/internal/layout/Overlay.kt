// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.headless.internal.layout

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.canvas

fun UiPrimitiveScope.overlayScrim(slot: Rectangle, color: Color) {
    registerOverlayOcclusion(slot, isModal = true)
    canvas(slot) { drawRect(0f, 0f, slot.width, slot.height, color, overlay = true) }
}
