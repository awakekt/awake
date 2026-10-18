/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FormulaSetTest {
    private val hit = formulaVariables {
        number("atk")
        number("def")
        boolean("critical")
    }
    private val level = FormulaVariables.numbers("level")

    @Test
    fun everySlotCompilesAndIsFoundByHandleAndPath() {
        val builder = FormulaSet.Builder()
        val damage = builder.formula("combat.damage", "max(1, atk * 2 - def) * if(critical, 1.5, 1)", hit)
        val canLearn = builder.condition("skills.canLearn", "level >= 10", level)
        val set = builder.build()

        val inputs = FormulaInputs(hit)
        inputs[hit["atk"]] = 40.0
        inputs[hit["def"]] = 25.0
        inputs[hit["critical"]] = true
        assertEquals(82.5, set[damage].evaluate(inputs))
        assertSame(set[damage], set.formula("combat.damage"))

        val learner = FormulaInputs(level)
        learner[level["level"]] = 12.0
        assertTrue(set[canLearn].test(learner))
        assertSame(set[canLearn], set.condition("skills.canLearn"))
        assertEquals(listOf("combat.damage", "skills.canLearn"), set.expressions.keys.toList())
        assertEquals(listOf("combat.damage", "skills.canLearn"), set.slots.map { it.path })
    }

    @Test
    fun everyProblemOfEverySlotIsReportedOnceWithItsPath() {
        val result = FormulaSet.Builder().run {
            formula("attackRange", "1.5", hit)
            formula("combat.chances.miss", "0.05 + defence / 1000", hit)
            formula("combat.damage", "atk * 2 - def + critical", hit)
            condition("combat.canFlee", "level", level)
            compile()
        }

        val invalid = assertIs<FormulaSetResult.Invalid>(result)
        assertEquals(
            listOf(
                "combat.chances.miss" to FormulaProblemKind.UnknownVariable,
                "combat.damage" to FormulaProblemKind.TypeMismatch,
                "combat.canFlee" to FormulaProblemKind.TypeMismatch,
            ),
            invalid.problems.map { it.path to it.kind },
        )
        val miss = invalid.problems[0]
        assertEquals("0.05 + defence / 1000", miss.source)
        assertEquals(7, miss.start)
        assertEquals(14, miss.end)
        assertEquals(1, miss.line)
        assertEquals(8, miss.column)
        assertEquals("unknown variable 'defence'; did you mean 'def'?", miss.message)
        assertEquals(
            "combat.chances.miss: '0.05 + defence / 1000': 1:8: unknown variable 'defence'; did you mean 'def'?",
            miss.toString(),
        )
    }

    @Test
    fun theThrowingFormListsEveryProblemOnItsOwnLine() {
        val error = assertFailsWith<FormulaSetException> {
            formulaSet {
                formula("swingMillis", "1000 - spd", hit)
                formula("damage", "max(atk)", hit)
                formula("threat", "damage * 2 +", hit)
            }
        }

        assertEquals(3, error.problems.size)
        assertEquals(
            """
            Invalid formulas:
            - swingMillis: '1000 - spd': 1:8: unknown variable 'spd'; the variables are atk, def, critical
            - damage: 'max(atk)': 1:1: max takes at least 2 arguments, not 1: max(a, b, ...)
            - threat: 'damage * 2 +': 1:13: the formula ends early; expected a value
            """.trimIndent(),
            error.message,
        )
        assertIs<IllegalArgumentException>(error)
    }

    @Test
    fun randomIsRejectedWhenTheSlotHasNoRandomSource() {
        val result = FormulaSet.Builder().run {
            formula("damage", "atk * random(0.9, 1.1)", hit)
            compile()
        }

        val problem = result.problems.single()
        assertEquals(FormulaProblemKind.RandomUnavailable, problem.kind)
        assertEquals("damage", problem.path)
        assertEquals(6, problem.start)
        assertEquals(22, problem.end)
        assertEquals("random() cannot be used here: nothing gives this formula a random source", problem.message)
    }

    @Test
    fun randomIsAcceptedWhenTheSlotHasARandomSource() {
        val builder = FormulaSet.Builder()
        val damage = builder.formula("damage", "atk * random(0.9, 1.1)", hit, random = true)
        val drops = builder.condition("drops", "random() < 0.25", random = true)
        val set = builder.build()

        assertTrue(set[damage].usesRandom)
        assertTrue(set[drops].usesRandom)
        val inputs = FormulaInputs(hit, FormulaRandom.seeded(7))
        inputs[hit["atk"]] = 100.0
        val value = set[damage].evaluate(inputs)
        assertTrue(value >= 90.0 && value < 110.0, "$value")
    }

    @Test
    fun conditionSlotsCompileAndReportLikeFormulaSlots() {
        val builder = FormulaSet.Builder()
        val usable = builder.condition("usableWhen", "level >= 10 and not false", level)
        val set = builder.build()
        val inputs = FormulaInputs(level)
        inputs[level["level"]] = 9.0
        assertEquals(false, set[usable].test(inputs))

        val problems = FormulaSet.Builder().run {
            condition("usableWhen", "level + 1", level)
            condition("visibleWhen", "lvel > 2", level)
            compile().problems
        }
        assertEquals(listOf("usableWhen", "visibleWhen"), problems.map { it.path })
        assertEquals("the formula gives a number, but a condition must give true or false", problems[0].message)
        assertEquals("unknown variable 'lvel'; did you mean 'level'?", problems[1].message)
    }

    @Test
    fun eachSlotKeepsItsOwnOptions() {
        val strict = FormulaOptions(undefinedMath = UndefinedMath.Error)
        val problems = FormulaSet.Builder().run {
            formula("lenient", "1 / 0")
            formula("strict", "1 / 0", options = strict)
            compile().problems
        }
        assertEquals(listOf("strict" to FormulaProblemKind.Undefined), problems.map { it.path to it.kind })
    }

    @Test
    fun aPathIsUsedOnceAndASlotBelongsToItsSet() {
        val builder = FormulaSet.Builder()
        builder.formula("damage", "1")
        assertFailsWith<IllegalArgumentException> { builder.formula("damage", "2") }
        assertFailsWith<IllegalArgumentException> { builder.condition(" ", "true") }

        val other = FormulaSet.Builder()
        val foreign = other.formula("damage", "1")
        val set = builder.build()
        assertFailsWith<IllegalArgumentException> { set[foreign] }
        assertEquals(
            "No formula slot 'threat'; the slots are damage",
            assertFailsWith<IllegalArgumentException> { set.formula("threat") }.message,
        )
        assertEquals(
            "The formula slot 'damage' gives a number, not true or false",
            assertFailsWith<IllegalArgumentException> { set.condition("damage") }.message,
        )
    }

    @Test
    fun aLongSourceIsShortenedInMessages() {
        val source = "1 + ".repeat(30) + "missing"
        val problem = FormulaSet.Builder().run {
            formula("long", source)
            compile().problems.single()
        }
        assertEquals("long: '${source.take(80)}...': 1:121: unknown variable 'missing'; no variables are declared", problem.toString())
    }
}
