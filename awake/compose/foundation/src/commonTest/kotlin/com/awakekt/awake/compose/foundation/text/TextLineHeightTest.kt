/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line box of #1024: `line-height: normal` rounded as a browser rounds it, every line the same
 * whole number of device pixels below the last, and an authored line height honoured on many lines.
 */
class TextLineHeightTest {
    private val white = Color(1f, 1f, 1f, 1f)

    /** The default family held to its distance field, so every glyph is one quad with the face's metrics. */
    private val font = UiFonts.default().withPixelGlyphMaxSize(0)

    /**
     * Chrome on Windows lays Roboto out at 13, 14, 17 and 22 px for 11, 12, 14 and 18 px text, with the
     * baseline at 10, 11, 13 and 17: its `OS/2` win ascent and descent, each rounded to a whole pixel
     * (`text-layout/chromium.json`). The exact sums were 12.89, 14.06, 16.41 and 21.09.
     */
    @Test
    fun theDefaultLineHeightIsTheBrowsersNormal() {
        val expected = mapOf(11 to (13 to 10), 12 to (14 to 11), 14 to (17 to 13), 18 to (22 to 17))
        for ((size, line) in expected) {
            val output = frame(density = 1f) { Text("Hg", Modifier.testTag("text"), style = style(size.toFloat())) }
            assertEquals(line.first, output.textBox().height, "$size px line height")
            assertEquals(line.second.toFloat(), baselines(output).single(), 1e-3f, "$size px baseline")
        }
    }

    /**
     * Ten lines at 125% and 150% are ten equal steps: the advance is rounded once for the run rather
     * than each baseline being rounded from a fractional advance, which alternated the gaps by a pixel.
     * The measured box is exactly ten of those steps, and every baseline is on the pixel grid.
     */
    @Test
    fun everyGapInATenLineParagraphIsTheSameAtFractionalDensities() {
        val tenLines = List(10) { "Hg" }.joinToString("\n")
        for (density in listOf(1.25f, 1.5f)) {
            for (size in 9..24) {
                val output = frame(density) { Text(tenLines, Modifier.testTag("text"), style = style(size.toFloat())) }
                val baselines = baselines(output)
                val case = "${size}sp at $density"
                assertEquals(10, baselines.size, case)
                val gap = baselines[1] - baselines[0]
                assertEquals(floor(gap), gap, case)
                for (i in 1 until baselines.size) {
                    assertEquals(gap, baselines[i] - baselines[i - 1], 1e-3f, "$case gap $i of $baselines")
                    assertEquals(floor(baselines[i] + 0.5f), baselines[i], 1e-3f, "$case baseline $i of $baselines")
                }
                assertEquals((gap * 10).toInt(), output.textBox().height, case)
            }
        }
    }

    /**
     * A line height tighter than the font's applies to every line, as CSS applies it. The half-leading
     * is negative, so the glyphs reach outside their 10 px boxes: 14 px Roboto is 17 px of ascent and
     * descent, the half above is floor(-3.5) = -4 px, and the baseline sits at 13 - 4 = 9, where
     * Chrome puts it. The old rule widened a multi-line box back to the font's 16.41 px.
     */
    @Test
    fun aTighterLineHeightAppliesToMultilineText() {
        val output = frame(density = 1f) {
            Text("Hg\nHg\nHg", Modifier.testTag("text"), style = style(14f).copy(lineHeight = 10.sp))
        }

        assertEquals(30, output.textBox().height)
        assertEquals(listOf(9f, 19f, 29f), baselines(output))
    }

    /** A looser one centres the glyphs: (24 - 17) / 2 floors to 3 px above, so the baseline is 13 + 3. */
    @Test
    fun aLooserLineHeightSplitsItsLeadingAboveAndBelow() {
        val output = frame(density = 1f) {
            Text("Hg\nHg", Modifier.testTag("text"), style = style(14f).copy(lineHeight = 24.sp))
        }

        assertEquals(48, output.textBox().height)
        assertEquals(listOf(16f, 40f), baselines(output))
    }

    private fun style(size: Float) = TextStyle(font = font, color = white, size = size.sp)

    private fun frame(density: Float, content: context(Composer) () -> Unit): FrameOutput =
        ComposeHost(density = density).frame(FrameInput(800, 800), content)

    private fun FrameOutput.textBox() = semantics.single { it.testTag == "text" }

    /** The baseline each `H` was drawn on, recovered from its quad and the face's metrics. */
    private fun baselines(output: FrameOutput): List<Float> {
        val glyphs = output.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        return glyphs.filterIndexed { index, _ -> index % 2 == 0 }.map { glyph ->
            val face = checkNotNull(glyph.font)
            val rect = checkNotNull(face.glyphFor('H'.code, FontWeight.Normal))
            val glyphPx = glyph.h / rect.heightEm
            glyph.y - rect.offsetYEm * glyphPx + face.ascentEm * glyphPx
        }
    }
}
