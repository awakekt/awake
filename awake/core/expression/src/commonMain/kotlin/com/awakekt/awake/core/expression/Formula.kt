/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * A number computed from [FormulaInputs]: damage from attack and defence, experience for a level, a price,
 * a drop weight. A plugin takes a `Formula` wherever it needs such a number. A game supplies it as data,
 * a string compiled with [compile], or as code, a Kotlin implementation of this interface.
 *
 * ```kotlin
 * val damage = Formula.compile("max(1, atk * 2 - def)", combat)
 * val inputs = FormulaInputs(combat).apply { this[atk] = 30.0; this[def] = 12.0 }
 * damage.evaluate(inputs) // 48.0
 * ```
 */
fun interface Formula {
    /** The number for [inputs]. */
    fun evaluate(inputs: FormulaInputs): Double

    /** Compiling and checking formulas from text. */
    companion object {
        /**
         * Compiles [source] into a formula that reads [variables]. Compile once and evaluate many times.
         *
         * @throws FormulaException when [source] is not a valid formula giving a number: bad syntax, an
         *   unknown variable or function, a wrong argument count or type, or a limit in [options] passed.
         */
        fun compile(
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            options: FormulaOptions = FormulaOptions.Default,
        ): CompiledFormula {
            val compilation = Compiler(source, variables, options).compile(FormulaType.Number)
            val root = compilation.number ?: throw FormulaException(source, compilation.problems)
            return CompiledFormula(source, variables, options, compilation, root)
        }

        /**
         * Everything wrong with [source] as a formula giving a number, in source order, without throwing:
         * what an editor underlines while a formula is typed. Empty when it compiles.
         */
        fun check(
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            options: FormulaOptions = FormulaOptions.Default,
        ): List<FormulaProblem> = Compiler(source, variables, options).compile(FormulaType.Number).problems

        /** A formula that always gives [value]. */
        fun constant(value: Double): Formula = Formula { value }
    }
}

/**
 * A true-or-false test on [FormulaInputs]: whether a skill may be used, whether an item drops. Like
 * [Formula], a game supplies it as data with [compile] or as code.
 */
fun interface FormulaCondition {
    /** Whether the condition holds for [inputs]. */
    fun test(inputs: FormulaInputs): Boolean

    /** Compiling and checking conditions from text. */
    companion object {
        /**
         * Compiles [source] into a condition that reads [variables].
         *
         * @throws FormulaException when [source] is not a valid formula giving true or false.
         */
        fun compile(
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            options: FormulaOptions = FormulaOptions.Default,
        ): CompiledCondition {
            val compilation = Compiler(source, variables, options).compile(FormulaType.Boolean)
            val root = compilation.boolean ?: throw FormulaException(source, compilation.problems)
            return CompiledCondition(source, variables, options, compilation, root)
        }

        /** Everything wrong with [source] as a condition, in source order, without throwing. */
        fun check(
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            options: FormulaOptions = FormulaOptions.Default,
        ): List<FormulaProblem> = Compiler(source, variables, options).compile(FormulaType.Boolean).problems
    }
}

/**
 * A formula compiled from text: checked, with its constant parts folded, ready to evaluate. It is
 * immutable, so one instance may be evaluated from many threads, each with its own [FormulaInputs].
 *
 * Two compiled formulas are equal when their [source], [variables] and [options] are.
 *
 * @property source The text it was compiled from; what a serializer writes.
 * @property variables The declaration it reads; [FormulaInputs] must be made from an equal one.
 * @property options The limits and policy it was compiled with.
 * @property usedVariables The variables it reads, in order of first appearance.
 * @property usesRandom Whether it calls `random()`, and so needs [FormulaInputs.random].
 */
sealed class CompiledExpression(
    val source: String,
    val variables: FormulaVariables,
    val options: FormulaOptions,
    val usedVariables: List<FormulaVariable>,
    val usesRandom: Boolean,
) {
    /** What it gives: a number for [CompiledFormula], true or false for [CompiledCondition]. */
    abstract val type: FormulaType

    internal fun checkInputs(inputs: FormulaInputs) {
        if (inputs.variables !== variables) {
            require(inputs.variables == variables) {
                "These inputs hold ${inputs.variables}, but '$source' was compiled for $variables"
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CompiledExpression &&
            other.type == type &&
            other.source == source &&
            other.variables == variables &&
            other.options == options

    override fun hashCode(): Int = (source.hashCode() * 31 + variables.hashCode()) * 31 + type.hashCode()

    /** The [source]. */
    override fun toString(): String = source
}

/** A [Formula] compiled from text by [Formula.compile]. */
class CompiledFormula internal constructor(
    source: String,
    variables: FormulaVariables,
    options: FormulaOptions,
    compilation: Compilation,
    private val root: NumberNode,
) : CompiledExpression(source, variables, options, compilation.usedVariables, compilation.usesRandom),
    Formula {
    override val type: FormulaType get() = FormulaType.Number

    /**
     * The number for [inputs]. Allocates nothing.
     *
     * @throws IllegalArgumentException when [inputs] are for different variables.
     * @throws FormulaEvaluationException under [UndefinedMath.Error] when an operation has no finite answer,
     *   or when `random()` runs without [FormulaInputs.random].
     */
    override fun evaluate(inputs: FormulaInputs): Double {
        checkInputs(inputs)
        return root.evaluate(inputs)
    }

    /** The constant value when the formula reads no variables and no random source, or null. */
    val constantValue: Double? get() = (root as? Constant)?.value
}

/** A [FormulaCondition] compiled from text by [FormulaCondition.compile]. */
class CompiledCondition internal constructor(
    source: String,
    variables: FormulaVariables,
    options: FormulaOptions,
    compilation: Compilation,
    private val root: BooleanNode,
) : CompiledExpression(source, variables, options, compilation.usedVariables, compilation.usesRandom),
    FormulaCondition {
    override val type: FormulaType get() = FormulaType.Boolean

    /**
     * Whether the condition holds for [inputs]. Allocates nothing.
     *
     * @throws IllegalArgumentException when [inputs] are for different variables.
     * @throws FormulaEvaluationException under [UndefinedMath.Error] when an operation has no finite answer,
     *   or when `random()` runs without [FormulaInputs.random].
     */
    override fun test(inputs: FormulaInputs): Boolean {
        checkInputs(inputs)
        return root.test(inputs)
    }
}
