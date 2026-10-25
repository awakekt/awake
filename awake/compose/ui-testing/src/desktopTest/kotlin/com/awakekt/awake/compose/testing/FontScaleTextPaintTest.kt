/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A host's font scale is painted, not only measured: text at [FONT_SCALES] paints exactly what the
 * same text at the scaled size paints, so its glyphs fill the box it was measured for and its own clip
 * cuts nothing more (#1053). Before, a scale below 1 measured wrapped text for smaller glyphs and
 * painted them at full size past the box, and a scale above 1 did not scale the text at all.
 */
class FontScaleTextPaintTest {
    private val text = "The quick brown fox jumps over the lazy dog again"

    @Test
    fun scaledTextPaintsWhatTheScaledSizePaints() {
        val failures = GlyphInk.cases().flatMap { case ->
            FONT_SCALES.mapNotNull { fontScale ->
                val scaled = case.style.copy(size = (case.size * fontScale).sp, lineHeight = case.style.lineHeight?.let { (it.value * fontScale).sp })
                val difference = difference(case.density, fontScale, case.size, { Text(text, Modifier.clipToBounds(), style = case.style) }) {
                    Text(text, Modifier.clipToBounds(), style = scaled)
                }
                if (difference > 0) "$case Text at font scale $fontScale: $difference pixels differ from ${case.size * fontScale}sp" else null
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aScaledFieldPaintsWhatTheScaledSizePaints() {
        val failures = GlyphInk.cases().filter { !it.tight }.flatMap { case ->
            FONT_SCALES.mapNotNull { fontScale ->
                val scaled = case.style.copy(size = (case.size * fontScale).sp)
                val difference = difference(case.density, fontScale, case.size, { field(case.style) }) { field(scaled) }
                if (difference > 0) "$case field at font scale $fontScale: $difference pixels differ from ${case.size * fontScale}sp" else null
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    context(_: Composer)
    private fun field(style: TextStyle) {
        BasicTextField(TextFieldState("Harbor Town jumps", cursor = 0), Modifier.padding(12.dp), style = style)
    }

    /**
     * Pixels whose ink differs between [scaled] painted at [fontScale] and [reference] painted at
     * font scale 1, each wrapped at 160 dp, either way round.
     */
    private fun difference(
        density: Float,
        fontScale: Float,
        size: Int,
        scaled: context(Composer) () -> Unit,
        reference: context(Composer) () -> Unit,
    ): Int {
        val width = ((160 + 2 * 24) * density).roundToInt() + size * 4
        val height = ((size * 12 + 48) * density).roundToInt()
        fun paint(scale: Float, content: context(Composer) () -> Unit) =
            GlyphInk.paint(
                GlyphInk.frame(density, width, height, fontScale = scale) {
                    Box(Modifier.padding(24.dp).width(160.dp + (size * 2).dp)) { content() }
                }.primitives,
                width,
                height,
            )
        val actual = paint(fontScale, scaled)
        val expected = paint(1f, reference)
        assertTrue(GlyphInk.inkedPixels(expected) > 0, "nothing drawn")
        return GlyphInk.lostPixels(expected, actual) + GlyphInk.lostPixels(actual, expected)
    }

    private companion object {
        val FONT_SCALES = listOf(0.85f, 1.3f)
    }
}
