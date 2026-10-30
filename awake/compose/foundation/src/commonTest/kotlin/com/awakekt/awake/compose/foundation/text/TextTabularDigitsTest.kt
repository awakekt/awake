/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontNumerals
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tabular and proportional numeric figure spacing (#1023): equal-width digits across `0`..`9` under
 * [FontNumerals.Tabular] and a stable right edge on a right-aligned counter from `1` to `999`.
 */
class TextTabularDigitsTest {
    private val white = Color(1f, 1f, 1f, 1f)
    private val font = UiFonts.default()

    @Test
    fun tabularDigitsAdvanceEveryDigitByTheSameWidth() {
        val digits = "0123456789"
        for (sizePx in listOf(14f, 32f)) {
            val run = TextRun(
                text = digits,
                style = TextStyle(size = sizePx.sp, numerals = FontNumerals.Tabular),
                font = font,
                glyphPx = sizePx,
                density = 1f,
            )
            val expectedStep = run.offsetAt(1) - run.offsetAt(0)
            assertTrue(expectedStep > 0f)
            for (index in 1 until digits.length) {
                val step = run.offsetAt(index + 1) - run.offsetAt(index)
                assertEquals(expectedStep, step, 1e-5f, "digit '${digits[index]}' at ${sizePx}px")
            }
        }
    }

    @Test
    fun rightAlignedCounterKeepsItsRightEdgeFixedFrom1To999() {
        val host = ComposeHost(density = 1f)
        var counter = "1"
        var numerals: FontNumerals? = FontNumerals.Tabular

        fun measureCounter(): Pair<Int, Int> {
            val out = host.frame(FrameInput(200, 60)) {
                Box(modifier = Modifier.width(120.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        counter,
                        modifier = Modifier.testTag("counter"),
                        style = TextStyle(color = white, size = 16.sp, numerals = numerals),
                    )
                }
            }
            val node = assertNotNull(out.semantics.firstNotNullOfOrNull { it.findTag("counter") })
            return node.x to (node.x + node.width)
        }

        val tabularBounds = listOf("1", "9", "42", "99", "100", "111", "999").associateWith { value ->
            counter = value
            measureCounter()
        }
        tabularBounds.forEach { (value, bounds) ->
            assertEquals(120, bounds.second, "right edge for '$value' stays pinned to 120")
        }
        assertEquals(tabularBounds.getValue("1").first, tabularBounds.getValue("9").first, "1 and 9 share width")
        assertEquals(tabularBounds.getValue("42").first, tabularBounds.getValue("99").first, "42 and 99 share width")
        assertEquals(tabularBounds.getValue("100").first, tabularBounds.getValue("111").first, "100 and 111 share width")
        assertEquals(tabularBounds.getValue("111").first, tabularBounds.getValue("999").first, "111 and 999 share width")

        // Under proportional figures, "111" is narrower than "999", so its left edge sits further right.
        numerals = FontNumerals.Proportional
        counter = "111"
        val prop111 = measureCounter()
        counter = "999"
        val prop999 = measureCounter()
        assertEquals(120, prop111.second)
        assertEquals(120, prop999.second)
        assertTrue(prop111.first > prop999.first, "proportional '111' (${prop111.first}) is narrower than '999' (${prop999.first})")
    }

    @Test
    fun inheritedNumeralsReachBothPixelGlyphAndDistanceFieldPainting() {
        for (sizeSp in listOf(14.sp, 32.sp)) {
            val tabGlyphs = paint {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(numerals = FontNumerals.Tabular)) {
                    Text("111", style = TextStyle(color = white, size = sizeSp))
                }
            }
            val propGlyphs = paint {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(numerals = FontNumerals.Proportional)) {
                    Text("111", style = TextStyle(color = white, size = sizeSp))
                }
            }
            assertEquals(3, tabGlyphs.size)
            assertEquals(3, propGlyphs.size)
            assertTrue(tabGlyphs[2].x > propGlyphs[2].x, "tabular '111' spreads wider than proportional at $sizeSp")
        }

        // Direct LayoutNode + TextMeasurePolicy callers also honour style.numerals.
        val tabNode = LayoutNode(TextMeasurePolicy("111111", TextStyle(size = 16.sp, numerals = FontNumerals.Tabular), font))
        val propNode = LayoutNode(TextMeasurePolicy("111111", TextStyle(size = 16.sp, numerals = FontNumerals.Proportional), font))
        tabNode.measure(Constraints.of(0, 4000, 0, 4000))
        propNode.measure(Constraints.of(0, 4000, 0, 4000))
        assertTrue(tabNode.width > propNode.width)
    }

    @Test
    fun substitutedFiguresDrawFromTheirOwnPageAndEverythingElseFromTheFamilyAtlas() {
        val proportional = font.withNumerals(FontNumerals.Proportional)
        // 48 px draws from the distance field, the atlas a renderer uploads per font.
        val glyphs = paint {
            Text("A1:", style = TextStyle(color = white, size = 48.sp, numerals = FontNumerals.Proportional))
        }

        assertEquals(2, proportional.atlasPages.size)
        assertSame(font, proportional.atlasPages[0])
        val figures = proportional.atlasPages[1]
        // One pass per page: the family's glyphs, then the figure page's.
        assertEquals(3, glyphs.size)
        assertSame(font, glyphs[0].font, "'A' samples the family's own atlas, already uploaded for default digits")
        assertSame(font, glyphs[1].font, "':' too")
        assertSame(figures, glyphs[2].font, "the proportional '1' samples the figure page")
        assertTrue(glyphs[2].x > glyphs[0].x && glyphs[2].x < glyphs[1].x, "and sits between them")
    }

    private fun paint(content: context(Composer) () -> Unit): List<UiDrawPrimitive.Glyph> =
        ComposeHost(density = 1f).frame(FrameInput(400, 200), content).primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
}

private fun com.awakekt.awake.compose.ui.semantics.SemanticsNode.findTag(
    tag: String,
): com.awakekt.awake.compose.ui.semantics.SemanticsNode? =
    if (testTag == tag) this else children.firstNotNullOfOrNull { it.findTag(tag) }
