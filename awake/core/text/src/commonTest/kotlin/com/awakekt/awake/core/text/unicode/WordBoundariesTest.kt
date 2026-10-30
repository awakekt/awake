/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordBoundariesTest {

    private fun forwardStops(text: String): List<Int> {
        val stops = mutableListOf(0)
        var cursor = 0
        while (cursor < text.length) {
            val next = WordBoundaries.nextWordBoundary(text, cursor)
            assertTrue(next > cursor, "nextWordBoundary stalled at $cursor in '$text'")
            assertTrue(GraphemeClusters.isBoundary(text, next), "non-cluster boundary $next in '$text'")
            stops += next
            cursor = next
        }
        return stops
    }

    private fun backwardStops(text: String): List<Int> {
        val stops = mutableListOf(text.length)
        var cursor = text.length
        while (cursor > 0) {
            val prev = WordBoundaries.previousWordBoundary(text, cursor)
            assertTrue(prev < cursor, "previousWordBoundary stalled at $cursor in '$text'")
            assertTrue(GraphemeClusters.isBoundary(text, prev), "non-cluster boundary $prev in '$text'")
            stops += prev
            cursor = prev
        }
        return stops
    }

    private fun wordAt(text: String, index: Int): String {
        val range = WordBoundaries.wordRangeAt(text, index)
        return text.substring(range.first, range.last + 1)
    }

    @Test
    fun stepsAcrossLatinKoreanJapaneseAndChineseBoundaries() {
        // Latin + underscore identifiers skip spaces and punctuation.
        val latin = "Harbor, quay_01!"
        assertEquals(listOf(0, 6, 15, 16), forwardStops(latin))
        assertEquals(listOf(16, 8, 0), backwardStops(latin))
        assertEquals("Harbor", wordAt(latin, 2))
        assertEquals("quay_01", wordAt(latin, 10))

        // Korean groups Hangul runs between spaces and punctuation.
        val korean = "항구 마을!"
        assertEquals(listOf(0, 2, 5, 6), forwardStops(korean))
        assertEquals(listOf(6, 3, 0), backwardStops(korean))
        assertEquals("항구", wordAt(korean, 1))
        assertEquals("마을", wordAt(korean, 4))

        // Japanese separates Kanji (per cluster), Hiragana runs, and Katakana runs (including ー).
        val japanese = "港のコーヒー店"
        // 港 (0..1) | の (1..2) | コーヒー (2..6) | 店 (6..7)
        assertEquals(listOf(0, 1, 2, 6, 7), forwardStops(japanese))
        assertEquals(listOf(7, 6, 2, 1, 0), backwardStops(japanese))
        assertEquals("港", wordAt(japanese, 0))
        assertEquals("の", wordAt(japanese, 1))
        assertEquals("コーヒー", wordAt(japanese, 3))
        assertEquals("店", wordAt(japanese, 6))
    }

    @Test
    fun separatesMixedLatinAndCjkWithoutSpaces() {
        val mixed = "Harbor港口Town 42"
        // Harbor (0..6) | 港 (6..7) | 口 (7..8) | Town (8..12) | " 42" -> 15
        assertEquals(listOf(0, 6, 7, 8, 12, 15), forwardStops(mixed))
        assertEquals(listOf(15, 13, 8, 7, 6, 0), backwardStops(mixed))
        assertEquals("Harbor", wordAt(mixed, 0))
        assertEquals("港", wordAt(mixed, 6))
        assertEquals("口", wordAt(mixed, 7))
        assertEquals("Town", wordAt(mixed, 9))
        assertEquals("42", wordAt(mixed, 15))
    }

    @Test
    fun respectsNewlinesAndMultiCodePointGraphemeClusters() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val combiningWord = "cafe\u0301"
        val multiline = "$combiningWord $family\r\n港"
        val stops = forwardStops(multiline)
        assertEquals(
            listOf(
                0,
                combiningWord.length,
                combiningWord.length + 1 + family.length,
                combiningWord.length + 1 + family.length + 2,
                multiline.length,
            ),
            stops,
        )
        // Double-clicking inside a ZWJ emoji cluster selects the whole cluster and nothing around it.
        val insideFamily = combiningWord.length + 3
        assertEquals(family, wordAt(multiline, insideFamily))
        assertEquals(combiningWord, wordAt(multiline, combiningWord.length - 1))

        // Empty text returns an empty range at 0 without throwing.
        assertEquals(IntRange.EMPTY, WordBoundaries.wordRangeAt("", 0))
        assertEquals(0, WordBoundaries.previousWordBoundary("", 0))
        assertEquals(0, WordBoundaries.nextWordBoundary("", 0))
    }
}
