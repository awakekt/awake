/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormulaGrammarTest {
    private val variables = formulaVariables {
        number("a")
        number("b")
        number("attacker.level")
        boolean("flag")
        boolean("other")
    }

    private fun number(source: String, a: Double = 0.0, b: Double = 0.0, flag: Boolean = false): Double {
        val inputs = FormulaInputs(variables)
        inputs["a"] = a
        inputs["b"] = b
        inputs["flag"] = flag
        return Formula.compile(source, variables).evaluate(inputs)
    }

    private fun condition(source: String, a: Double = 0.0, flag: Boolean = false, other: Boolean = false): Boolean {
        val inputs = FormulaInputs(variables)
        inputs["a"] = a
        inputs["flag"] = flag
        inputs["other"] = other
        return FormulaCondition.compile(source, variables).test(inputs)
    }

    @Test
    fun numbers() {
        assertEquals(42.0, number("42"))
        assertEquals(1.5, number("1.5"))
        assertEquals(0.5, number(".5"))
        assertEquals(1500.0, number("1.5e3"))
        assertEquals(0.025, number("2.5E-2"))
        assertEquals(10.0, number("1e+1"))
    }

    @Test
    fun arithmeticPrecedence() {
        assertEquals(14.0, number("2 + 3 * 4"))
        assertEquals(20.0, number("(2 + 3) * 4"))
        assertEquals(3.0, number("10 - 4 - 3"))
        assertEquals(2.0, number("16 / 4 / 2"))
        assertEquals(7.0, number("1 + 12 % 5 * 3"))
        assertEquals(-1.0, number("-7 % 3"))
        assertEquals(1.0, number("7 % -3"))
    }

    @Test
    fun powerIsRightAssociativeAndBindsTighterThanSign() {
        assertEquals(512.0, number("2 ^ 3 ^ 2"))
        assertEquals(-4.0, number("-2 ^ 2"))
        assertEquals(4.0, number("(-2) ^ 2"))
        assertEquals(0.5, number("2 ^ -1"))
        assertEquals(18.0, number("2 * 3 ^ 2"))
        assertEquals(8.0, number("pow(2, 3)"))
    }

    @Test
    fun signs() {
        assertEquals(-3.0, number("-a", a = 3.0))
        assertEquals(3.0, number("--a", a = 3.0))
        assertEquals(3.0, number("+a", a = 3.0))
        assertEquals(1.0, number("4 - -a", a = -3.0))
    }

    @Test
    fun whitespaceAndLinesAreIgnored() {
        assertEquals(7.0, number("  1 +\n\t2 *\r\n 3  "))
    }

    @Test
    fun dottedNamesAreVariables() {
        val inputs = FormulaInputs(variables).apply { this["attacker.level"] = 9.0 }
        assertEquals(10.0, Formula.compile("attacker.level + 1", variables).evaluate(inputs))
    }

    @Test
    fun comparisons() {
        assertTrue(condition("a < 2", a = 1.0))
        assertFalse(condition("a < 1", a = 1.0))
        assertTrue(condition("a <= 1", a = 1.0))
        assertTrue(condition("a > 0", a = 1.0))
        assertTrue(condition("a >= 1", a = 1.0))
        assertTrue(condition("a == 1", a = 1.0))
        assertTrue(condition("a != 2", a = 1.0))
        assertTrue(condition("a + 1 > 1 * 2 - 1", a = 1.0))
    }

    @Test
    fun logicPrecedenceIsNotThenAndThenOr() {
        assertTrue(condition("true or false and false"))
        assertFalse(condition("(true or false) and false"))
        assertTrue(condition("not false and true"))
        assertTrue(condition("not a > 5", a = 1.0))
        assertFalse(condition("not not flag"))
        assertTrue(condition("flag == other"))
        assertTrue(condition("flag != other", flag = true))
        assertTrue(condition("a > 0 and a < 2 or flag", a = 1.0))
    }

    @Test
    fun ifChoosesABranch() {
        assertEquals(1.5, number("if(flag, 1.5, 1)", flag = true))
        assertEquals(1.0, number("if(flag, 1.5, 1)", flag = false))
        assertEquals(3.0, number("if(a > 2, a, if(a > 1, 2, 3))", a = 0.0))
        assertTrue(condition("if(flag, a > 1, other)", a = 2.0, flag = true))
    }

    @Test
    fun functions() {
        assertEquals(1.0, number("min(3, 1, 2)"))
        assertEquals(3.0, number("max(3, 1, 2)"))
        assertEquals(5.0, number("clamp(9, 0, 5)"))
        assertEquals(0.0, number("clamp(-1, 0, 5)"))
        assertEquals(2.0, number("clamp(2, 0, 5)"))
        assertEquals(4.0, number("clamp(2, 4, 1)"))
        assertEquals(2.0, number("floor(2.7)"))
        assertEquals(-3.0, number("floor(-2.1)"))
        assertEquals(3.0, number("ceil(2.1)"))
        assertEquals(3.0, number("round(2.5)"))
        assertEquals(-3.0, number("round(-2.5)"))
        assertEquals(2.0, number("round(2.4999)"))
        assertEquals(4.0, number("abs(-4)"))
        assertEquals(3.0, number("sqrt(9)"))
        assertEquals(0.0, number("log(1)"))
        assertEquals(3.0, number("log(1000, 10)"))
        assertEquals(10.0, number("log(1024, 2)"))
        assertEquals(15.0, number("lerp(10, 20, 0.5)"))
        assertEquals(25.0, number("lerp(10, 20, 1.5)"))
    }

    @Test
    fun constantPartsAreFolded() {
        assertEquals(10.0, Formula.compile("2 * (3 + 2)").constantValue)
        assertEquals(null, Formula.compile("a * (3 + 2)", variables).constantValue)
        assertEquals(10.0, number("a * (3 + 2)", a = 2.0))
    }
}
