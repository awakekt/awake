/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Editing steps by grapheme cluster: no caret move, deletion, selection or length limit ever leaves
 * part of a flag, an emoji sequence or a letter with its marks behind.
 */
class GraphemeTextFieldTest {

    private val clusters = listOf("a", FAMILY, THUMBS_UP_MEDIUM, FLAG_PH, KEYCAP_ONE, "e\u0301", "\r\n", "b")
    private val text = clusters.joinToString("")

    private fun boundaries(): List<Int> = clusters.runningFold(0) { at, cluster -> at + cluster.length }

    @Test
    fun arrowKeysStepOverWholeClusters() {
        val state = TextFieldState(text, cursor = 0)
        val forward = mutableListOf(state.cursor)
        repeat(clusters.size) {
            state.apply(TextEditAction.ArrowRight)
            forward += state.cursor
        }
        assertEquals(boundaries(), forward)
        val backward = mutableListOf(state.cursor)
        repeat(clusters.size) {
            state.apply(TextEditAction.ArrowLeft)
            backward += state.cursor
        }
        assertEquals(boundaries().reversed(), backward)
    }

    @Test
    fun backspaceRemovesOneWholeClusterAtATime() {
        val state = TextFieldState(text)
        for (count in clusters.size downTo 1) {
            assertEquals(clusters.take(count).joinToString(""), state.text)
            state.apply(TextEditAction.Backspace)
        }
        assertEquals("", state.text)
    }

    @Test
    fun deleteRemovesOneWholeClusterAtATime() {
        val state = TextFieldState(text, cursor = 0)
        for (count in clusters.size downTo 1) {
            assertEquals(clusters.takeLast(count).joinToString(""), state.text)
            state.apply(TextEditAction.Delete)
            assertEquals(0, state.cursor)
        }
        assertEquals("", state.text)
    }

    @Test
    fun aCaretOrSelectionSetInsideAClusterMovesToItsEdges() {
        val familyStart = 1
        val familyEnd = 1 + FAMILY.length
        val state = TextFieldState(text, cursor = familyStart + 3)
        assertEquals(familyStart, state.cursor)
        state.moveCursorTo(familyStart + 5)
        assertEquals(familyStart, state.cursor)
        state.setText(text, familyStart + 2)
        assertEquals(familyStart, state.cursor)
        // A selection is widened to whole clusters, whichever way it runs.
        state.select(familyStart + 2, familyEnd - 1)
        assertEquals(familyStart to familyEnd, state.selectionStart to state.selectionEnd)
        assertEquals(FAMILY, state.selectedText)
        state.select(familyEnd - 1, familyStart + 2)
        assertEquals(familyStart to familyEnd, state.selectionStart to state.selectionEnd)
        assertEquals(familyStart, state.cursor)
    }

    @Test
    fun maxLengthCountsGraphemesByDefault() {
        val state = TextFieldState()
        state.maxLength = 3
        state.insert("a")
        state.insert(FAMILY)
        state.insert(FLAG_PH + "zz")
        assertEquals("a$FAMILY$FLAG_PH", state.text)
        assertEquals(state.text.length, state.cursor)
        state.insert("x")
        assertEquals("a$FAMILY$FLAG_PH", state.text)
    }

    @Test
    fun aCharacterThatJoinsTheClusterBeforeItFitsAtTheLimit() {
        val state = TextFieldState()
        state.maxLength = 1
        state.insert("\uD83D\uDC4D")
        // A skin tone joins the thumbs-up rather than adding a character.
        state.insert("\uD83C\uDFFD")
        assertEquals(THUMBS_UP_MEDIUM, state.text)
    }

    @Test
    fun maxLengthCanCountCodePointsOrUtf8Bytes() {
        val codePoints = TextFieldState()
        codePoints.maxLength = 6
        codePoints.maxLengthUnit = TextLengthUnit.CodePoints
        codePoints.insert("a$FAMILY$FLAG_PH")
        // a (1) and the family (5) fill it; the flag (2) would not fit whole.
        assertEquals("a$FAMILY", codePoints.text)

        val bytes = TextFieldState()
        bytes.maxLength = 7
        bytes.maxLengthUnit = TextLengthUnit.Utf8Bytes
        bytes.insert("ab$FLAG_PH")
        // The flag is 8 bytes: it goes in whole or not at all.
        assertEquals("ab", bytes.text)
        bytes.insert("\u00E9\u00E9\u00E9")
        assertEquals("ab\u00E9\u00E9", bytes.text)
    }

    @Test
    fun typingOverASelectionAtTheLimitStillReplacesIt() {
        val state = TextFieldState("abc")
        state.maxLength = 3
        state.select(1, 2)
        state.insert(FAMILY + FAMILY)
        assertEquals("a${FAMILY}c", state.text)
    }

    @Test
    fun anImeCommitIsLimitedToo() {
        val state = TextFieldState("ab")
        state.maxLength = 3
        state.setComposition(ImeComposition("\u4F60\u597D", 2, 2))
        assertEquals("ab\u4F60\u597D", state.displayedText)
        state.commitComposition()
        assertEquals("ab\u4F60", state.text)
    }

    @Test
    fun setTextIsNotLimitedAndANegativeLimitIsRefused() {
        val state = TextFieldState()
        state.maxLength = 1
        state.setText("abc")
        assertEquals("abc", state.text)
        assertFailsWith<IllegalArgumentException> { state.maxLength = -1 }
    }

    @Test
    fun theRunPlacesCaretsAndClicksOnClusterBoundariesOnly() {
        val line = text.replace("\r\n", "  ")
        val run = TextRun(line, TextStyle.Default, UiFonts.trueSans(), glyphPx = 16f, density = 1f)
        val end = run.offsetAt(line.length)
        var x = -2f
        while (x < end + 4f) {
            val index = run.indexAt(x)
            assertTrue(GraphemeClusters.isBoundary(line, index), "a click at $x landed inside a cluster at $index")
            x += 0.5f
        }
        // Each emoji is one stand-in wide, as wide as '?'.
        val afterA = run.offsetAt(1)
        val familyWidth = run.offsetAt(1 + FAMILY.length) - afterA
        val standIn = TextRun("?", TextStyle.Default, UiFonts.trueSans(), glyphPx = 16f, density = 1f).offsetAt(1)
        assertEquals(standIn, familyWidth, 1e-4f)
    }

    @Test
    fun aMaskedFieldDrawsOneMaskPerCluster() {
        val secret = "a$FAMILY$FLAG_PH"
        val masked = maskedText(secret, '*')
        assertEquals(secret.length, masked.length)
        assertEquals(3, GraphemeClusters.count(masked))
        assertEquals(3, masked.count { it == '*' })
        // The same boundaries as the real text, so the caret needs no mapping.
        for (index in 0..secret.length) {
            assertEquals(GraphemeClusters.isBoundary(secret, index), GraphemeClusters.isBoundary(masked, index), "boundary at $index")
        }
        // A pictograph mask stays one cluster per character too.
        assertEquals(3, GraphemeClusters.count(maskedText(secret, '\u2764')))
        // The revealed cluster is shown whole.
        assertEquals(secret.length, maskedText(secret, '*', secret.length - 1, secret.length).length)
        assertTrue(maskedText(secret, '*', secret.length - 1, secret.length).endsWith(FLAG_PH))
    }

    private companion object {
        const val FAMILY = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        const val THUMBS_UP_MEDIUM = "\uD83D\uDC4D\uD83C\uDFFD"
        const val FLAG_PH = "\uD83C\uDDF5\uD83C\uDDED"
        const val KEYCAP_ONE = "1\uFE0F\u20E3"
    }
}
