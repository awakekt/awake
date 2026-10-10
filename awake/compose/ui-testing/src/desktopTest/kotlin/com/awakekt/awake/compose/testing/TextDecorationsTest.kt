/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextDecorationsTest {
    private fun glyphs(style: TextStyle): List<UiDrawPrimitive.Glyph> =
        composeFrame(200, 60) { Text("Hi", style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    @Test
    fun aShadowIsTheTextAgainUnderItMovedByItsOffset() {
        val plain = glyphs(TextStyle(color = Color.White))
        val shadowed = glyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 2f, 3f)))

        assertEquals(plain.size * 2, shadowed.size)
        val (under, over) = shadowed.chunked(plain.size)
        assertEquals(plain, over, "the text itself is drawn last, unchanged")
        assertTrue(under.all { it.color == Color.Black })
        assertEquals(plain.map { it.x + 2f to it.y + 3f }, under.map { it.x to it.y })
    }

    @Test
    fun aTextFieldDrawsItsOutlineAndShadowToo() {
        val state = TextFieldState("Hi")
        fun fieldGlyphs(style: TextStyle) =
            composeFrame(200, 60) { BasicTextField(state, style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

        val plain = fieldGlyphs(TextStyle(color = Color.White))
        val decorated = fieldGlyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black), outline = TextOutline(Color.Black)))

        assertEquals(plain.size * 10, decorated.size, "a shadow, eight outline copies, then the text")
    }

    @Test
    fun anOutlineSurroundsEveryGlyphUnderTheText() {
        val plain = glyphs(TextStyle(color = Color.White))
        val outlined = glyphs(TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f)))

        assertEquals(plain.size * 9, outlined.size, "eight copies around, then the text")
        assertEquals(plain, outlined.takeLast(plain.size))
        val first = plain.first()
        val ring = outlined.dropLast(plain.size).filterIndexed { i, _ -> i % plain.size == 0 }
        assertTrue(ring.all { it.color == Color.Black })
        assertEquals(2f, ring.maxOf { it.x } - first.x, 1e-4f, "two dp to the right")
        assertEquals(2f, first.x - ring.minOf { it.x }, 1e-4f, "and two to the left")
        assertEquals(2f, ring.maxOf { it.y } - first.y, 1e-4f, "below")
        assertEquals(2f, first.y - ring.minOf { it.y }, 1e-4f, "and above")
    }
}
