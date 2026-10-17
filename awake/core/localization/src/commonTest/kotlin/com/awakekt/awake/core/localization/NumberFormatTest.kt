/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.localization.NumberStyle.Compact
import com.awakekt.awake.core.localization.NumberStyle.Integer
import com.awakekt.awake.core.localization.NumberStyle.Percent
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NumberFormatTest {

    private fun fmt(tag: String) = NumberFormat.forLocale(LocaleId.of(tag))

    private val nbsp = " "
    private val narrowNbsp = " "

    @Test
    fun separatorsFollowTheLocale() {
        assertEquals("1,234.5", fmt("en").format(1234.5))
        assertEquals("1.234,5", fmt("de").format(1234.5))
        assertEquals("1${narrowNbsp}234,5", fmt("fr").format(1234.5))
        assertEquals("1${nbsp}234,5", fmt("ru").format(1234.5))
        assertEquals("1,234,567", fmt("ja").format(1_234_567))
        assertEquals("1.234.567", fmt("pt-BR").format(1_234_567), "pt-BR uses pt's data")
        assertEquals("-1,234.5", fmt("en").format(-1234.5))
        assertEquals("−5", fmt("sv").format(-5), "Swedish writes a real minus sign")
    }

    @Test
    fun someLocalesGroupOnlyFromFiveDigits() {
        assertEquals("1234", fmt("es").format(1234))
        assertEquals("12.345", fmt("es").format(12_345))
        assertEquals("1234", fmt("pl").format(1234))
        assertEquals("12${nbsp}345", fmt("pl").format(12_345))
        assertEquals("1234", fmt("pt-PT").format(1234), "pt-PT has its own data")
        assertEquals("1.234", fmt("pt").format(1234))
    }

    @Test
    fun decimalsKeepThreeDigitsAndRoundHalfToEven() {
        assertEquals("3.142", fmt("en").format(3.14159))
        assertEquals("0.062", fmt("en").format(0.0625))
        assertEquals("1.1", fmt("en").format(1.1f))
        assertEquals("2", fmt("en").format(2.0))
        assertEquals("0", fmt("en").format(-0.0001), "a number that rounds to zero has no sign")
        assertEquals("9,223,372,036,854,775,807", fmt("en").format(Long.MAX_VALUE))
        assertEquals("-9,223,372,036,854,775,808", fmt("en").format(Long.MIN_VALUE))
    }

    @Test
    fun integerRoundsHalfToEven() {
        assertEquals("2", fmt("en").format(1.5, Integer))
        assertEquals("2", fmt("en").format(2.5, Integer))
        assertEquals("-2", fmt("en").format(-2.5, Integer))
        assertEquals("1.235", fmt("de").format(1234.6, Integer))
    }

    @Test
    fun percentUsesTheLocalesPattern() {
        assertEquals("45%", fmt("en").format(0.45, Percent))
        assertEquals("45$nbsp%", fmt("de").format(0.45, Percent))
        assertEquals("45$nbsp%", fmt("fr").format(0.45, Percent))
        assertEquals("%45", fmt("tr").format(0.45, Percent))
        assertEquals("1,250%", fmt("en").format(12.5, Percent))
        assertEquals("-5%", fmt("en").format(-0.05, Percent))
    }

    @Test
    fun compactIsShortForAHud() {
        val en = fmt("en")
        assertEquals("999", en.format(999, Compact))
        assertEquals("1K", en.format(1000, Compact))
        assertEquals("1.2K", en.format(1234, Compact))
        assertEquals("12K", en.format(12_345, Compact))
        assertEquals("123K", en.format(123_456, Compact))
        assertEquals("1.2M", en.format(1_234_567, Compact))
        assertEquals("1M", en.format(999_999, Compact), "999,999 rounds up into the next size")
        assertEquals("1.5B", en.format(1_500_000_000L, Compact))
        assertEquals("1500T", en.format(1.5e15, Compact), "past the largest size, the number grows")
        assertEquals("-1.2K", en.format(-1234, Compact))
        assertEquals("5.7", en.format(5.67, Compact))
        assertEquals("0.57", en.format(0.567, Compact))
    }

    @Test
    fun compactFollowsTheLocale() {
        assertEquals("1234", fmt("de").format(1234, Compact), "German leaves thousands uncompacted")
        assertEquals("12.345", fmt("de").format(12_345, Compact))
        assertEquals("1,2${nbsp}Mio.", fmt("de").format(1_234_567, Compact))
        assertEquals("1234", fmt("ja").format(1234, Compact))
        assertEquals("1.2万", fmt("ja").format(12_345, Compact), "Japanese counts in ten thousands")
        assertEquals("12万", fmt("ja").format(123_456, Compact))
        assertEquals("1,5${nbsp}тыс.", fmt("ru").format(1500, Compact))
        assertEquals("1200${nbsp}M", fmt("es").format(1_200_000_000L, Compact))
        assertEquals("1,2${nbsp}mil", fmt("es").format(1234, Compact))
    }

    @Test
    fun theGuidesTable() {
        // website/docs/guides/localization.md, "Numbers"; the guide shows no-break spaces as spaces.
        val rows = mapOf(
            "en" to listOf("12,345.6", "45%", "1.2M"),
            "de" to listOf("12.345,6", "45$nbsp%", "1,2${nbsp}Mio."),
            "fr" to listOf("12${narrowNbsp}345,6", "45$nbsp%", "1,2${nbsp}M"),
            "es" to listOf("12.345,6", "45$nbsp%", "1,2${nbsp}M"),
            "ja" to listOf("12,345.6", "45%", "123万"),
            "tr" to listOf("12.345,6", "%45", "1,2${nbsp}Mn"),
        )
        for ((tag, expected) in rows) {
            val format = fmt(tag)
            assertEquals(expected, listOf(format.format(12_345.6), format.format(0.45, Percent), format.format(1_234_567, Compact)), tag)
        }
    }

    @Test
    fun anUncoveredLanguageUsesCldrRoot() {
        assertSame(NumberFormat.Root, fmt("xx"))
        assertEquals("1,234.5", NumberFormat.Root.format(1234.5))
        assertEquals("1.2K", NumberFormat.Root.format(1234, Compact))
        assertEquals("1.2G", NumberFormat.Root.format(1_234_000_000L, Compact))
    }

    @Test
    fun everyCoveredLanguageHasData() {
        val covered = listOf(
            "ar", "de", "en", "es", "et", "fi", "fil", "fr", "id", "it", "ja", "ko", "ms", "nl", "pl",
            "pt", "ru", "sv", "th", "tl", "tr", "uk", "vi", "yue", "zh",
        )
        for (tag in covered) {
            val format = fmt(tag)
            assertTrue(format !== NumberFormat.Root, "$tag has no number data")
            for (value in listOf(0, 7, 1234, 12_345_678, -42)) {
                for (style in NumberStyle.entries) assertTrue(format.format(value, style).isNotEmpty(), "$tag $style $value")
            }
        }
    }

    @Test
    fun theDefaultFontDrawsTheDigitsAndSignsOfEveryLocale() {
        // Scripts the UI font lacks, such as Cyrillic and CJK suffixes, come from LocaleFonts.
        val otherScripts = setOf("ar", "ja", "ko", "ru", "th", "uk", "yue", "zh")
        val samples = NumberStyle.entries.flatMap { style -> listOf(-1234567.5, 0.25, 12_345).map { style to it } }
        val missing = CldrNumberData.locales.keys.filter { it !in otherScripts }.flatMap { tag ->
            val format = NumberFormat.forLocale(LocaleId.parseOrNull(tag) ?: LocaleId.English)
            samples.flatMap { (style, value) -> undrawable(format.format(value, style)).map { "$tag:U+${it.code.toString(16)}" } }
        }
        assertTrue(missing.isEmpty(), "the default font lacks: ${missing.distinct()}")
    }

    /** The chars of [text] the default font has no glyph for; spaces and direction marks draw none. */
    private fun undrawable(text: String): List<Char> {
        val font = UiFonts.default()
        val marks = setOf(Char(0x200E), Char(0x200F))
        return text.filter { !it.isWhitespace() && it !in marks && !font.hasGlyph(it) }.toList()
    }
}
