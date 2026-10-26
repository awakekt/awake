/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Every glyph of the built-in font, `UiFonts.default()`, on the CPU raster the UI baselines use, so a
 * change to how text draws re-records these and the Render Evidence comment shows its before and after
 * (#1055).
 *
 * Each sheet holds the whole set at 12, 24 and 48 sp, top to bottom, in neutral greys, recorded as an 8-bit
 * grey PNG to keep a baseline every text change re-records small. The glyphs alone, near-black on white, in
 * Regular, Bold and Black at device scale 1, and in Regular at 1.5; the same glyphs white on near-black, the
 * HUD case, in Regular and Bold at 1 and Regular at 1.5 (#1064); the layout boxes in Regular at 1 and 1.5.
 * Within a size the glyphs fill rows of 17 in [BuiltInGlyphs.specimen]
 * order: `UiFonts.DEFAULT_CHARS`, then anything else the font draws. With today's 102 glyphs that is 6 rows:
 * space to '0', then '1' to 'A', 'B' to 'R', 'S' to 'c', 'd' to 't', and 'u' to '−'. A glyph sits in the
 * same column and row at every size and in every sheet, so a one-glyph change is one cell of the diff, and
 * a declared glyph the font loses shows as '?' in its own cell. A glyph the font gains adds a cell, and
 * rows as needed, with no change to this test.
 *
 * The sizes take both paths text draws by: whole sizes up to 24 device pixels draw pixel glyphs (12 and
 * 24 at scale 1, 18 at 1.5), larger ones the distance field (48 at scale 1, 36 and 72 at 1.5). Both draw
 * with the one text coverage rule (#1003), so a weight change between the 24 and 48 rows is the font's,
 * not the path's; how much ink each polarity holds is measured against Chromium by
 * `TextPolarityWeightTest`, not here.
 *
 * - `glyph-specimen-<weight>-<scale>x`: the glyphs alone.
 * - `glyph-specimen-<weight>-<scale>x-light-on-dark`: the glyphs alone, white on near-black.
 * - `glyph-boxes-regular-<scale>x`: the same grid with each glyph's layout box, the `Text` node's own
 *   bounds, tinted and framed beneath the ink. Ink outside the tint is ink a clip or layer the size of
 *   the text's box cuts (#1053).
 *
 * Re-record with `-DAWAKE_RECORD_SNAPSHOTS=true` only after the change is explained; see
 * [BuiltInFontCoverageTest] for which code points the set holds and lacks.
 */
class GlyphSpecimenBaselineTest {
    @Test
    fun regularAtScale1() = assertSheet(Sheet.Specimen, FontWeight.Normal, 1f)

    @Test
    fun boldAtScale1() = assertSheet(Sheet.Specimen, FontWeight.Bold, 1f)

    @Test
    fun blackAtScale1() = assertSheet(Sheet.Specimen, FontWeight.Black, 1f)

    @Test
    fun regularAtScale15() = assertSheet(Sheet.Specimen, FontWeight.Normal, 1.5f)

    @Test
    fun regularBoxesAtScale1() = assertSheet(Sheet.Boxes, FontWeight.Normal, 1f)

    @Test
    fun regularBoxesAtScale15() = assertSheet(Sheet.Boxes, FontWeight.Normal, 1.5f)

    @Test
    fun regularLightOnDarkAtScale1() = assertSheet(Sheet.Specimen, FontWeight.Normal, 1f, Polarity.LightOnDark)

    @Test
    fun boldLightOnDarkAtScale1() = assertSheet(Sheet.Specimen, FontWeight.Bold, 1f, Polarity.LightOnDark)

    @Test
    fun regularLightOnDarkAtScale15() = assertSheet(Sheet.Specimen, FontWeight.Normal, 1.5f, Polarity.LightOnDark)

    private enum class Sheet(val prefix: String) { Specimen("glyph-specimen"), Boxes("glyph-boxes") }

    /** The sheet's greys: near-black ink on white cells, or the same greys the other way round (#1064). */
    private enum class Polarity(val suffix: String, val ground: Color, val cell: Color, val ink: Color) {
        DarkOnLight("", Color(0.78f, 0.78f, 0.78f, 1f), Color(1f, 1f, 1f, 1f), Color(0.09f, 0.09f, 0.09f, 1f)),
        LightOnDark("-light-on-dark", Color(0.3f, 0.3f, 0.3f, 1f), Color(0.09f, 0.09f, 0.09f, 1f), Color(1f, 1f, 1f, 1f)),
    }

    private fun assertSheet(sheet: Sheet, weight: FontWeight, scale: Float, polarity: Polarity = Polarity.DarkOnLight) {
        val widthDp = SIZES.maxOf { cellWidth(it) * COLUMNS } + 2 * MARGIN
        val heightDp = SIZES.sumOf { cellHeight(it) * rows } + GAP * (SIZES.size - 1) + 2 * MARGIN
        val width = (widthDp * scale).roundToInt()
        val height = (heightDp * scale).roundToInt()
        val primitives = ComposeHost(density = scale).frame(FrameInput(width, height)) {
            Column(Modifier.padding(MARGIN.dp), verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
                for (size in SIZES) Grid(sheet, weight, size, polarity)
            }
        }.primitives
        val name = "${sheet.prefix}-${weightName(weight)}-${if (scale == 1f) "1" else scale.toString()}x${polarity.suffix}"
        primitives.assertMatchesGrayscaleBaseline(name, width, height, background = polarity.ground)
    }

    context(_: Composer)
    private fun Grid(sheet: Sheet, weight: FontWeight, size: Int, polarity: Polarity) {
        val style = TextStyle(color = polarity.ink, size = size.sp, weight = weight, font = UiFonts.default())
        val inset = cellInset(size).dp
        Column(Modifier.background(polarity.cell)) {
            for (row in 0 until rows) {
                Row {
                    for (column in 0 until COLUMNS) {
                        val index = row * COLUMNS + column
                        Box(Modifier.size(cellWidth(size).dp, cellHeight(size).dp)) {
                            val codePoint = glyphs.getOrNull(index) ?: return@Box
                            val box = if (sheet == Sheet.Boxes) Modifier.drawBehind { outlinedBox() } else Modifier
                            Text(String(Character.toChars(codePoint)), Modifier.offset(inset, inset).then(box), style = style)
                        }
                    }
                }
            }
        }
    }

    /**
     * The node's bounds tinted exactly, framed by a one-pixel outline just outside them. Drawn before the
     * text, so ink covers both: an outline inside the bounds would hide the ink at the box's edges.
     */
    private fun DrawScope.outlinedBox() {
        val w = width.toFloat()
        val h = height.toFloat()
        drawRect(color = BOX_TINT)
        drawRect(-1f, -1f, w + 2f, 1f, BOX_OUTLINE)
        drawRect(-1f, h, w + 2f, 1f, BOX_OUTLINE)
        drawRect(-1f, 0f, 1f, h, BOX_OUTLINE)
        drawRect(w, 0f, 1f, h, BOX_OUTLINE)
    }

    private fun weightName(weight: FontWeight): String = when (weight) {
        FontWeight.Bold -> "bold"
        FontWeight.Black -> "black"
        else -> "regular"
    }

    private companion object {
        val SIZES = listOf(12, 24, 48)
        const val COLUMNS = 17

        /** Read off the font once: every weight's coverage scans all of Unicode. */
        val glyphs: List<Int> by lazy { BuiltInGlyphs.specimen(UiFonts.default()) }
        val rows: Int get() = (glyphs.size + COLUMNS - 1) / COLUMNS

        /** Cells are 4/3 em wide and 3/2 em tall: room for the widest glyph ('@', 0.9 em) and a 1.17 em line. */
        fun cellWidth(size: Int) = size * 4 / 3
        fun cellHeight(size: Int) = size * 3 / 2

        /** A sixth of an em, rounded to even dp so the text's origin is a whole pixel at scale 1.5 too, which pixel glyphs need. */
        fun cellInset(size: Int) = size / 6 / 2 * 2

        /** Around the sheet, in dp. */
        const val MARGIN = 8

        /** Between one size's grid and the next, in dp. */
        const val GAP = 8

        // Neutral greys only (Polarity's too): the sheets are recorded as 8-bit grey PNGs, which a coloured pixel would fail.
        val BOX_TINT = Color(0.88f, 0.88f, 0.88f, 1f)
        val BOX_OUTLINE = Color(0.55f, 0.55f, 0.55f, 1f)
    }
}
