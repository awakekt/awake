/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Accented capitals keep their accents, from 12 to 96 sp, in `Text` and in a text field, in the built-in font and
 * in a loaded one (#1058). Their marks are the glyphs' highest ink, above the cap height and, for Å, nearly at
 * the line box's top, so they are what a clip sized to a text box cuts first (#1024, #1053).
 *
 * Two kinds of check: the ink a clip or a layer takes from the text, counted pixel by pixel as the other
 * glyph-clipping tests do ([GlyphInk]); and that the accents are drawn at all, since a clip that cuts nothing
 * proves little if the text drew a '?' or no mark.
 *
 * Negative control: with the loaded font baked from `UiFonts.ASCII_CHARS`, so its accented capitals draw as
 * '?', [theAccentsAreDrawn] fails: "loaded TTF 12sp d1.0 font line: 'É' has 29 inked pixels, no more than 'E'
 * with 36", and the same for every size, density and accent.
 */
class AccentedCapitalsClipTest {
    private val fonts = listOf("built-in" to UiFonts.default(), "loaded TTF" to GlyphInk.loadedRoboto)

    /** Capitals with marks above the cap height (À É Î Õ Ü Å Ć Č Ĝ Ń Ő Ř Ś Š Ů Ž), then Ç, Ł and Ø, whose marks sit in or beside the capital. */
    private val capitals = "ÀÉÎÕÜ ÅĆČĜ ŃŐŘŚŠŮŽ ÇŁØ"

    @Test
    fun aLayerAroundTextKeepsAccentedCapitals() {
        val failures = cases().mapNotNull { case ->
            val lost = lostToALayer(capitals, case)
            "$case: the layer cut $lost pixels of ink".takeIf { lost > 0 }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aTextFieldKeepsAccentedCapitals() {
        val failures = cases().mapNotNull { case ->
            val lost = lostToTheFieldsClip(capitals, case)
            "$case: the field cut $lost pixels of ink".takeIf { lost > 0 }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** An accent above a capital is ink the bare capital does not have, at every size and in both fonts, and not a '?'. */
    @Test
    fun theAccentsAreDrawn() {
        val failures = cases().filter { !it.tight }.flatMap { case ->
            listOf("É" to "E", "Å" to "A", "Ž" to "Z", "Ő" to "O").mapNotNull { (accented, bare) ->
                val withAccent = inkOf(accented, case)
                val without = inkOf(bare, case)
                val standIn = inkOf("?", case)
                when {
                    withAccent <= without -> "$case: '$accented' has $withAccent inked pixels, no more than '$bare' with $without"
                    withAccent == standIn -> "$case: '$accented' draws as the stand-in '?'"
                    else -> null
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Both fonts at 12 to 96 sp, on a plain and a 1.5 display, with the font's line height and one em (`leading-none`). */
    private fun cases(): List<GlyphInk.Case> = fonts.flatMap { (name, font) ->
        SIZES.flatMap { size -> listOf(1f, 1.5f).flatMap { density -> listOf(false, true).map { GlyphInk.Case(name, font, size, density, it) } } }
    }

    /** Pixels of ink a barely translucent layer takes away from [text], against the same text unwrapped. */
    private fun lostToALayer(text: String, case: GlyphInk.Case): Int {
        val width = ((case.size * text.length + 80) * case.density).roundToInt()
        val height = ((case.size * 3 + 80) * case.density).roundToInt()
        val plain = GlyphInk.frame(case.density, width, height) {
            Box(Modifier.padding(40.dp)) { Box { Text(text, style = case.style) } }
        }
        val layered = GlyphInk.frame(case.density, width, height) {
            Box(Modifier.padding(40.dp)) { Box(Modifier.graphicsLayer(alpha = 0.999f)) { Text(text, style = case.style) } }
        }
        val expected = GlyphInk.paint(plain.primitives, width, height)
        assertTrue(GlyphInk.inkedPixels(expected) > 0, "$case '$text' drew nothing")
        return GlyphInk.lostPixels(expected, GlyphInk.paint(layered.primitives, width, height, layered.graphicsLayers))
    }

    /** Pixels of ink a padded single-line field's clip takes from [text], against the same frame unclipped. */
    private fun lostToTheFieldsClip(text: String, case: GlyphInk.Case): Int {
        // Room for any glyph's ink past its line box: half an em on every side.
        val pad = maxOf(16, case.size / 2)
        val width = ((case.size * text.length + 2 * pad + 48) * case.density).roundToInt()
        val height = ((case.size * 2 + 2 * pad + 48) * case.density).roundToInt()
        val primitives = GlyphInk.frame(case.density, width, height) {
            Box(Modifier.padding(24.dp)) {
                BasicTextField(TextFieldState(text, cursor = 0), Modifier.padding(pad.dp), style = case.style, singleLine = true)
            }
        }.primitives
        val unclipped = GlyphInk.paint(GlyphInk.withoutClips(primitives), width, height)
        assertTrue(GlyphInk.inkedPixels(unclipped) > 0, "$case '$text' drew nothing")
        return GlyphInk.lostPixels(unclipped, GlyphInk.paint(primitives, width, height))
    }

    /** The inked pixels of [text] drawn alone in a roomy frame. */
    private fun inkOf(text: String, case: GlyphInk.Case): Int {
        val width = ((case.size * 2 + 80) * case.density).roundToInt()
        val height = ((case.size * 3 + 80) * case.density).roundToInt()
        val frame = GlyphInk.frame(case.density, width, height) { Box(Modifier.padding(40.dp)) { Text(text, style = case.style) } }
        return GlyphInk.inkedPixels(GlyphInk.paint(frame.primitives, width, height))
    }

    private companion object {
        val SIZES = listOf(12, 14, 16, 18, 24, 32, 48, 72, 96)
    }
}
