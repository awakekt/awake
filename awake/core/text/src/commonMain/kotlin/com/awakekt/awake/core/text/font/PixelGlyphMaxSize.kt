/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * This font, drawing text up to [maxPixelSize] device pixels from its [UiFont.pixelGlyphs] and larger
 * text from its distance field. 0 draws every size from the distance field, for text that should look
 * the same at every size; [UiFonts.bitmap] keeps its pixel-art look either way, having no outlines.
 *
 * Everything else, the atlas included, is this font's own, so a renderer uploads its atlas once whichever
 * of the two is drawn. Make it once, as a font is made, and keep it: a style compares fonts by identity.
 *
 * @param maxPixelSize The largest size that draws from pixel glyphs, up to [PixelGlyphs.MAX_PIXEL_SIZE].
 * @return A font that draws as this one does, with its own limit.
 */
fun UiFont.withPixelGlyphMaxSize(maxPixelSize: Int): UiFont {
    require(maxPixelSize in 0..PixelGlyphs.MAX_PIXEL_SIZE) {
        "maxPixelSize must be between 0 and ${PixelGlyphs.MAX_PIXEL_SIZE}, not $maxPixelSize"
    }
    val base = if (this is PixelGlyphLimitedFont) delegate else this
    return if (maxPixelSize == base.pixelGlyphMaxSize) base else PixelGlyphLimitedFont(base, maxPixelSize)
}

/** [delegate] with its own [pixelGlyphMaxSize]; see [withPixelGlyphMaxSize]. */
private class PixelGlyphLimitedFont(
    val delegate: UiFont,
    override val pixelGlyphMaxSize: Int,
    private val origin: PixelGlyphLimitedFont? = null,
) : UiFont by delegate {
    override val pixelGlyphs: PixelGlyphs? = if (pixelGlyphMaxSize > 0) delegate.pixelGlyphs else null

    private val proportionalFont: UiFont by lazy { buildNumericVariant(FontNumerals.Proportional) }
    private val tabularFont: UiFont by lazy { buildNumericVariant(FontNumerals.Tabular) }

    // A single-face font answers itself here; this answers the font it wraps, whose atlas is the one to upload.
    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont = delegate.fontFor(codePoint, weight)

    override fun withNumerals(numerals: FontNumerals?): UiFont = when {
        numerals == null -> this
        origin != null -> origin.withNumerals(numerals)
        numerals == FontNumerals.Proportional -> proportionalFont
        else -> tabularFont
    }

    private fun buildNumericVariant(numerals: FontNumerals): UiFont {
        val variant = delegate.withNumerals(numerals)
        return when {
            variant === delegate -> this
            pixelGlyphMaxSize == variant.pixelGlyphMaxSize -> variant
            else -> PixelGlyphLimitedFont(variant, pixelGlyphMaxSize, origin = this)
        }
    }
}
