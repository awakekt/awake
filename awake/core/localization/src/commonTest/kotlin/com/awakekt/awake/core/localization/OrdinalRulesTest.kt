/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.localization.PluralCategory.Few
import com.awakekt.awake.core.localization.PluralCategory.Many
import com.awakekt.awake.core.localization.PluralCategory.One
import com.awakekt.awake.core.localization.PluralCategory.Other
import com.awakekt.awake.core.localization.PluralCategory.Two
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The integer samples CLDR lists with each ordinal rule. */
class OrdinalRulesTest {

    private fun assertOrdinals(tag: String, expected: Map<PluralCategory, List<Int>>) {
        val rule = OrdinalRules.forLocale(LocaleId.of(tag))
        for ((category, numbers) in expected) {
            for (n in numbers) assertEquals(category, rule.select(PluralOperands.of(n)), "$tag $n")
        }
    }

    @Test
    fun english() = assertOrdinals(
        "en",
        mapOf(
            One to listOf(1, 21, 31, 41, 101, 1001),
            Two to listOf(2, 22, 32, 102, 1002),
            Few to listOf(3, 23, 33, 103, 1003),
            Other to listOf(0, 4, 11, 12, 13, 18, 100, 111, 112, 113, 1000),
        ),
    )

    @Test
    fun swedish() = assertOrdinals("sv", mapOf(One to listOf(1, 2, 21, 22, 101, 102), Other to listOf(0, 3, 11, 12, 13, 100)))

    @Test
    fun italian() = assertOrdinals("it", mapOf(Many to listOf(8, 11, 80, 800), Other to listOf(0, 1, 7, 9, 10, 12, 81, 801)))

    @Test
    fun oneForFirstOnly() {
        for (tag in listOf("fr", "fil", "tl", "ms", "vi")) {
            assertOrdinals(tag, mapOf(One to listOf(1), Other to listOf(0, 2, 11, 21, 101)))
        }
    }

    @Test
    fun ukrainian() = assertOrdinals("uk", mapOf(Few to listOf(3, 23, 33, 103), Other to listOf(0, 1, 2, 4, 13, 113)))

    @Test
    fun theRestUseOther() {
        for (tag in listOf("de", "nl", "fi", "et", "es", "pt", "ru", "pl", "ar", "tr", "ja", "ko", "zh", "th", "id", "yue")) {
            assertOrdinals(tag, mapOf(Other to listOf(0, 1, 2, 3, 11, 21, 101)))
        }
        assertEquals(OrdinalRules.Root, OrdinalRules.forLocale(LocaleId.of("xx")))
    }

    @Test
    fun theRuleSyntaxReadsCldrConditions() {
        val rule = CldrRuleSyntax.parse("one: i = 1 and v = 0; few: v = 0 and i % 10 = 2..4 and i % 100 != 12..14 or f = 7")
        assertEquals(One, rule.select(PluralOperands.of(1)))
        assertEquals(Other, rule.select(PluralOperands.of("1.0")), "v is 1")
        assertEquals(Few, rule.select(PluralOperands.of(23)))
        assertEquals(Other, rule.select(PluralOperands.of(13)))
        assertEquals(Few, rule.select(PluralOperands.of("0.7")), "the or branch")
        assertEquals(Other, CldrRuleSyntax.parse("").select(PluralOperands.of(1)))
        assertFailsWith<IllegalArgumentException> { CldrRuleSyntax.parse("one: q = 1") }
    }
}
