/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/**
 * One locale's number data from CLDR, as the `generateCldrNumberData` task in `cldr.gradle.kts` writes it.
 *
 * @property decimal The decimal separator.
 * @property group The grouping separator.
 * @property minus The minus sign, with any direction marks CLDR puts around it.
 * @property percent The percent pattern with `0` where the number goes: `0%`, `0 %`, `%0`.
 * @property minimumGrouping How many digits the first group needs before a number is grouped: 2
 *   writes `1234` but `12,345`.
 * @property compact The short compact pattern for 10^3 through 10^14: `0K`, `00K`, … A pattern of
 *   only `0` leaves a number of that size uncompacted. Where the plural forms differ, the entry is
 *   `category=pattern` pairs joined by `|`.
 */
internal class CldrNumbers(
    val decimal: String,
    val group: String,
    val minus: String,
    val percent: String,
    val minimumGrouping: Int,
    val compact: List<String>,
)

/**
 * A CLDR plural rule in its own syntax, `n % 10 = 1 and n % 100 != 11`, read once and then evaluated
 * against [PluralOperands]. Supports the operands `n`, `i`, `v`, `w`, `f`, `t` (with `c` and `e` as 0),
 * `%`, `=`/`!=`, value lists, `a..b` ranges, `and` and `or`.
 */
internal object CldrRuleSyntax {

    /**
     * Parses `category: condition; category: condition`.
     *
     * @throws IllegalArgumentException when a rule does not follow the syntax.
     */
    fun parse(rules: String): PluralRule {
        if (rules.isBlank()) return PluralRules.Root
        val branches = rules.split(';').map { branch ->
            val category = PluralCategory.ofKeyword(branch.substringBefore(':').trim())
                ?: throw IllegalArgumentException("Not a plural category in \"$branch\".")
            category to condition(branch.substringAfter(':').trim())
        }
        return PluralRule { operands -> branches.firstOrNull { (_, matches) -> matches(operands) }?.first ?: PluralCategory.Other }
    }

    private fun condition(text: String): (PluralOperands) -> Boolean {
        val alternatives = text.split(" or ").map { and -> and.split(" and ").map(::relation) }
        return { o -> alternatives.any { relations -> relations.all { it(o) } } }
    }

    private fun relation(text: String): (PluralOperands) -> Boolean {
        val negated = "!=" in text
        val (left, right) = text.split(if (negated) "!=" else "=").map(String::trim)
        val operand = left.substringBefore('%').trim()
        val modulus = left.substringAfter('%', "").trim().takeIf { it.isNotEmpty() }?.toLong()
        val ranges = right.split(',').map { item ->
            val bounds = item.trim().split("..")
            bounds.first().toLong()..bounds.last().toLong()
        }
        require(operand.length == 1 && operand[0] in "niwvftce") { "Not a plural operand: \"$operand\" in \"$text\"." }
        return { o ->
            val value = value(o, operand[0], modulus)
            val matches = value == kotlin.math.floor(value) && ranges.any { value.toLong() in it }
            matches != negated
        }
    }

    private fun value(o: PluralOperands, operand: Char, modulus: Long?): Double {
        val v = when (operand) {
            'n' -> o.n
            'i' -> o.i.toDouble()
            'v' -> o.v.toDouble()
            'f' -> o.f.toDouble()
            't' -> trimmedFraction(o).toDouble()
            'w' -> (o.v - trailingZeros(o)).toDouble()
            else -> 0.0
        }
        return if (modulus == null) v else v % modulus
    }

    private fun trailingZeros(o: PluralOperands): Int {
        var f = o.f
        var zeros = 0
        while (zeros < o.v && f % 10 == 0L) {
            f /= 10
            zeros++
        }
        return zeros
    }

    private fun trimmedFraction(o: PluralOperands): Long {
        var f = o.f
        while (f != 0L && f % 10 == 0L) f /= 10
        return f
    }
}
