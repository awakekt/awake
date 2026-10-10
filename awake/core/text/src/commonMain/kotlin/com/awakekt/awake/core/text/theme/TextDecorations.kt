/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.theme

import com.awakekt.awake.core.color.Color

/**
 * A copy of the text drawn under it in [color], moved by [offsetX] and [offsetY] dp: a hard drop
 * shadow, as games draw under text that sits over a busy scene.
 *
 * @property color The shadow's colour.
 * @property offsetX How far right it falls, in dp.
 * @property offsetY How far down it falls, in dp.
 */
data class TextShadow(val color: Color, val offsetX: Float = 1f, val offsetY: Float = 1f)

/**
 * A ring of [color] around every glyph, [width] dp wide, drawn under the text: what keeps a name
 * readable over any background.
 *
 * @property color The outline's colour.
 * @property width Its width in dp; above 0.
 */
data class TextOutline(val color: Color, val width: Float = 1f) {
    init {
        require(width > 0f) { "A text outline is wider than 0 dp; was $width." }
    }
}
