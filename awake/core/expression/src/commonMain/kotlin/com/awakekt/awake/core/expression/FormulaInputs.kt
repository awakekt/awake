/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * The values one evaluation reads: a slot for each of [variables], 0 or false until set, and the
 * [random] source `random()` draws from.
 *
 * Keep one and set its values before each evaluation; evaluating reads it without allocating. It is not
 * safe to write from one thread while another evaluates with it.
 *
 * @property variables The declaration these inputs hold values for.
 * @property random Where `random()` draws from, or null when no formula evaluated with these inputs may
 *   use it; evaluating `random()` without one throws [FormulaEvaluationException].
 */
class FormulaInputs(
    val variables: FormulaVariables,
    var random: FormulaRandom? = null,
) {
    internal val values = DoubleArray(variables.size)

    /**
     * Sets the number [variable] to [value].
     *
     * @throws IllegalArgumentException when [variable] is not one of [variables], holds true or false, or
     *   [value] is not finite.
     */
    operator fun set(variable: FormulaVariable, value: Double) {
        val index = slot(variable, FormulaType.Number)
        require(value.isFinite()) { "Formula variable '${variable.name}' must be finite, was $value" }
        values[index] = value
    }

    /** Sets the number [variable] to [value]; see [set]. */
    operator fun set(variable: FormulaVariable, value: Int) {
        set(variable, value.toDouble())
    }

    /**
     * Sets the true-or-false [variable] to [value].
     *
     * @throws IllegalArgumentException when [variable] is not one of [variables] or holds a number.
     */
    operator fun set(variable: FormulaVariable, value: Boolean) {
        values[slot(variable, FormulaType.Boolean)] = if (value) 1.0 else 0.0
    }

    /** Sets the number variable called [name]; a convenience for tools, slower than setting a [FormulaVariable]. */
    operator fun set(name: String, value: Double) {
        set(variables[name], value)
    }

    /** Sets the true-or-false variable called [name]; a convenience for tools. */
    operator fun set(name: String, value: Boolean) {
        set(variables[name], value)
    }

    /**
     * The value of the number [variable].
     *
     * @throws IllegalArgumentException when [variable] is not one of [variables] or holds true or false.
     */
    operator fun get(variable: FormulaVariable): Double = values[slot(variable, FormulaType.Number)]

    /**
     * The value of the true-or-false [variable].
     *
     * @throws IllegalArgumentException when [variable] is not one of [variables] or holds a number.
     */
    fun boolean(variable: FormulaVariable): Boolean = values[slot(variable, FormulaType.Boolean)] != 0.0

    /** Sets every variable back to 0 or false. [random] is kept. */
    fun clear() {
        values.fill(0.0)
    }

    private fun slot(variable: FormulaVariable, type: FormulaType): Int {
        require(variables.owns(variable)) { "Formula variable '${variable.name}' is not declared in $variables" }
        require(variable.type == type) { "Formula variable '${variable.name}' holds ${variable.type.label}, not ${type.label}" }
        return variable.index
    }
}
