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
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A text field clips its text at its padding box, as CSS clips a field's content, not at the line box:
 * a glyph's ink reaches past its advance and its line, and a clip at the text's own box cut the ring of
 * an `Å` and the tail of a leading `j` (#1053).
 */
class TextFieldGlyphClipTest {
    @Test
    fun aFieldsClipKeepsTheInkItsPaddingHasRoomFor() {
        val failures = GlyphInk.cases().flatMap { case ->
            val texts = listOf("Åjump", "ÉÑÇÿ gjpqy|Wolf")
            texts.mapNotNull { text -> lostToTheFieldsClip(text, case).takeIf { it > 0 }?.let { "$case '$text': cut $it pixels of ink" } }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
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
        assertTrue(GlyphInk.inkedPixels(unclipped) > 0, "'$text' drew nothing")
        return GlyphInk.lostPixels(unclipped, GlyphInk.paint(primitives, width, height))
    }

    @Test
    fun aClipStopsAtTheFieldsOwnBoundsAndAtTheTextOnASideItScrollsPast() {
        val density = 1.5f
        val primitives = GlyphInk.frame(density, 400, 120) {
            Box(Modifier.padding(20.dp)) {
                BasicTextField(
                    TextFieldState("Harbor Town quarterly report draft", cursor = 0),
                    Modifier.width(120.dp).padding(16.dp),
                    style = TextStyle(color = GlyphInk.black, size = 14.sp),
                    singleLine = true,
                )
            }
        }.primitives
        val clip = primitives.filterIsInstance<UiDrawPrimitive.ClipPush>().single().rect
        // Unfocused, the field shows the start of its text: the left side is the padding box's edge,
        // and the overflowing right side stays at the content box, so no clipped-off character
        // shows in the padding.
        assertEquals(20f * density, clip.x, "left edge")
        assertEquals(20f * density, clip.y, "top edge")
        assertEquals((20f + 120f - 16f) * density, clip.x + clip.width, "right edge")
    }
}
