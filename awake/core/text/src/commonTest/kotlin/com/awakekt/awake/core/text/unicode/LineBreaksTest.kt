/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineBreaksTest {

    private fun segments(text: String, wordBreak: WordBreak = WordBreak.Normal): List<String> {
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            val next = LineBreaks.nextOpportunity(text, cursor, text.length, wordBreak)
            assertTrue(next > cursor, "nextOpportunity made no progress at $cursor in '$text'")
            result += text.substring(cursor, next)
            cursor = next
        }
        assertConsistentWithCanBreakAt(text, wordBreak)
        return result
    }

    private fun assertConsistentWithCanBreakAt(text: String, wordBreak: WordBreak = WordBreak.Normal) {
        if ('\n' in text || '\r' in text) return
        for (from in 0..text.length) {
            val startCluster = GraphemeClusters.floorBoundary(text, from)
            val effectiveFrom = if (startCluster < from) GraphemeClusters.nextBoundary(text, startCluster) else from
            val expected = ((effectiveFrom + 1) until text.length)
                .firstOrNull { LineBreaks.canBreakAt(text, it, 0, text.length, wordBreak) }
                ?: text.length
            assertEquals(
                expected.coerceAtMost(text.length),
                LineBreaks.nextOpportunity(text, from, text.length, wordBreak),
                "nextOpportunity mismatch at from=$from in '$text' ($wordBreak)",
            )
        }
    }

    @Test
    fun breaksBetweenIdeographsKanaAndHangulSyllables() {
        assertEquals(listOf("港", "口", "小", "镇"), segments("港口小镇"))
        // CJK Extension A and Extension B (astral plane surrogate pairs) break per ideograph.
        val extA = "\u3400"
        val extB = "\uD840\uDC00"
        assertEquals(listOf(extA, extB, "港"), segments("$extA${extB}港"))
        assertEquals(listOf("ラ", "ン", "タ", "ン", "と", "あ", "か", "り"), segments("ランタンとあかり"))
        assertEquals(listOf("항", "구 ", "마", "을"), segments("항구 마을"))
    }

    @Test
    fun keepAllPreservesKoreanAndCjkRunsBetweenSpaces() {
        assertEquals(
            listOf("항구 ", "마을의 ", "등불이 ", "밝게 ", "빛납니다."),
            segments("항구 마을의 등불이 밝게 빛납니다.", WordBreak.KeepAll),
        )
        // Punctuation and brackets still allow breaks before opening brackets and after closing marks.
        assertEquals(
            listOf("항구", "(등불)", "마을"),
            segments("항구(등불)마을", WordBreak.KeepAll),
        )
        assertEquals(
            listOf("항구，", "마을。"),
            segments("항구，마을。", WordBreak.KeepAll),
        )
        // Normal mode still breaks between every Hangul syllable.
        assertEquals(
            listOf("항", "구，", "마", "을。"),
            segments("항구，마을。", WordBreak.Normal),
        )
    }

    @Test
    fun kinsokuNeverStartsALineWithClosingPunctuationOrProlongedSoundMark() {
        val closers = listOf(
            "。", "，", "、", "」", "）", "！", "？", "ー", "・", "：", "；", "々", "〜",
            "｡", "､", "｣", ")", "!", "?", "ｰ", "･", ",", ".", ":", ";",
        )
        for (mark in closers) {
            val sample = "港${mark}文"
            assertFalse(LineBreaks.canBreakAt(sample, 1), "must not break before '$mark'")
            assertEquals(listOf("港$mark", "文"), segments(sample), "wrong segments for '$mark'")
        }
        // Consecutive closing marks stay glued to the character before them.
        assertEquals(listOf("港」）！？。", "文"), segments("港」）！？。文"))
        assertEquals(listOf("コー", "ヒー", "店"), segments("コーヒー店"))
        assertEquals(listOf("ｶｰ", "ﾄﾞ"), segments("ｶｰﾄﾞ"))
    }

    @Test
    fun kinsokuNeverBreaksAfterOpeningPunctuationEvenAcrossSpaces() {
        val openers = listOf("「", "『", "（", "【", "《", "｢", "(", "[", "{")
        for (open in openers) {
            val sample = "港${open}文"
            assertTrue(LineBreaks.canBreakAt(sample, 1), "may break before '$open'")
            assertFalse(LineBreaks.canBreakAt(sample, 1 + open.length), "must not break after '$open'")
            assertEquals(listOf("港", "${open}文"), segments(sample))

            val spaced = "港$open   文"
            assertFalse(LineBreaks.canBreakAt(spaced, spaced.length - 1), "LB14: OP SP* × must hold for '$open'")
            assertEquals(listOf("港", "$open   文"), segments(spaced))
        }
    }

    @Test
    fun mixedLatinNumbersUrlsAndHyphensFollowUax14Pairs() {
        assertEquals(
            listOf("Harbor", "港", "口", "Town ", "42", "号"),
            segments("Harbor港口Town 42号"),
        )
        // Narrow parens glue to adjacent ASCII identifiers (`fn(x)`), but break against CJK (`港(x)港`).
        assertEquals(listOf("render(frame) ", "ok"), segments("render(frame) ok"))
        assertEquals(listOf("港", "(frame)", "港"), segments("港(frame)港"))
        // Decimal numbers, filenames and URLs stay unsplit until emergency cluster fallback.
        assertEquals(listOf("v1.28.0 ", "config.json"), segments("v1.28.0 config.json"))
        assertEquals(
            listOf("https://awake.example/docs/scene/harbor_quay_v2.html"),
            segments("https://awake.example/docs/scene/harbor_quay_v2.html"),
        )
        // Minus before a standalone number stays glued; hyphens inside words/ranges allow a break after '-'.
        assertEquals(listOf("-5 ", "and ", "a-", "1 ", "and ", "self-", "hosted"), segments("-5 and a-1 and self-hosted"))
        assertEquals(
            listOf("a\n", "-5"),
            run {
                assertFalse(LineBreaks.canBreakAt("a\n-5", 3), "after a newline, -5 must stay together")
                listOf("a\n", "-5")
            },
        )
    }

    @Test
    fun glueEllipsisEmDashesAndQuotesStayIntact() {
        // NBSP (U+00A0) and Word Joiner (U+2060) suppress breaks on both sides.
        assertEquals(listOf("100\u00A0km ", "港\u2060口"), segments("100\u00A0km 港\u2060口"))
        // Paired em-dashes (LB17: B2 × B2) stay paired while breaking before and after; paired ellipses (LB22) glue before.
        assertEquals(listOf("港", "——", "口"), segments("港——口"))
        assertEquals(listOf("港……", "口"), segments("港……口"))
        // Quotation marks glue inward and outward (LB19).
        assertEquals(listOf("“Harbor” ", "“港", "口”"), segments("“Harbor” “港口”"))
    }

    @Test
    fun neverBreaksInsideMultiCodePointGraphemeClusters() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val skinTone = "\uD83D\uDC4D\uD83C\uDFFD"
        val flag = "\uD83C\uDDEF\uD83C\uDDF5"
        val combining = "e\u0301"
        val voicedHalfwidthKana = "\uFF83\uFF9E" // ﾃﾞ: halfwidth te + voiced sound mark (Extend)
        val conjoiningHangul = "\u1100\u1161\u11A8"
        val sample = "港$family$skinTone$flag${combining}文$voicedHalfwidthKana$conjoiningHangul"

        for (i in 0..sample.length) {
            if (!GraphemeClusters.isBoundary(sample, i)) {
                assertFalse(LineBreaks.canBreakAt(sample, i), "broke inside grapheme cluster at $i")
            }
        }
        assertEquals(
            listOf("港", family, skinTone, flag, combining, "文", voicedHalfwidthKana, conjoiningHangul),
            segments(sample),
        )
    }
}
