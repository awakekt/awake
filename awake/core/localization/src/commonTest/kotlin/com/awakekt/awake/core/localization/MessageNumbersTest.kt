/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `selectordinal`, `{n, number, …}` and `#` written the locale's way. */
class MessageNumbersTest {

    private val en = LocaleId.English
    private val de = LocaleId.of("de")
    private val nbsp = " "

    private fun format(pattern: String, locale: LocaleId, vararg arguments: Pair<String, Any?>): String =
        MessageFormat.parse(pattern).format(arguments.toMap(), locale)

    @Test
    fun englishOrdinals() {
        val rank = "{rank, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}"
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 101 to "101st", 111 to "111th", 1001 to "1,001st",
        )
        for ((n, text) in expected) assertEquals(text, format(rank, en, "rank" to n))
    }

    @Test
    fun ordinalsFollowTheLanguagesOwnRules() {
        val place = "{n, selectordinal, one {#re} other {#e}}"
        assertEquals("1re", format(place, LocaleId.of("fr"), "n" to 1))
        assertEquals("2e", format(place, LocaleId.of("fr"), "n" to 2))
        val italian = "{n, selectordinal, many {l''#°} other {il #°}}"
        assertEquals("l'8°", format(italian, LocaleId.of("it"), "n" to 8))
        assertEquals("il 7°", format(italian, LocaleId.of("it"), "n" to 7))
        assertEquals("ordinal", format("{n, selectordinal, =3 {ordinal} other {#.}}", de, "n" to 3), "an exact match wins")
        assertEquals("1.", format("{n, selectordinal, other {#.}}", de, "n" to 1))
    }

    @Test
    fun selectOrdinalNeedsAnOtherBranch() {
        val error = assertFailsWith<MessageSyntaxException> { MessageFormat.parse("{n, selectordinal, one {#st}}") }
        assertTrue("selectordinal" in error.message.orEmpty())
    }

    @Test
    fun numberArgumentsUseTheLocale() {
        assertEquals("1,234.5 / 1234.5", format("{n, number} / {n}", en, "n" to 1234.5), "a plain argument is not grouped")
        assertEquals("1.234,5", format("{n, number}", de, "n" to 1234.5))
        assertEquals("1.234", format("{n, number, integer}", de, "n" to 1234.5), "half to even")
        assertEquals("45$nbsp%", format("{p, number, percent}", de, "p" to 0.45))
        assertEquals("1,2${nbsp}Mio.", format("{gold, number, compact}", de, "gold" to 1_234_567))
        assertEquals("12K", format("{gold, number, ::compact-short}", en, "gold" to 12_345))
        assertEquals("1,234", format("{n, number, #,##0.00}", en, "n" to 1234), "an ICU pattern we don't read writes as number does")
        assertEquals("2,500", format("{n, number}", en, "n" to "2500"), "a numeric string is a number")
    }

    @Test
    fun aNumberArgumentThatIsNotANumberShowsAsItIs() {
        val problems = mutableListOf<String>()
        val text = MessageFormat.parse("{n, number} and {m, number}").format(mapOf("n" to "lots"), en) { problems += it }
        assertEquals("lots and {m}", text)
        assertEquals(2, problems.size)
    }

    @Test
    fun poundInAPluralUsesTheLocale() {
        val points = "{n, plural, one {# Punkt} other {# Punkte}}"
        assertEquals("1.234 Punkte", format(points, de, "n" to 1234))
        assertEquals("1 Punkt", format(points, de, "n" to 1))
        assertEquals("2,5 Punkte", format(points, de, "n" to 2.5))
        assertEquals("1,000 items", format("{n, plural, one {# item} other {# items}}", en, "n" to 1000))
    }

    @Test
    fun theOldOverloadUsesCldrRoot() {
        val message = MessageFormat.parse("{n, plural, other {# coins}} {r, selectordinal, other {#th}}")
        assertEquals("1,000 coins 2th", message.format(mapOf("n" to 1000, "r" to 2), PluralRules.Root))
    }

    @Test
    fun stringsFormatWithTheTablesLocale() = runTest {
        val tables = mapOf(
            en to mapOf(
                "rank" to "You placed {rank, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}",
                "gold" to "{gold, number, compact} gold",
            ),
            de to mapOf("rank" to "Du bist {rank, selectordinal, other {#.}}"),
        )
        val strings = Strings.load(StringTableSource.of(tables), locale = en)
        assertEquals("You placed 22nd", strings.text("rank", "rank" to 22))
        assertEquals("1.2M gold", strings.text("gold", "gold" to 1_234_567))
        strings.setLocale(de)
        assertEquals("Du bist 1.002.", strings.text("rank", "rank" to 1002))
        assertEquals("1.2M gold", strings.text("gold", "gold" to 1_234_567), "English text keeps English numbers")
    }

    @Test
    fun aStringsCanBeGivenOrdinalRulesForALanguageNotCovered() = runTest {
        val xx = LocaleId.of("xx")
        val strings = Strings.load(
            StringTableSource.of(mapOf(xx to mapOf("rank" to "{n, selectordinal, one {#!} other {#}}"))),
            locale = xx,
            defaultLocale = xx,
            ordinalRules = { PluralRule { if (it.i == 1L) PluralCategory.One else PluralCategory.Other } },
        )
        assertEquals("1!", strings.text("rank", "n" to 1))
        assertEquals("2", strings.text("rank", "n" to 2))
    }
}
