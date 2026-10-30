/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.theme

import com.awakekt.awake.core.color.Color

/**
 * A copy of the text drawn under it in [color], moved by [offsetX] and [offsetY] dp: a drop shadow, as games draw
 * under text that sits over a busy scene. Hard by default; a [blur] softens its edge into a falloff that reaches
 * [blur] dp either side of it, a glow when the offset is zero.
 *
 * @property color The shadow's colour.
 * @property offsetX How far right it falls, in dp.
 * @property offsetY How far down it falls, in dp.
 * @property blur How far either side of its edge the shadow fades, in dp; 0 for a hard shadow.
 */
data class TextShadow(val color: Color, val offsetX: Float = 1f, val offsetY: Float = 1f, val blur: Float = 0f) {
    init {
        require(blur >= 0f) { "A text shadow's blur is 0 dp or more; was $blur." }
    }

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(color: Color, offsetX: Float = 1f, offsetY: Float = 1f) : this(color, offsetX, offsetY, 0f)

    /** The `copy` from before [blur], which keeps this one's [blur]. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(color: Color = this.color, offsetX: Float = this.offsetX, offsetY: Float = this.offsetY): TextShadow =
        TextShadow(color, offsetX, offsetY, blur)
}

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
