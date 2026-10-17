/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/**
 * The ordinal plural rules of Unicode CLDR, by language: the form a language uses for 1st, 2nd,
 * 3rd, which `selectordinal` picks a branch by.
 *
 * English has `one` (1st, 21st), `two` (2nd, 22nd), `few` (3rd, 23rd) and `other` (4th, 11th, 12th,
 * 13th). Swedish, Italian, French, Filipino, Malay, Vietnamese and Ukrainian have rules of their own,
 * and the other languages [PluralRules] covers use `other` for every number. The rules are generated
 * from CLDR with the rest of [NumberFormat]'s data. A language not covered gets [Root]; pass a
 * [Strings] its own rule for one.
 */
object OrdinalRules {

    /** The CLDR root rule: every number is [PluralCategory.Other]. */
    val Root: PluralRule = PluralRules.Root

    /**
     * The ordinal rule [locale] uses.
     *
     * @param locale The locale; its language picks the rule.
     * @return The language's rule, or [Root] for a language not covered.
     */
    fun forLocale(locale: LocaleId): PluralRule {
        val language = if (locale.language == "tl") "fil" else locale.language
        val rules = CldrNumberData.ordinals[language] ?: return Root
        return CldrRuleSyntax.parse(rules)
    }
}
