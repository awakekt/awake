/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormulaValidationTest {
    private val combat = formulaVariables {
        number("atk")
        number("def")
        number("level")
        boolean("critical")
    }

    private fun problems(source: String, options: FormulaOptions = FormulaOptions.Default) =
        Formula.check(source, combat, options)

    private fun single(source: String, options: FormulaOptions = FormulaOptions.Default): FormulaProblem =
        problems(source, options).single()

    @Test
    fun validFormulaHasNoProblems() {
        assertEquals(emptyList(), problems("max(1, atk * 2 - def) * if(critical, 1.5, 1)"))
    }

    @Test
    fun unknownVariableNamesTheClosestDeclaredOne() {
        val problem = single("atk * 2 - deff")
        assertEquals(FormulaProblemKind.UnknownVariable, problem.kind)
        assertEquals("1:11: unknown variable 'deff'; did you mean 'def'?", problem.toString())
        assertEquals(10, problem.start)
        assertEquals(14, problem.end)
    }

    @Test
    fun unknownVariableListsAShortDeclaration() {
        assertEquals(
            "unknown variable 'speed'; the variables are atk, def, level, critical",
            single("speed").message,
        )
        assertEquals("unknown variable 'x'; no variables are declared", Formula.check("x").single().message)
    }

    @Test
    fun everySemanticProblemIsReportedInSourceOrder() {
        val found = problems("atak + foo(1) + min(1)")
        assertEquals(
            listOf(FormulaProblemKind.UnknownVariable, FormulaProblemKind.UnknownFunction, FormulaProblemKind.ArgumentCount),
            found.map { it.kind },
        )
        assertEquals(listOf(1, 8, 17), found.map { it.column })
    }

    @Test
    fun unknownFunctionAndMisusedNames() {
        assertEquals("unknown function 'mx'; did you mean 'max'?", single("mx(1, 2)").message)
        assertEquals("'atk' is a variable, not a function", single("atk(2)").message)
        assertEquals("'max' is a function; call it as max(a, b, ...)", single("max + 1").message)
    }

    @Test
    fun argumentCounts() {
        assertEquals("clamp takes 3 arguments, not 2: clamp(x, low, high)", single("clamp(1, 2)").message)
        assertEquals("min takes at least 2 arguments, not 1: min(a, b, ...)", single("min(1)").message)
        assertEquals("floor takes 1 argument, not 0: floor(x)", single("floor()").message)
        assertEquals("log takes 1 to 2 arguments, not 3: log(x) or log(x, base)", single("log(1, 2, 3)").message)
        assertEquals(
            "random takes no arguments or 2, not 1: random() or random(low, high)",
            single("random(1)").message,
        )
    }

    @Test
    fun typeMismatches() {
        assertEquals("'+' needs a number, but 'critical' is true or false", single("atk + critical").message)
        assertEquals("'and' needs true or false, but 'atk' is a number", FormulaCondition.check("atk and critical", combat).single().message)
        assertEquals("'==' compares a number with true or false", FormulaCondition.check("atk == critical", combat).single().message)
        assertEquals("if's condition needs true or false, but 'atk' is a number", single("if(atk, 1, 2)").message)
        assertEquals("if's two results must both be numbers or both be true or false", single("if(critical, 1, true)").message)
        assertEquals("the formula gives true or false, but a number is needed", single("atk > def").message)
        assertEquals(
            "the formula gives a number, but a condition must give true or false",
            FormulaCondition.check("atk", combat).single().message,
        )
    }

    @Test
    fun syntaxErrorsCarryPositions() {
        fun syntax(source: String): String = single(source).also { assertEquals(FormulaProblemKind.Syntax, it.kind) }.toString()
        assertEquals("1:1: the formula is empty", syntax("   "))
        assertEquals("1:4: the formula ends early; expected a value", syntax("1 +"))
        assertEquals("1:3: expected an operator before 'atk'", syntax("2 atk"))
        assertEquals("1:7: expected ')' to match the '(' before it", syntax("(1 + 2"))
        assertEquals("1:2: ')' has no matching '('", syntax("1)"))
        assertEquals("1:5: use == to compare", syntax("atk = 1"))
        assertEquals("1:5: use 'and' instead of '&'", syntax("atk && def"))
        assertEquals("1:5: use 'or' instead of '|'", syntax("atk || def"))
        assertEquals("1:1: use 'not' instead of '!'", syntax("!critical"))
        assertEquals("1:3: use ^ for a power", syntax("2 ** 3"))
        assertEquals("1:10: use if(condition, then, else) instead of '?' and ':'", syntax("critical ? 1 : 2"))
        assertEquals("1:3: unexpected character '#'", syntax("1 #"))
        assertEquals("1:1: 'if' needs (condition, then, else)", syntax("if"))
        assertEquals("1:5: expected a value, found 'and'", syntax("1 + and"))
        assertEquals("1:9: comparisons do not chain; join them with 'and', as in a < b and b < c", syntax("1 < atk < 3"))
        assertEquals("2:3: expected a value, found ','", syntax("1 +\n  , 2"))
    }

    @Test
    fun limits() {
        val tight = FormulaOptions(maxLength = 10, maxDepth = 3, maxNodes = 5)
        assertEquals(FormulaProblemKind.TooLong, single("1 + 2 + 3 + 4", tight).kind)
        assertEquals(FormulaProblemKind.TooDeep, single("((((1))))", tight).kind)
        assertEquals(FormulaProblemKind.TooLarge, single("1+2+3+4", tight).kind)
        assertEquals("the formula nests more than 3 levels deep", single("((((1))))", tight).message)
        assertEquals("the formula has more than 5 parts", single("1+2+3+4", tight).message)
        assertEquals("the formula has 13 characters, more than the limit of 10", single("1 + 2 + 3 + 4", tight).message)
    }

    @Test
    fun defaultLimitsStopPathologicalInput() {
        assertEquals(FormulaProblemKind.TooDeep, single("(".repeat(500) + "1" + ")".repeat(500)).kind)
        assertEquals(FormulaProblemKind.TooDeep, single("-".repeat(500) + "1").kind)
        assertEquals(FormulaProblemKind.TooLong, single("1" + "+1".repeat(600)).kind)
        assertEquals(FormulaProblemKind.TooLarge, single("1" + "+1".repeat(300)).kind)
    }

    @Test
    fun constantUndefinedOperationsAreRejectedUnderErrorPolicy() {
        val strict = FormulaOptions(undefinedMath = UndefinedMath.Error)
        val problem = single("atk + 1 / (2 - 2)", strict)
        assertEquals(FormulaProblemKind.Undefined, problem.kind)
        assertEquals("1:9: '/' divides by zero", problem.toString())
        assertEquals("sqrt of a negative number is undefined", single("sqrt(-1)", strict).message)
        assertEquals(0.0, Formula.compile("1 / 0").constantValue)
    }

    @Test
    fun compileThrowsWithEveryProblem() {
        val error = assertFailsWith<FormulaException> { Formula.compile("atak + critical", combat) }
        assertEquals("atak + critical", error.source)
        assertEquals(2, error.problems.size)
        assertEquals(
            "Invalid formula 'atak + critical': 1:1: unknown variable 'atak'; did you mean 'atk'?; " +
                "1:8: '+' needs a number, but 'critical' is true or false",
            error.message,
        )
    }

    @Test
    fun variableDeclarationsAreValidated() {
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("and") }
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("1st") }
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("a.") }
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("a..b") }
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("hit-points") }
        assertFailsWith<IllegalArgumentException> { FormulaVariables.numbers("atk", "atk") }
        assertEquals(listOf("a_1", "target.hp"), FormulaVariables.numbers("a_1", "target.hp").names)
    }

    @Test
    fun inputsRejectWrongVariablesAndValues() {
        val inputs = FormulaInputs(combat)
        assertFailsWith<IllegalArgumentException> { inputs[combat["critical"]] = 1.0 }
        assertFailsWith<IllegalArgumentException> { inputs[combat["atk"]] = true }
        assertFailsWith<IllegalArgumentException> { inputs[combat["atk"]] = Double.NaN }
        assertFailsWith<IllegalArgumentException> { inputs[combat["atk"]] = Double.POSITIVE_INFINITY }
        assertFailsWith<IllegalArgumentException> { inputs["speed"] = 1.0 }
        assertFailsWith<IllegalArgumentException> { inputs[FormulaVariables.numbers("other")["other"]] = 1.0 }
    }

    @Test
    fun inputsMustMatchTheFormulasVariables() {
        val formula = Formula.compile("atk", combat)
        val equalDeclaration = formulaVariables {
            number("atk")
            number("def")
            number("level")
            boolean("critical")
        }
        val inputs = FormulaInputs(equalDeclaration).apply { this["atk"] = 3.0 }
        assertEquals(3.0, formula.evaluate(inputs))
        assertFailsWith<IllegalArgumentException> { formula.evaluate(FormulaInputs(FormulaVariables.numbers("atk"))) }
    }

    @Test
    fun editorCanListFunctionsAndVariables() {
        assertTrue(FormulaFunction.entries.all { it.signature.startsWith(it.identifier + "(") })
        assertEquals(FormulaFunction.Lerp, FormulaFunction.of("lerp"))
        assertEquals(listOf("atk", "def", "level", "critical"), combat.names)
        assertEquals(listOf("def", "atk"), Formula.compile("def + atk * def", combat).usedVariables.map { it.name })
    }
}
