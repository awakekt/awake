/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormulaEvaluationTest {
    private val stats = formulaVariables {
        number("x")
        number("y")
        boolean("flag")
    }
    private val x = stats["x"]
    private val y = stats["y"]
    private val flag = stats["flag"]

    private val strict = FormulaOptions(undefinedMath = UndefinedMath.Error)

    private fun inputs(x: Double = 0.0, y: Double = 0.0, flag: Boolean = false): FormulaInputs =
        FormulaInputs(stats).also {
            it[this.x] = x
            it[this.y] = y
            it[this.flag] = flag
        }

    @Test
    fun undefinedOperationsGiveZeroByDefault() {
        assertEquals(0.0, Formula.compile("x / y", stats).evaluate(inputs(x = 5.0)))
        assertEquals(0.0, Formula.compile("x % y", stats).evaluate(inputs(x = 5.0)))
        assertEquals(0.0, Formula.compile("sqrt(x)", stats).evaluate(inputs(x = -4.0)))
        assertEquals(0.0, Formula.compile("log(x)", stats).evaluate(inputs(x = 0.0)))
        assertEquals(0.0, Formula.compile("log(8, x)", stats).evaluate(inputs(x = 1.0)))
        assertEquals(0.0, Formula.compile("x ^ 0.5", stats).evaluate(inputs(x = -4.0)))
        assertEquals(0.0, Formula.compile("x ^ -1", stats).evaluate(inputs(x = 0.0)))
        assertEquals(0.0, Formula.compile("x * x", stats).evaluate(inputs(x = 1e200)))
        assertEquals(0.0, Formula.compile("x ^ y", stats).evaluate(inputs(x = 10.0, y = 400.0)))
    }

    @Test
    fun undefinedOperationsThrowWithTheirPositionUnderErrorPolicy() {
        fun failure(source: String, x: Double, y: Double = 0.0): String =
            assertFailsWith<FormulaEvaluationException> {
                Formula.compile(source, stats, strict).evaluate(inputs(x = x, y = y))
            }.message!!

        assertEquals("1:7: '/' divides by zero", failure("1 + x / y", 1.0))
        assertEquals("1:3: '%' divides by zero", failure("x % y", 1.0))
        assertEquals("1:1: sqrt of a negative number is undefined", failure("sqrt(x)", -1.0))
        assertEquals("1:1: log of zero or a negative number is undefined", failure("log(x)", 0.0))
        assertEquals("1:1: log to a base of zero or less, or of 1, is undefined", failure("log(2, x)", 1.0))
        assertEquals("1:3: '^' of a negative number to a fractional power is undefined", failure("x ^ 0.5", -2.0))
        assertEquals("1:3: '^' of zero to a negative power divides by zero", failure("x ^ -2", 0.0))
        assertEquals("1:3: '*' overflows", failure("x * x", 1e300))
        assertEquals("2:3: '+' overflows", failure("x\n  + x", 1e308))
    }

    @Test
    fun onlyTheChosenBranchIsEvaluated() {
        val guarded = Formula.compile("if(y != 0, x / y, -1)", stats, strict)
        assertEquals(-1.0, guarded.evaluate(inputs(x = 5.0)))
        assertEquals(2.5, guarded.evaluate(inputs(x = 5.0, y = 2.0)))
        val shortCircuit = FormulaCondition.compile("y != 0 and x / y > 1", stats, strict)
        assertFalse(shortCircuit.test(inputs(x = 5.0)))
        assertTrue(FormulaCondition.compile("y == 0 or x / y > 1", stats, strict).test(inputs(x = 5.0)))
    }

    @Test
    fun randomDrawsFromTheInputsSourceDeterministically() {
        val roll = Formula.compile("floor(random(1, 7))", stats)
        assertTrue(roll.usesRandom)
        assertEquals(null, roll.constantValue)

        fun rolls(seed: Long): List<Double> {
            val inputs = FormulaInputs(stats, FormulaRandom.seeded(seed))
            return List(20) { roll.evaluate(inputs) }
        }
        assertEquals(rolls(7), rolls(7))
        assertTrue(rolls(7).all { it in 1.0..6.0 })
        assertEquals(listOf(6.0, 3.0, 5.0, 3.0, 5.0, 5.0, 6.0, 5.0, 3.0, 2.0), rolls(2026).take(10))
    }

    @Test
    fun skippedBranchesDoNotDrawRandomNumbers() {
        val chance = FormulaCondition.compile("flag and random() < 0.5", stats)
        val random = SeededFormulaRandom(1)
        val inputs = FormulaInputs(stats, random)
        chance.test(inputs)
        assertEquals(1L, random.state)
        inputs[flag] = true
        chance.test(inputs)
        assertTrue(random.state != 1L)
    }

    @Test
    fun randomWithoutASourceThrows() {
        val failure = assertFailsWith<FormulaEvaluationException> {
            Formula.compile("1 + random()", stats).evaluate(inputs())
        }
        assertEquals("1:5: random() needs a random source: set FormulaInputs.random", failure.message)
        assertEquals(4, failure.start)
        assertEquals(12, failure.end)
    }

    @Test
    fun aBadRandomSourceIsRejected() {
        val inputs = FormulaInputs(stats, FormulaRandom { 1.0 })
        assertFailsWith<FormulaEvaluationException> { Formula.compile("random()", stats).evaluate(inputs) }
    }

    @Test
    fun aSavedGeneratorResumesItsSequence() {
        val random = FormulaRandom.seeded(99)
        random.nextLong()
        val saved = random.state
        val next = random.nextDouble()
        assertEquals(next, SeededFormulaRandom(saved).nextDouble())
    }

    @Test
    fun codeImplementsTheSameInterfaceAsData() {
        val fromData: Formula = Formula.compile("max(1, x * 2 - y)", stats)
        val fromCode = Formula { inputs -> maxOf(1.0, inputs[x] * 2 - inputs[y]) }
        for ((a, b) in listOf(30.0 to 12.0, 1.0 to 50.0, 7.5 to 0.25)) {
            assertEquals(fromCode.evaluate(inputs(a, b)), fromData.evaluate(inputs(a, b)))
        }
        assertEquals(3.0, Formula.constant(3.0).evaluate(inputs()))
    }

    @Test
    fun compiledFormulasCompareBySource() {
        assertEquals(Formula.compile("x + 1", stats), Formula.compile("x + 1", stats))
        assertEquals(Formula.compile("x + 1", stats).hashCode(), Formula.compile("x + 1", stats).hashCode())
        assertTrue(Formula.compile("x + 1", stats) != Formula.compile("x+1", stats))
        assertEquals("x + 1", Formula.compile("x + 1", stats).toString())
    }

    @Test
    fun inputsCanBeClearedAndRead() {
        val values = inputs(x = 2.0, flag = true)
        assertEquals(2.0, values[x])
        assertTrue(values.boolean(flag))
        values.clear()
        assertEquals(0.0, values[x])
        assertFalse(values.boolean(flag))
        values[x] = 4
        assertEquals(4.0, values[x])
    }
}
