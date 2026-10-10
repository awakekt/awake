/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.format

/** What a [NumberFormat] writes. */
enum class NumberStyle(
    /** The letter a format's spec starts with: `n` in `n0`. */
    val letter: Char,
) {
    /** The digits in groups of three, such as `1,234,567.50`. */
    Grouped('n'),

    /** The digits alone, such as `1234567.50`. */
    Fixed('f'),

    /** The value as a percentage of 1, such as `45.7%` for 0.457. */
    Percent('p'),
}

/**
 * How a number is written for display, such as `1,234,567` or `45.7%`: a [style] and a number of
 * [decimals]. Its spec is the style's letter and the decimals as a digit, so `n0` is [NumberStyle.Grouped]
 * with no decimals, `f2` is [NumberStyle.Fixed] to two places, and `p1` is [NumberStyle.Percent] to one.
 *
 * | Value | `n0` | `n2` | `f1` | `p0` |
 * | --- | --- | --- | --- | --- |
 * | `1234567` | `1,234,567` | `1,234,567.00` | `1234567.0` | `123456700%` |
 * | `1234.5678` | `1,235` | `1,234.57` | `1234.6` | `123457%` |
 * | `0.456` | `0` | `0.46` | `0.5` | `46%` |
 * | `-0.04` | `0` | `-0.04` | `0.0` | `-4%` |
 *
 * A value is rounded to its decimals half away from zero, as it reads in decimal: 1.005 to two places
 * is 1.01. A whole number is written exactly, however large, where a `Float` or a `Double` would lose
 * its low digits. A number that rounds to zero has no minus sign. Separators are always `,` and `.`; a
 * game that localises its numbers writes its own text.
 *
 * @property style What the format writes.
 * @property decimals The places after the decimal point, from 0 to [MAX_DECIMALS].
 */
data class NumberFormat(val style: NumberStyle, val decimals: Int = 0) {
    init {
        require(decimals in 0..MAX_DECIMALS) { "decimals must be 0 to $MAX_DECIMALS, not $decimals" }
    }

    /** [value] written in this format, to the last digit. */
    fun format(value: Long): String = write(value.toString())

    /**
     * [value] written in this format, as the decimal it prints as: `0.1` is a tenth, not the nearest
     * binary fraction. A value that is not finite is written as it is: `NaN`, `Infinity`.
     */
    fun format(value: Double): String = if (value.isFinite()) write(value.toString()) else value.toString()

    /** This format's spec, which [parse] reads back. */
    override fun toString(): String = "${style.letter}$decimals"

    // [literal] is a Long or a finite Double as Kotlin prints it: `-12`, `3.5`, `1.0E-5`, or `1e21`
    // on a platform that leaves out the fraction. That is a decimal, so the rest is string
    // arithmetic: nothing is rounded twice, and nothing overflows however large the value.
    private fun write(literal: String): String {
        val parts = checkNotNull(LITERAL.matchEntire(literal)) { "not a number: $literal" }.groupValues
        val negative = parts[SIGN].isNotEmpty()
        // The value is `digits` x 10^-scale.
        val digits = parts[WHOLE] + parts[FRACTION]
        val scale = parts[FRACTION].length - (parts[EXPONENT].toIntOrNull() ?: 0) - if (style == NumberStyle.Percent) PERCENT_SHIFT else 0
        val units = if (scale <= decimals) digits + "0".repeat(decimals - scale) else roundOff(digits, scale - decimals)
        val padded = units.padStart(decimals + 1, '0')
        val whole = padded.dropLast(decimals).trimStart('0').ifEmpty { "0" }
        val fraction = if (decimals > 0) "." + padded.takeLast(decimals) else ""
        val sign = if (negative && units.any { it != '0' }) "-" else ""
        return sign + (if (style == NumberStyle.Grouped) grouped(whole) else whole) + fraction + if (style == NumberStyle.Percent) "%" else ""
    }

    /** Reading a format back from its spec. */
    companion object {
        /** The most decimals a format can ask for. */
        const val MAX_DECIMALS = 9

        private const val SIGN = 1
        private const val WHOLE = 2
        private const val FRACTION = 3
        private const val EXPONENT = 4
        private const val PERCENT_SHIFT = 2
        private const val GROUP = 3
        private const val SPEC_LENGTH = 2
        private val LITERAL = Regex("""(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?""")

        /**
         * The format [spec] names, such as `n0`, `f2` or `p1`, or null when it is not a style letter
         * (`n`, `f`, `p`, in either case) followed by one digit.
         */
        fun parse(spec: String): NumberFormat? {
            val style = NumberStyle.entries.firstOrNull { spec.firstOrNull()?.lowercaseChar() == it.letter }
            val decimals = spec.getOrNull(1)?.takeIf { it in '0'..'9' }?.let { it - '0' }
            return if (style == null || decimals == null || spec.length != SPEC_LENGTH) null else NumberFormat(style, decimals)
        }

        /** [digits] without its last [drop] digits, rounded up when the first of them was 5 or more. */
        private fun roundOff(digits: String, drop: Int): String {
            // At least one digit is kept, so 0.5 to no decimals has a 0 to round up.
            val padded = digits.padStart(drop + 1, '0')
            val kept = padded.dropLast(drop)
            return if (padded[kept.length] >= '5') increment(kept) else kept
        }

        private fun increment(digits: String): String {
            val chars = digits.toCharArray()
            var i = chars.lastIndex
            while (i >= 0 && chars[i] == '9') chars[i--] = '0'
            if (i < 0) return "1" + chars.concatToString()
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }

        private fun grouped(digits: String): String = buildString {
            for ((i, digit) in digits.withIndex()) {
                if (i > 0 && (digits.length - i) % GROUP == 0) append(',')
                append(digit)
            }
        }
    }
}
