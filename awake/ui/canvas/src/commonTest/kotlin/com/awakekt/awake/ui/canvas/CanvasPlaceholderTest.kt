/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val Filipino = LocaleId.of("fil")

private val TABLES = StringTableSource.of(
    mapOf(
        LocaleId.English to mapOf("chat.hint" to "Say something", "name.hint" to "Name for {slot}"),
        Filipino to mapOf("chat.hint" to "Magsabi ng kahit ano"),
    ),
)

/** A TextField's `placeholder`: a text key shown, muted, only while the field is empty and unfocused. */
class CanvasPlaceholderTest {
    private val host = ComposeHost()
    private lateinit var strings: Strings
    private val field = CanvasElement().apply {
        kind = CanvasElementKind.TextField
        offsetX = 0f
        offsetY = 0f
        width = 300f
        height = 40f
        fontSize = 14f
        color = "#FFC800"
        placeholder = "chat.hint"
    }
    private val items = listOf(CanvasItem(0, field))

    private fun frame(typedText: String = ""): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, typedText = typedText)) { CanvasOverlay(items, strings = strings) }

    private fun FrameOutput.glyphs() = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    /** The glyphs [text] draws as a Text element would, for comparing the field's against. */
    private fun literal(text: String): List<UiDrawPrimitive.Glyph> {
        val plain = CanvasElement().apply {
            offsetX = 0f; offsetY = 0f; width = 300f; height = 40f; fontSize = 14f; this.text = text
        }
        return ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(listOf(CanvasItem(0, plain))) }.glyphs()
    }

    @Test
    fun anEmptyUnfocusedFieldShowsItsPlaceholderInMutedColour() = withStrings {
        val glyphs = frame().glyphs()

        assertEquals("Say something".count { it != ' ' }, glyphs.size)
        val muted = glyphs.map { it.color }.distinct().single()
        assertEquals(1f, muted.r, 0.01f, "the field's own colour, only fainter")
        assertEquals(0.5f, muted.a, 0.01f)
    }

    @Test
    fun theKeysTextIsShownInTheActiveLocale() = withStrings {
        val english = frame().glyphs()
        strings.setLocale(Filipino)
        val filipino = frame().glyphs()

        assertEquals("Say something".count { it != ' ' }, english.size)
        assertEquals("Magsabi ng kahit ano".count { it != ' ' }, filipino.size)
    }

    @Test
    fun aPlaceholderTakesTheFieldsTextArguments() = withStrings {
        field.placeholder = "name.hint"
        field.textArgs = mapOf("slot" to "7")

        assertEquals("Name for 7".count { it != ' ' }, frame().glyphs().size)
    }

    @Test
    fun theTypedTextTakesThePlaceholdersPlaceInFullColour() = withStrings {
        frame()
        field.focused = true
        frame()
        frame(typedText = "hi")
        val glyphs = frame().glyphs()

        assertEquals(2, glyphs.size, "only the typed text, no placeholder")
        assertEquals(1f, glyphs.map { it.color.a }.distinct().single(), 0.001f)
    }

    @Test
    fun aTypedFieldThatLosesFocusShowsItsTextNotThePlaceholder() = withStrings {
        field.text = "hi"
        frame()
        field.focused = true
        frame()
        field.focused = false

        assertEquals(2, frame().glyphs().size)
        assertEquals(2, frame().glyphs().size)
    }

    @Test
    fun aFocusedEmptyFieldShowsNoPlaceholder() = withStrings {
        frame()
        field.focused = true
        frame()

        assertEquals(emptyList(), frame().glyphs())
    }

    @Test
    fun theFieldsPlaceholderReturnsWhenItIsEmptiedAndLetGo() = withStrings {
        frame()
        field.focused = true
        frame()
        frame(typedText = "hi")
        frame()
        field.text = ""
        frame()
        field.focused = false
        frame()

        assertEquals("Say something".count { it != ' ' }, frame().glyphs().size)
    }

    @Test
    fun aFieldWithNoPlaceholderDrawsNothingWhileEmpty() = withStrings {
        field.placeholder = ""

        assertEquals(emptyList(), frame().glyphs())
    }

    @Test
    fun theTextIsPaddedAndThePlaceholderSitsAtTheSamePlace() = withStrings {
        field.padding = CanvasPadding(horizontal = 15f, vertical = 0f)
        val hint = frame().glyphs().first()
        field.placeholder = ""
        field.text = "Say something"
        val typed = frame().glyphs().first()

        assertEquals(typed.x, hint.x, 0.01f, "the placeholder starts where typed text would")
        assertTrue(hint.x >= 15f)
        assertEquals(literal("Say something").size, frame().glyphs().size)
    }

    /** Runs [body] with the strings loaded, which suspends. */
    private fun withStrings(body: suspend () -> Unit) = runTest {
        strings = Strings.load(TABLES, locale = LocaleId.English)
        body()
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 60
    }
}
