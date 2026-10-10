/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NumberFormatTest {

    private fun format(spec: String) = assertNotNull(NumberFormat.parse(spec), "$spec is a format")

    @Test
    fun aWholeNumberIsWrittenExactlyWhereAFloatWouldRoundIt() {
        // 16,777,217 is the first whole number a Float cannot hold: it reads back as 16,777,216.
        assertEquals("16,777,217", format("n0").format(16_777_217L))
        assertEquals("16777216", 16_777_217f.toLong().toString(), "the loss this format avoids")
        assertEquals("9,223,372,036,854,775,807", format("n0").format(Long.MAX_VALUE))
        assertEquals("-9,223,372,036,854,775,808", format("n0").format(Long.MIN_VALUE))
        assertEquals("9223372036854775807", format("f0").format(Long.MAX_VALUE))
    }

    @Test
    fun digitsGroupInThrees() {
        val grouped = format("n0")

        assertEquals(
            listOf("0", "7", "999", "1,000", "12,345", "100,000", "1,234,567", "-1,000", "-999"),
            listOf(0L, 7L, 999L, 1_000L, 12_345L, 100_000L, 1_234_567L, -1_000L, -999L).map(grouped::format),
        )
    }

    @Test
    fun theTableInTheDocumentationHolds() {
        val specs = listOf("n0", "n2", "f1", "p0")

        assertEquals(listOf("1,234,567", "1,234,567.00", "1234567.0", "123456700%"), specs.map { format(it).format(1_234_567L) })
        assertEquals(listOf("1,235", "1,234.57", "1234.6", "123457%"), specs.map { format(it).format(1234.5678) })
        assertEquals(listOf("0", "0.46", "0.5", "46%"), specs.map { format(it).format(0.456) })
        assertEquals(listOf("0", "-0.04", "0.0", "-4%"), specs.map { format(it).format(-0.04) })
    }

    @Test
    fun aValueRoundsHalfAwayFromZeroAsItReadsInDecimal() {
        assertEquals("1.01", format("f2").format(1.005), "a double holds 1.005 as 1.00499999999999989...")
        assertEquals("3", format("f0").format(2.5))
        assertEquals("-3", format("f0").format(-2.5))
        assertEquals("1", format("f0").format(0.5))
        assertEquals("0.0", format("f1").format(0.049))
        assertEquals("0.1", format("f1").format(0.05))
        assertEquals("10.0", format("f1").format(9.96), "the carry runs into a new digit")
        assertEquals("1,000,000", format("n0").format(999_999.5), "and through a group")
        assertEquals("0", format("f0").format(0.4999))
    }

    @Test
    fun aNumberThatRoundsToZeroHasNoMinusSign() {
        assertEquals("0.0", format("f1").format(-0.04))
        assertEquals("0", format("n0").format(-0.4))
        assertEquals("0", format("f0").format(-0.0))
        assertEquals("-0.1", format("f1").format(-0.05), "while -0.05 rounds away from zero to a tenth")
    }

    @Test
    fun aPercentIsAFractionTimesAHundred() {
        assertEquals("100%", format("p0").format(1L))
        assertEquals("0%", format("p0").format(0L))
        assertEquals("45.7%", format("p1").format(0.4567))
        assertEquals("45.67%", format("p2").format(0.4567))
        assertEquals("150%", format("p0").format(1.5))
        assertEquals("-12.5%", format("p1").format(-0.125))
        assertEquals("0.5%", format("p1").format(0.005))
    }

    @Test
    fun largeAndSmallDoublesAreWrittenInFull() {
        assertEquals("1,000,000,000,000,000,000,000", format("n0").format(1e21))
        assertEquals("0.000015", format("f6").format(1.5e-5))
        assertEquals("0.00", format("f2").format(1e-12))
        assertEquals("0.100000000", format("f9").format(0.1), "a tenth, not the binary fraction nearest it")
        assertEquals("100,000,000,000", format("n0").format(1e11))
    }

    @Test
    fun aValueThatIsNotFiniteIsWrittenAsItIs() {
        val grouped = format("n2")

        assertEquals("NaN", grouped.format(Double.NaN))
        assertEquals("Infinity", grouped.format(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", grouped.format(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun aSpecIsAStyleLetterAndOneDigit() {
        assertEquals(NumberFormat(NumberStyle.Grouped, 0), NumberFormat.parse("n0"))
        assertEquals(NumberFormat(NumberStyle.Fixed, 2), NumberFormat.parse("f2"))
        assertEquals(NumberFormat(NumberStyle.Percent, 9), NumberFormat.parse("p9"))
        assertEquals(NumberFormat(NumberStyle.Grouped, 1), NumberFormat.parse("N1"), "either case")
        for (spec in listOf("", "n", "0", "n10", "n-1", "x1", "nn", " n0", "n0 ", "n٣", "int", "grouped")) {
            assertNull(NumberFormat.parse(spec), "'$spec' is not a format")
        }
    }

    @Test
    fun aFormatPrintsAsTheSpecItWasReadFrom() {
        for (spec in listOf("n0", "f3", "p9")) assertEquals(spec, format(spec).toString())
    }

    @Test
    fun decimalsStayWithinWhatTheSpecCanSay() {
        assertEquals("0.123456789", NumberFormat(NumberStyle.Fixed, NumberFormat.MAX_DECIMALS).format(0.123456789))
        assertFailsWith<IllegalArgumentException> { NumberFormat(NumberStyle.Fixed, NumberFormat.MAX_DECIMALS + 1) }
        assertFailsWith<IllegalArgumentException> { NumberFormat(NumberStyle.Fixed, -1) }
    }
}
