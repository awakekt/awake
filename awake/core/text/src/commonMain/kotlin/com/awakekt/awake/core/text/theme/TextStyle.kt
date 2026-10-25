/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.theme

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont

/**
 * Groups text-related styling properties.
 *
 * @property color The foreground text color, or null to inherit.
 * @property size The font size in sp, or null to inherit.
 * @property lineHeight Optional authored line advance. Null keeps the font's intrinsic metrics.
 * @property scale Relative scale multiplier applied to the text.
 * @property weight Font weight indicating stroke thickness.
 * @property letterSpacing Additional spacing between adjacent characters.
 * @property shadow A shadow drawn under the text, or null to inherit.
 * @property outline An outline drawn around the text, or null to inherit.
 * @property font The typeface to measure and draw with, or null to inherit `LocalFont`.
 *   Compared by whatever equality the [UiFont] instance itself provides -- identity unless a
 *   font overrides it -- which matches the atlas cache a renderer keys on the same instance.
 * @property kerning Whether the font's pair kerning moves adjacent characters, such as the `A` and `V`
 *   of "AV", closer or apart; null to inherit. Kerning applies unless a style sets this to false, as a
 *   browser's `font-kerning: auto` does, and measuring, painting, the caret and hit testing all follow it.
 */
data class TextStyle(
    val color: Color? = null,
    val size: Sp? = null,
    val lineHeight: Sp? = null,
    val scale: Float = 1f,
    val weight: FontWeight = FontWeight.Normal,
    val letterSpacing: Sp = 0f.sp,
    val shadow: TextShadow? = null,
    val outline: TextOutline? = null,
    val font: UiFont? = null,
    val kerning: Boolean? = null,
) {
    /**
     * Default text style constants.
     */
    companion object {
        /** Default empty text style with no overrides. */
        val Default = TextStyle()
    }

    /**
     * Merges this text style with [other], with properties in [other] taking precedence.
     *
     * @param other The style whose defined properties should override this style's properties.
     * @return The merged [TextStyle] instance.
     */
    infix fun then(other: TextStyle): TextStyle = when {
        // A merge that changes nothing returns an instance that already exists. Every Text merges
        // its style over the inherited one every frame, and most of those merges are no-ops.
        other.inheritsNothingFrom(this) -> other
        other.changesNothingIn(this) -> this
        else -> TextStyle(
            other.color ?: color,
            other.size ?: size,
            other.lineHeight ?: lineHeight,
            other.scale,
            other.weight,
            other.letterSpacing,
            other.shadow ?: shadow,
            other.outline ?: outline,
            other.font ?: font,
            other.kerning ?: kerning,
        )
    }

    /** Whether merging this over [base] gives this: every field it leaves unset is unset in [base] too. */
    private fun inheritsNothingFrom(base: TextStyle): Boolean =
        (color != null || base.color == null) && (size != null || base.size == null) &&
            (lineHeight != null || base.lineHeight == null) && (shadow != null || base.shadow == null) &&
            (outline != null || base.outline == null) && (font != null || base.font == null) &&
            (kerning != null || base.kerning == null)

    /** Whether merging this over [base] gives [base]: every field it sets, [base] already has. */
    private fun changesNothingIn(base: TextStyle): Boolean =
        sameOrUnset(color, base.color) && sameOrUnset(size, base.size) && sameOrUnset(lineHeight, base.lineHeight) &&
            sameOrUnset(shadow, base.shadow) && sameOrUnset(outline, base.outline) && sameOrUnset(font, base.font) &&
            sameOrUnset(kerning, base.kerning) && scale == base.scale && weight == base.weight &&
            letterSpacing == base.letterSpacing
}

private fun <T> sameOrUnset(value: T?, base: T?): Boolean = value == null || value == base
