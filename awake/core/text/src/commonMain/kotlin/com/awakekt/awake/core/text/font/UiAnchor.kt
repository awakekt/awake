/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.scope.pixelPerfectTextScale
//
// enum class UiAnchor {
//    TopLeft,
//    TopRight,
//    BottomLeft,
//    BottomRight
// }
//
// fun UiSlot.anchored(
//    anchor: UiAnchor,
//    width: Float,
//    height: Float,
//    margin: UiInsets = UiInsets.Zero
// ): UiSlot {
//    val resolvedWidth = width.coerceAtLeast(0f)
//    val resolvedHeight = height.coerceAtLeast(0f)
//    val anchoredX = when (anchor) {
//        UiAnchor.TopLeft, UiAnchor.BottomLeft -> x + margin.start.toPx()
//        UiAnchor.TopRight, UiAnchor.BottomRight -> x + this.width - resolvedWidth - margin.end.toPx()
//    }
//    val anchoredY = when (anchor) {
//        UiAnchor.TopLeft, UiAnchor.TopRight -> y + margin.top.toPx()
//        UiAnchor.BottomLeft, UiAnchor.BottomRight -> y + this.height - resolvedHeight - margin.bottom.toPx()
//    }
//    return UiSlot(
//        x = anchoredX,
//        y = anchoredY,
//        width = resolvedWidth,
//        height = resolvedHeight
//    )
// }

/**
 * Calculates the total pixel height required to render a multiline block of text.
 *
 * @param lineCount The number of lines of text to be rendered.
 * @param textScale The relative scale multiplier applied to the font's base cell size.
 * @param gap The vertical gap in pixels between adjacent lines of text.
 * @return The total computed height in pixels, or `0f` if [lineCount] is zero or negative.
 */
fun UiFont.textBlockHeight(
    lineCount: Int,
    textScale: Float = 1f,
    gap: Float = 8f,
): Float {
    if (lineCount <= 0) {
        return 0f
    }
    val glyphPx = cellSize * pixelPerfectTextScale(textScale)
    return lineCount * glyphPx + (lineCount - 1) * gap
}
