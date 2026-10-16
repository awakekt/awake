/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageFormatTest {

    private val english = PluralRules.forLocale(LocaleId.English)

    private fun format(pattern: String, vararg arguments: Pair<String, Any?>, plurals: PluralRule = english): String =
        MessageFormat.parse(pattern).format(arguments.toMap(), plurals)

    @Test
    fun namedPlaceholdersAreFilledIn() {
        assertEquals("Hello, Mira! You have 3 coins.", format("Hello, {name}! You have {coins, number} coins.", "name" to "Mira", "coins" to 3))
        assertEquals("2.5 km", format("{distance} km", "distance" to 2.5))
        assertEquals("4 km", format("{distance} km", "distance" to 4.0))
    }

    @Test
    fun pluralPicksTheBranchForTheLanguage() {
        val pattern = "{count, plural, =0 {No items} one {# item} other {# items}}"
        assertEquals("No items", format(pattern, "count" to 0))
        assertEquals("1 item", format(pattern, "count" to 1))
        assertEquals("7 items", format(pattern, "count" to 7))
    }

    @Test
    fun pluralUsesTheMessageLanguagesCategories() {
        val pattern = "{n, plural, one {# предмет} few {# предмета} many {# предметов} other {# предмета}}"
        val russian = PluralRules.forLocale(LocaleId.of("ru"))
        assertEquals("21 предмет", format(pattern, "n" to 21, plurals = russian))
        assertEquals("3 предмета", format(pattern, "n" to 3, plurals = russian))
        assertEquals("11 предметов", format(pattern, "n" to 11, plurals = russian))
    }

    @Test
    fun offsetIsSubtractedBeforeTheCategoryAndThePound() {
        val pattern = "{guests, plural, offset:1 =0 {Nobody} =1 {{host}} one {{host} and # other} other {{host} and # others}}"
        assertEquals("Nobody", format(pattern, "guests" to 0, "host" to "Ana"))
        assertEquals("Ana", format(pattern, "guests" to 1, "host" to "Ana"))
        assertEquals("Ana and 1 other", format(pattern, "guests" to 2, "host" to "Ana"))
        assertEquals("Ana and 4 others", format(pattern, "guests" to 5, "host" to "Ana"))
    }

    @Test
    fun selectPicksByText() {
        val pattern = "{gender, select, female {She} male {He} other {They}} joined."
        assertEquals("She joined.", format(pattern, "gender" to "female"))
        assertEquals("They joined.", format(pattern, "gender" to "unknown"))
    }

    @Test
    fun branchesNest() {
        val pattern = "{who, select, self {{n, plural, one {You found # key} other {You found # keys}}} other {{who} found {n}}}"
        assertEquals("You found 2 keys", format(pattern, "who" to "self", "n" to 2))
        assertEquals("Ren found 2", format(pattern, "who" to "Ren", "n" to 2))
    }

    @Test
    fun apostrophesQuoteSyntaxAndDoubleApostrophesAreOne() {
        assertEquals("Type {name} to rename", format("Type '{name}' to rename", "name" to "x"))
        assertEquals("It's Mira's turn", format("It''s {name}''s turn", "name" to "Mira"))
        assertEquals("l'arbre", format("l'arbre"))
        assertEquals("Braces: {}", format("Braces: '{}'"))
        assertEquals("# 3", format("{n, plural, other {'#' #}}", "n" to 3))
        assertEquals("It's {quoted} here", format("It's '{quoted}' here"))
    }

    @Test
    fun anArgumentValueIsNeverParsed() {
        assertEquals("Hi {evil, select, other {x}} '", format("Hi {name}", "name" to "{evil, select, other {x}} '"))
    }

    @Test
    fun aMissingArgumentShowsItsPlaceholderAndIsReported() {
        val problems = mutableListOf<String>()
        val text = MessageFormat.parse("Hi {name}, {n, plural, one {one} other {many}}").format(emptyMap(), english) { problems += it }
        assertEquals("Hi {name}, many", text)
        assertEquals(2, problems.size)
    }

    @Test
    fun brokenPatternsAreRejected() {
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("Hi {name") }
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("{n, plural, one {x}}") }
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("{n, select, a {x}}") }
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("{when, date}") }
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("{n, plural, lots {x} other {y}}") }
        assertFailsWith<MessageSyntaxException> { MessageFormat.parse("a } b") }
    }

    @Test
    fun argumentNamesIncludeNestedOnes() {
        assertEquals(setOf("who", "n"), MessageFormat.parse("{who, select, other {{n}}}").argumentNames)
        assertTrue(MessageFormat.isPlain("Plain text, no syntax."))
    }
}
