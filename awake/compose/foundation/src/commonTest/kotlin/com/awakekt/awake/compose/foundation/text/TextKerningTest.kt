/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pair kerning through layout (#1022): measured, painted, wrapped, under the caret and under a click, all
 * from the same kerned pen.
 *
 * The browser widths are Chrome's for the same Roboto Regular at 14 px, kerned and with
 * `font-kerning: none`, as the issue measured them and the conformance suite captures them.
 */
class TextKerningTest {
    private val white = Color(1f, 1f, 1f, 1f)
    private val font = UiFonts.default()

    /** The default family held to its distance field, whose quads sit on the unrounded pen. */
    private val fieldFont = font.withPixelGlyphMaxSize(0)

    private fun run(text: String, kerning: Boolean? = null, glyphPx: Float = 14f, wrapWidthPx: Float = Float.POSITIVE_INFINITY) =
        TextRun(text, TextStyle(kerning = kerning), font, glyphPx, density = 1f, wrapWidthPx = wrapWidthPx)

    @Test
    fun kernedWidthsMatchTheBrowser() {
        for ((text, kerned, unkerned) in BROWSER_WIDTHS) {
            val width = run(text).offsetAt(text.length)
            assertEquals(kerned, width, 0.5f, "'$text' kerned")
            assertEquals(unkerned, run(text, kerning = false).offsetAt(text.length), 0.5f, "'$text' with kerning off")
            // The box a Text measures is the same kerned line, to the whole pixel it is sized to.
            val node = LayoutNode(TextMeasurePolicy(text, TextStyle(size = 14.sp), font))
            node.measure(Constraints.of(0, WIDE, 0, WIDE))
            assertEquals(width.roundToInt(), node.width, "'$text' measured box")
            assertEquals(width.roundToInt(), node.maxIntrinsicWidth(WIDE), "'$text' intrinsic width")
        }
    }

    @Test
    fun kerningIsOnByDefaultAndAStyleCanTurnItOffForItsSubtree() {
        assertTrue(run("AV").offsetAt(2) < run("AV", kerning = false).offsetAt(2))
        assertEquals(run("AV").offsetAt(2), run("AV", kerning = true).offsetAt(2))
        val off = TextStyle(kerning = false)
        assertEquals(false, (off then TextStyle(color = white)).kerning, "a style that says nothing about kerning inherits it")
        assertEquals(true, (off then TextStyle(kerning = true)).kerning)
        assertEquals(null, (TextStyle() then TextStyle(color = white)).kerning)
    }

    /**
     * The caret between two kerned clusters sits where the second one's glyph starts, so a click on either
     * side of that line lands between them, and a glyph is painted exactly at the caret before it.
     */
    @Test
    fun theCaretAndClicksLandBetweenKernedGlyphs() {
        val text = "AVATAR"
        val kerned = run(text)
        val plain = run(text, kerning = false)
        val advanceA = font.advanceFor('A'.code, 14f, FontWeight.Normal)
        val kernAV = font.kerningFor('A'.code, 'V'.code, 14f)
        assertTrue(kernAV < -0.5f, "Roboto kerns AV by -87/2048 em, was $kernAV px at 14 px")

        // Between A and V: A's advance moved by the pair's kerning, as V's pen is.
        assertEquals(advanceA + kernAV, kerned.caretPositionAt(1).x, 1e-4f)
        assertEquals(advanceA, plain.caretPositionAt(1).x, 1e-4f)

        // Every caret is the left edge of its glyph as painted, and every caret position clicks back to its index.
        val glyphs = paint { Text(text, style = TextStyle(font = fieldFont, color = white, size = 14.sp)) }
        assertEquals(text.length, glyphs.size)
        for (index in text.indices) {
            val rect = checkNotNull(fieldFont.glyphFor(text[index].code, FontWeight.Normal))
            assertEquals(kerned.offsetAt(index), glyphs[index].x - rect.offsetXEm * 14f, 1e-3f, "glyph $index sits at its caret")
        }
        for (index in 0..text.length) {
            val x = kerned.caretPositionAt(index).x
            assertEquals(index, kerned.indexAt(x), "the caret at $index")
            // Either side of the caret, but nearer it than to its neighbours, still resolves to it.
            assertEquals(index, kerned.indexAt(x - 0.25f), "just before the caret at $index")
            assertEquals(index, kerned.indexAt(x + 0.25f), "just after the caret at $index")
        }
        // A selection over "VA" spans exactly the kerned glyphs.
        var selected = 0f
        kerned.forEachSelectionSegment(1, 3) { x, _, width, _ ->
            assertEquals(kerned.caretPositionAt(1).x, x, 1e-4f)
            selected = width
        }
        assertEquals(kerned.caretPositionAt(3).x - kerned.caretPositionAt(1).x, selected, 1e-4f)
    }

    /**
     * Wrapping measures with the same kerned widths: "To Ty Yo" kerned is 52.77 px and unkerned 54.65, so a
     * 53 px line holds it whole only when it kerns.
     */
    @Test
    fun lineBreakingMeasuresKernedLines() {
        assertEquals(listOf("To Ty Yo"), run("To Ty Yo", wrapWidthPx = 53f).lines)
        assertEquals(listOf("To Ty", "Yo"), run("To Ty Yo", kerning = false, wrapWidthPx = 53f).lines)
    }

    /**
     * Pixel glyphs (#993): kerning moves the pen before each glyph is snapped, so every glyph still lands on a
     * whole pixel, and a pair kerned by more than a pixel lands that much closer. Roboto kerns `LT` by
     * -275/2048 em, -3.22 px at 24 px.
     */
    @Test
    fun pixelGlyphsSnapAfterKerning() {
        val kerned = paint { Text("LT", style = TextStyle(color = white, size = 24.sp)) }
        val plain = paint { Text("LT", style = TextStyle(color = white, size = 24.sp, kerning = false)) }
        assertEquals(2, kerned.size)
        (kerned + plain).forEach { assertEquals(floor(it.x), it.x, "a pixel glyph sits on a whole pixel, was ${it.x}") }
        assertEquals(plain[0].x, kerned[0].x)
        val moved = kerned[1].x - plain[1].x
        val kern = font.kerningFor('L'.code, 'T'.code, 24f)
        assertTrue(abs(moved - kern) <= 1f, "'T' moved $moved px for a $kern px kerning")
        assertNotEquals(0f, moved)
    }

    private fun paint(content: context(Composer) () -> Unit): List<UiDrawPrimitive.Glyph> =
        ComposeHost(density = 1f).frame(FrameInput(400, 200), content).primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    private companion object {
        const val WIDE = 10_000

        /** Chrome's widths at 14 px: kerned, then with `font-kerning: none`. */
        val BROWSER_WIDTHS = listOf(
            Triple("AVATAR", 50.76f, 53.29f),
            Triple("To Ty Yo", 52.77f, 54.65f),
            Triple("WAVE Tower", 79.08f, 80.92f),
        )
    }
}
