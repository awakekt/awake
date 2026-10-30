/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts

/** Where each line of world text sits across the text's own width. */
enum class WorldTextAlign {
    /** Lines start at the text's left edge. */
    Start,

    /** Lines are centred on the text's origin. */
    Center,

    /** Lines end at the text's right edge. */
    End,
}

/**
 * How text placed in the world looks: its face, size and colour, and how its lines line up.
 *
 * The text is drawn from [font]'s distance field, so it must be a distance-field font, such as
 * [UiFonts.default] or one [UiFonts.load] reads. Its size is in world units, so a sign keeps its
 * size in the world however near or far it is seen.
 *
 * Outlines and soft shadows are not drawn yet: they come from a wide-reach field drawn with the
 * glyph in the same draw, and the draw's `field` uniform keeps room for them.
 *
 * @property font The face the text is drawn in; a distance-field font.
 * @property size The em size, in world units: 0.25 is a quarter of a world unit per em.
 * @property weight Which of [font]'s weights the text uses.
 * @property color Straight-alpha colour of every glyph.
 * @property align How each line lines up across the text's width.
 * @property lineSpacing The distance from one line's top to the next, as a multiple of [font]'s line height.
 */
data class WorldTextStyle(
    val font: UiFont = UiFonts.default(),
    val size: Float = DEFAULT_SIZE,
    val weight: FontWeight = FontWeight.Normal,
    val color: Color = Color.White,
    val align: WorldTextAlign = WorldTextAlign.Center,
    val lineSpacing: Float = 1f,
) {
    init {
        require(size > 0f && size.isFinite()) { "A world text's size must be a positive number of world units, not $size" }
        require(lineSpacing > 0f && lineSpacing.isFinite()) { "A world text's lineSpacing must be positive, not $lineSpacing" }
        require(font.samplingMode == UiFontSamplingMode.DistanceField) {
            "World text draws from a distance field; this font samples as ${font.samplingMode}"
        }
    }

    /** Holds [DEFAULT_SIZE]. */
    companion object {
        /** The em size a style takes when none is given, in world units. */
        const val DEFAULT_SIZE: Float = 0.25f
    }
}
