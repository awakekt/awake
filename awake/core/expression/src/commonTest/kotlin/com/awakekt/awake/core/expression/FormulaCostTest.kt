/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.TimeSource

/**
 * The cost of one evaluation, measured on whatever target runs the test. The bound is loose, far above what
 * any target needs, so the test catches an accidental slowdown by orders of magnitude (an allocation or a
 * re-parse per call), not machine noise. The measured cost is printed for the record.
 */
class FormulaCostTest {
    private val combat = formulaVariables {
        number("atk")
        number("def")
        number("level")
        boolean("critical")
    }

    @Test
    fun aDamageFormulaCostsWellUnderAMicrosecondPerHit() {
        val damage = Formula.compile(
            "max(1, atk * (1.5 + level / 20) - def * 0.5) * if(critical, 1.75, 1) + floor(level ^ 1.2) % 7",
            combat,
        )
        val atk = combat["atk"]
        val def = combat["def"]
        val level = combat["level"]
        val critical = combat["critical"]
        val inputs = FormulaInputs(combat)

        fun run(count: Int): Double {
            var sum = 0.0
            for (i in 0 until count) {
                inputs[atk] = (i % 97).toDouble()
                inputs[def] = (i % 31).toDouble()
                inputs[level] = (i % 60 + 1).toDouble()
                inputs[critical] = i % 5 == 0
                sum += damage.evaluate(inputs)
            }
            return sum
        }

        run(WARM_UP)
        val start = TimeSource.Monotonic.markNow()
        val sum = run(MEASURED)
        val perEvaluation = start.elapsedNow() / MEASURED
        println("FormulaCostTest: ${perEvaluation.inWholeNanoseconds} ns per evaluation (checksum $sum)")
        assertTrue(perEvaluation < 20.microseconds, "one evaluation took $perEvaluation")
    }

    @Test
    fun compilingIsCheapEnoughForLoadTime() {
        val start = TimeSource.Monotonic.markNow()
        repeat(1000) { Formula.compile("max(1, atk * (1.5 + level / 20) - def) * if(critical, 1.75, 1)", combat) }
        val perCompile = start.elapsedNow() / 1000
        println("FormulaCostTest: ${perCompile.inWholeMicroseconds} us per compile")
        assertTrue(perCompile < 2000.microseconds, "one compile took $perCompile")
        assertEquals(0, Formula.check("atk", combat).size)
    }

    private companion object {
        const val WARM_UP = 50_000
        const val MEASURED = 200_000
    }
}
