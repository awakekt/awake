/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasPadding
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A canvas TextField clips its text at its padding box, as [TextFieldGlyphClipTest] holds a
 * `BasicTextField` to: a glyph's ink reaches past its advance and its line, and a field with no
 * padding cut the tail of a leading `j` and the ring of an `Å` at its edge (#1035, #1053).
 */
class CanvasTextFieldGlyphClipTest {
    private val loadedRoboto: UiFont get() = GlyphInk.loadedRoboto

    @Test
    fun aFieldsDefaultPaddingKeepsTheInkItsEdgeHasRoomFor() {
        val failures = cases().mapNotNull { case ->
            val lost = lostToTheFieldsClip(case, CanvasPadding())
            "$case: cut $lost pixels of ink".takeIf { lost > 0 }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aFieldWithNoPaddingStillCutsAtItsEdge() {
        // The control: the same fields, padded by nothing, lose ink, so the test above can fail.
        val cut = cases().filter { it.text == "jump" && it.fontName == "loaded TTF" && it.size == 48 }
        assertTrue(cut.isNotEmpty())
        for (case in cut) {
            val lost = lostToTheFieldsClip(case, CanvasPadding.None)
            assertTrue(lost > 0, "$case: a field with no padding cut nothing, so the padding case proves nothing")
        }
    }

    /** One text in one field: [font] at [size] px on a canvas drawn at [scale], in a field [tight] or roomy. */
    private data class Case(val fontName: String, val font: UiFont, val text: String, val size: Int, val scale: Float, val tight: Boolean) {
        override fun toString(): String = "$fontName '$text' ${size}px scale$scale ${if (tight) "tight" else "roomy"} field"
    }

    private fun cases(): List<Case> {
        val fonts = listOf(
            Triple("built-in", UiFonts.default(), listOf("jump")),
            Triple("loaded TTF", loadedRoboto, listOf("jump", "Åjump")),
        )
        return fonts.flatMap { (name, font, texts) ->
            texts.flatMap { text ->
                listOf(14, 48).flatMap { size ->
                    listOf(1f, 1.5f).flatMap { scale -> listOf(true, false).map { Case(name, font, text, size, scale, it) } }
                }
            }
        }
    }

    /** Pixels of ink the field's clip takes from [case]'s text, against the same frame unclipped. */
    private fun lostToTheFieldsClip(case: Case, padding: CanvasPadding): Int {
        val margin = 16f
        val field = CanvasElement().apply {
            kind = CanvasElementKind.TextField
            offsetX = margin
            offsetY = margin
            // Wide enough that the text never scrolls, so no side keeps a tight clip.
            width = case.size * case.text.length + 2 * margin + 48
            // A tight field is barely taller than its line; a roomy one is twice the type.
            height = if (case.tight) case.size * TIGHT_LINES else case.size * 2f
            text = case.text
            fontSize = case.size.toFloat()
            color = "#000000"
            font = FONT_FILE
            this.padding = padding
        }
        val width = ceil((field.width + 2 * margin) * case.scale).toInt()
        val height = ceil((field.height + 2 * margin) * case.scale).toInt()
        val primitives = ComposeHost().frame(FrameInput(width, height)) {
            CanvasOverlay(
                items = listOf(CanvasItem(0, field)),
                scale = case.scale,
                fonts = mapOf(FONT_FILE to case.font),
            )
        }.primitives
        val unclipped = GlyphInk.paint(GlyphInk.withoutClips(primitives), width, height)
        assertTrue(GlyphInk.inkedPixels(unclipped) > 0, "$case drew nothing")
        return GlyphInk.lostPixels(unclipped, GlyphInk.paint(primitives, width, height))
    }

    private companion object {
        const val FONT_FILE = "fonts/field.ttf"

        /** The shortest field a line fits in with room: the font's line is about 1.17 em. */
        const val TIGHT_LINES = 1.3f
    }
}
