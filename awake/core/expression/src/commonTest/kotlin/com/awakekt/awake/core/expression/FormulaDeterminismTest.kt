/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The same bits on every target. These run on the JVM, Android, iOS and wasm; the golden bit patterns were
 * taken from the JVM, so a target whose arithmetic differed would fail here.
 */
class FormulaDeterminismTest {
    private val golden = listOf(
        "pow(1.07, 37.5)" to 0x402949d5c086dd52uL,
        "pow(2.5, -3.25)" to 0x3faa0f36e2c12f9euL,
        "pow(100, 1.5)" to 0x408f400000000000uL,
        "pow(0.3, 0.7)" to 0x3fdb8d809c615f41uL,
        "pow(10, 15)" to 0x430c6bf526340000uL,
        "pow(3, 40)" to 0x43e517168a4523fduL,
        "pow(1.1, 2)" to 0x3ff35c28f5c28f5duL,
        "log(2)" to 0x3fe62e42fefa39efuL,
        "log(10)" to 0x40026bb1bbb55516uL,
        "log(0.001)" to 0xc01ba18a998fffa0uL,
        "log(123456.789)" to 0x40277281cad8a844uL,
        "log(1000, 10)" to 0x4008000000000000uL,
        "log(2, 3)" to 0x3fe430939835353duL,
        "sqrt(2)" to 0x3ff6a09e667f3bcduL,
        "round(-0.5)" to 0xbff0000000000000uL,
        "100 * 1.15 ^ 49" to 0x40f701714f5e1dbbuL,
        "floor(50 * 1.2 ^ 9.5)" to 0x4071a00000000000uL,
    )

    @Test
    fun goldenBitsMatchOnEveryTarget() {
        for ((source, bits) in golden) {
            val value = Formula.compile(source).constantValue!!
            assertEquals(bits, value.toRawBits().toULong(), "$source gave $value")
        }
    }

    @Test
    fun exactAnswersComeOutExact() {
        assertEquals(1000.0, Formula.compile("100 ^ 1.5").constantValue)
        assertEquals(3.0, Formula.compile("log(1000, 10)").constantValue)
        assertEquals(10.0, Formula.compile("log(1024, 2)").constantValue)
        assertEquals(1e15, Formula.compile("10 ^ 15").constantValue)
        assertEquals(-27.0, Formula.compile("(-3) ^ 3").constantValue)
        assertEquals(0.125, Formula.compile("2 ^ -3").constantValue)
    }

    @Test
    fun powAndLogStayCloseToThePlatform() {
        val random = SeededFormulaRandom(12345)
        repeat(2000) {
            val x = random.nextDouble() * 1000.0 + 1e-6
            val y = random.nextDouble() * 20.0 - 10.0
            val expectedPow = x.pow(y)
            if (expectedPow.isFinite() && expectedPow > 1e-300) {
                val pow = DeterministicMath.pow(x, y)
                assertTrue(abs(pow - expectedPow) <= abs(expectedPow) * 1e-15, "pow($x, $y) = $pow, platform $expectedPow")
            }
            val log = DeterministicMath.log(x)
            assertTrue(abs(log - ln(x)) <= maxOf(abs(ln(x)), 1e-300) * 1e-15, "log($x) = $log, platform ${ln(x)}")
        }
    }

    @Test
    fun logAndPowEdges() {
        assertEquals(Double.NEGATIVE_INFINITY, DeterministicMath.log(0.0))
        assertTrue(DeterministicMath.log(-1.0).isNaN())
        assertEquals(ln(Double.MIN_VALUE), DeterministicMath.log(Double.MIN_VALUE), 1e-12)
        assertEquals(ln(Double.MAX_VALUE), DeterministicMath.log(Double.MAX_VALUE), 1e-12)
        assertEquals(1.0, DeterministicMath.pow(0.0, 0.0))
        assertEquals(0.0, DeterministicMath.pow(0.0, 2.0))
        assertEquals(Double.POSITIVE_INFINITY, DeterministicMath.pow(0.0, -1.0))
        assertEquals(Double.POSITIVE_INFINITY, DeterministicMath.pow(10.0, 400.0))
        assertEquals(0.0, DeterministicMath.pow(10.0, -400.0))
        assertEquals(1.0, DeterministicMath.pow(1.0, 1e300))
        assertTrue(DeterministicMath.pow(-8.0, 1.0 / 3.0).isNaN())
        assertEquals(4.9e-324, DeterministicMath.pow(2.0, -1074.0))
    }

    @Test
    fun roundHalvesAwayFromZero() {
        assertEquals(1.0, DeterministicMath.round(0.5))
        assertEquals(0.0, DeterministicMath.round(0.49999999999999994))
        assertEquals(-1.0, DeterministicMath.round(-0.5))
        assertEquals(4503599627370497.0, DeterministicMath.round(4503599627370497.0))
    }

    @Test
    fun seededRandomMatchesTheSplitMix64Reference() {
        val random = SeededFormulaRandom(0)
        assertEquals(0xe220a8397b1dcdafuL, random.nextLong().toULong())
        assertEquals(0x6e789e6aa1b965f4uL, random.nextLong().toULong())
        assertEquals(0x06c45d188009454fuL, random.nextLong().toULong())
    }
}
