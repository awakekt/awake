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
import com.awakekt.awake.core.localization.PluralCategory.Zero
import kotlin.test.Test
import kotlin.test.assertEquals

class PluralRulesTest {

    private fun categories(tag: String, vararg numbers: Any): List<PluralCategory> {
        val rule = PluralRules.forLocale(LocaleId.of(tag))
        return numbers.map { rule.select(PluralOperands.of(it)) }
    }

    @Test
    fun englishHasOneOnlyForTheIntegerOne() {
        assertEquals(listOf(Other, One, Other, Other, Other), categories("en", 0, 1, 2, 1.5, "1.0"))
    }

    @Test
    fun spanishCountsMillionsAsMany() {
        assertEquals(listOf(Other, One, Other, Many, Other), categories("es", 0, 1, 2, 1_000_000, 1_000_001))
        assertEquals(One, categories("es", 1.0).single())
    }

    @Test
    fun filipinoExcludesFourSixAndNine() {
        assertEquals(listOf(One, One, One, Other, One, Other, Other, One, Other), categories("fil", 1, 2, 3, 4, 5, 6, 9, 10, 14))
        assertEquals(listOf(One, Other), categories("fil", "0.5", "0.4"))
    }

    @Test
    fun eastAsianLanguagesHaveOnlyOther() {
        for (tag in listOf("ko", "ja", "zh", "zh-Hant-TW")) {
            assertEquals(listOf(Other, Other, Other), categories(tag, 0, 1, 2), tag)
        }
    }

    @Test
    fun russianHasOneFewAndMany() {
        assertEquals(
            listOf(One, Few, Many, Many, One, Few, Many, Many, Other),
            categories("ru", 1, 2, 5, 11, 21, 22, 12, 0, 1.5),
        )
    }

    @Test
    fun arabicUsesAllSixCategories() {
        assertEquals(
            listOf(Zero, One, Two, Few, Few, Many, Many, Other, Few, Other),
            categories("ar", 0, 1, 2, 3, 10, 11, 99, 100, 103, 3.5),
        )
    }

    @Test
    fun portugalCountsZeroAsOtherAndBrazilAsOne() {
        assertEquals(One, categories("pt-BR", 0).single())
        assertEquals(Other, categories("pt-PT", 0).single())
    }

    @Test
    fun anUncoveredLanguageGetsTheRootRule() {
        assertEquals(listOf(Other, Other), categories("xx", 1, 2))
    }

    @Test
    fun operandsKeepVisibleFractionDigits() {
        val operands = PluralOperands.of("1.50")
        assertEquals(1L, operands.i)
        assertEquals(2, operands.v)
        assertEquals(50L, operands.f)
        assertEquals(0, PluralOperands.of(3.0).v)
        assertEquals(1, PluralOperands.of(1.5).v)
        assertEquals(1L, PluralOperands.of(-1).i)
    }
}
