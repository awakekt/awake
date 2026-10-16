/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/** A Unicode CLDR plural category: the form of a word a language uses for a count. */
enum class PluralCategory {
    Zero,
    One,
    Two,
    Few,
    Many,
    Other,
    ;

    /** The keyword a `plural` message names this category with: `zero`, `one`, `two`, `few`, `many`, `other`. */
    val keyword: String get() = name.lowercase()

    companion object {
        /**
         * The category a `plural` message keyword names.
         *
         * @param keyword One of `zero`, `one`, `two`, `few`, `many`, `other`.
         * @return The category, or null for any other word.
         */
        fun ofKeyword(keyword: String): PluralCategory? = entries.firstOrNull { it.keyword == keyword }
    }
}

/**
 * The CLDR plural operands of a number, as the rules read them.
 *
 * @property n The absolute value.
 * @property i The integer digits of [n].
 * @property v The number of visible fraction digits, trailing zeros included: 2 for `1.50`.
 * @property f The visible fraction digits as an integer, trailing zeros included: 50 for `1.50`.
 */
class PluralOperands(val n: Double, val i: Long, val v: Int, val f: Long) {

    /** Whether the number is an integer written without a fraction. */
    val isInteger: Boolean get() = v == 0

    companion object {
        /**
         * The operands of [number]. An integer type has no fraction digits. A [Double] or [Float]
         * has the fraction digits of its shortest decimal form, so `1.0` reads as `1` and `1.5`
         * as one fraction digit; pass the number as a [String] to keep visible zeros, `"1.50"`.
         *
         * @param number An [Int], [Long], [Short], [Byte], [Double], [Float] or decimal [String].
         * @return The operands.
         * @throws IllegalArgumentException when [number] is none of those, or a string that is not a decimal.
         */
        fun of(number: Any): PluralOperands = when (number) {
            is Int, is Long, is Short, is Byte -> {
                val value = (number as Number).toLong()
                val abs = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
                PluralOperands(abs.toDouble(), abs, 0, 0)
            }
            is Double, is Float -> {
                val value = (number as Number).toDouble()
                if (value.isNaN() || value.isInfinite()) {
                    PluralOperands(value, 0, 0, 0)
                } else if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < LONG_SAFE) {
                    val abs = kotlin.math.abs(value).toLong()
                    PluralOperands(abs.toDouble(), abs, 0, 0)
                } else {
                    ofDecimal(plainDecimal(kotlin.math.abs(value)))
                }
            }
            is String -> ofDecimal(number.trim().removePrefix("-").removePrefix("+"))
            else -> throw IllegalArgumentException("Not a number: $number (${number::class.simpleName}).")
        }

        private fun ofDecimal(text: String): PluralOperands {
            val whole = text.substringBefore('.')
            val fraction = if ('.' in text) text.substringAfter('.') else ""
            require(whole.isNotEmpty() && whole.all(Char::isDigit) && fraction.all(Char::isDigit)) { "Not a decimal: \"$text\"." }
            val i = whole.trimStart('0').take(MAX_DIGITS).ifEmpty { "0" }.toLong()
            val f = fraction.take(MAX_DIGITS).ifEmpty { "0" }.toLong()
            val n = text.toDouble()
            return PluralOperands(n, i, fraction.length, f)
        }

        /** [value] written without an exponent, as `Double.toString` writes large and small numbers with one. */
        private fun plainDecimal(value: Double): String {
            val text = value.toString()
            val exponentAt = text.indexOfFirst { it == 'E' || it == 'e' }
            if (exponentAt < 0) return text
            val mantissa = text.substring(0, exponentAt)
            val exponent = text.substring(exponentAt + 1).toInt()
            val digits = mantissa.replace(".", "")
            val point = mantissa.indexOf('.').let { if (it < 0) mantissa.length else it } + exponent
            return when {
                point <= 0 -> "0." + "0".repeat(-point) + digits
                point >= digits.length -> digits + "0".repeat(point - digits.length)
                else -> digits.substring(0, point) + "." + digits.substring(point)
            }.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }

        private const val LONG_SAFE = 9.0e15
        private const val MAX_DIGITS = 18
    }
}

/** Picks the [PluralCategory] a language uses for a number. */
fun interface PluralRule {
    /**
     * @param operands The number, as [PluralOperands.of] reads it.
     * @return The category its language uses for it.
     */
    fun select(operands: PluralOperands): PluralCategory
}

/**
 * The cardinal plural rules of Unicode CLDR, by language.
 *
 * Covered: Arabic, Cantonese, Chinese, Dutch, English, Estonian, Filipino, Finnish, French, German,
 * Indonesian, Italian, Japanese, Korean, Malay, Polish, Portuguese, Russian, Spanish, Swedish, Thai,
 * Turkish, Ukrainian and Vietnamese. Any other
 * language gets the CLDR root rule, where every number is `other`; pass a [Strings] its own rule for
 * one this does not cover.
 *
 * The rules ignore the compact-exponent operand `e`, which only matters for numbers written as
 * `1.2M`. Portuguese counts as Brazil does, where 0 is `one`, except in `pt-PT`, where only 1 is.
 */
object PluralRules {

    /** The CLDR root rule: every number is [PluralCategory.Other]. */
    val Root: PluralRule = PluralRule { PluralCategory.Other }

    private val english = PluralRule { if (it.i == 1L && it.isInteger) PluralCategory.One else PluralCategory.Other }

    private val millions = { o: PluralOperands -> o.isInteger && o.i != 0L && o.i % 1_000_000 == 0L }

    private val spanish = PluralRule {
        when {
            it.n == 1.0 -> PluralCategory.One
            millions(it) -> PluralCategory.Many
            else -> PluralCategory.Other
        }
    }

    private val italian = PluralRule {
        when {
            it.i == 1L && it.isInteger -> PluralCategory.One
            millions(it) -> PluralCategory.Many
            else -> PluralCategory.Other
        }
    }

    private val french = PluralRule {
        when {
            it.i == 0L || it.i == 1L -> PluralCategory.One
            millions(it) -> PluralCategory.Many
            else -> PluralCategory.Other
        }
    }

    private val filipino = PluralRule {
        val one = if (it.isInteger) it.i in 1L..3L || it.i % 10 !in FILIPINO_EXCLUDED else it.f % 10 !in FILIPINO_EXCLUDED
        if (one) PluralCategory.One else PluralCategory.Other
    }

    private val eastSlavic = PluralRule {
        if (!it.isInteger) return@PluralRule PluralCategory.Other
        val mod10 = it.i % 10
        val mod100 = it.i % 100
        when {
            mod10 == 1L && mod100 != 11L -> PluralCategory.One
            mod10 in 2L..4L && mod100 !in 12L..14L -> PluralCategory.Few
            else -> PluralCategory.Many
        }
    }

    private val polish = PluralRule {
        if (!it.isInteger) return@PluralRule PluralCategory.Other
        val mod10 = it.i % 10
        val mod100 = it.i % 100
        when {
            it.i == 1L -> PluralCategory.One
            mod10 in 2L..4L && mod100 !in 12L..14L -> PluralCategory.Few
            else -> PluralCategory.Many
        }
    }

    private val arabic = PluralRule {
        // n rather than i: 3.5 has no integer remainder of 100 in 3..10, so it is other.
        val integral = it.n == kotlin.math.floor(it.n)
        val mod100 = if (integral) (it.n % 100).toLong() else -1L
        when {
            it.n == 0.0 -> PluralCategory.Zero
            it.n == 1.0 -> PluralCategory.One
            it.n == 2.0 -> PluralCategory.Two
            mod100 in 3L..10L -> PluralCategory.Few
            mod100 in 11L..99L -> PluralCategory.Many
            else -> PluralCategory.Other
        }
    }

    private val turkish = PluralRule { if (it.n == 1.0) PluralCategory.One else PluralCategory.Other }

    private val byLanguage: Map<String, PluralRule> = buildMap {
        listOf("en", "de", "nl", "sv", "fi", "et").forEach { put(it, english) }
        listOf("ja", "ko", "zh", "th", "vi", "id", "ms", "yue").forEach { put(it, Root) }
        put("es", spanish)
        put("it", italian)
        put("fr", french)
        put("pt", french)
        put("fil", filipino)
        put("tl", filipino)
        put("ru", eastSlavic)
        put("uk", eastSlavic)
        put("pl", polish)
        put("ar", arabic)
        put("tr", turkish)
    }

    /**
     * The rule [locale] counts by.
     *
     * @param locale The locale; its language picks the rule.
     * @return The language's rule, or [Root] for a language not covered.
     */
    fun forLocale(locale: LocaleId): PluralRule {
        if (locale.language == "pt" && locale.tag.endsWith("-PT")) return italian
        return byLanguage[locale.language] ?: Root
    }

    private val FILIPINO_EXCLUDED = setOf(4L, 6L, 9L)
}
