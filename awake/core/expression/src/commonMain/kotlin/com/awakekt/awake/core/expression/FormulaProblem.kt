/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/** What is wrong with a formula. */
enum class FormulaProblemKind {
    /** It does not follow the grammar: a missing operator, an unclosed parenthesis, a stray character. */
    Syntax,

    /** It has more characters than [FormulaOptions.maxLength]. */
    TooLong,

    /** It nests more deeply than [FormulaOptions.maxDepth]. */
    TooDeep,

    /** It has more parts than [FormulaOptions.maxNodes]. */
    TooLarge,

    /** It names a variable that is not declared. */
    UnknownVariable,

    /** It calls a function that is not one of [FormulaFunction]. */
    UnknownFunction,

    /** It calls a function with too few or too many arguments. */
    ArgumentCount,

    /** It uses a number where true or false is needed, or the other way round. */
    TypeMismatch,

    /** A part that does not depend on the inputs has no finite answer, under [UndefinedMath.Error]. */
    Undefined,
}

/**
 * One thing wrong with a formula, and where, so an editor can underline it.
 *
 * @property kind What is wrong.
 * @property message A sentence saying what is wrong, for a person.
 * @property start The offset of the first character it covers.
 * @property end The offset after the last character it covers; equal to [start] at the end of the formula.
 * @property line The 1-based line [start] is on.
 * @property column The 1-based column of [start] on its line.
 */
class FormulaProblem(
    val kind: FormulaProblemKind,
    val message: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
) {
    /** `line:column: message`. */
    override fun toString(): String = "$line:$column: $message"
}

/**
 * Thrown when a formula cannot be compiled.
 *
 * @property source The formula's text.
 * @property problems Everything found wrong with it, in source order; at least one.
 */
class FormulaException(
    val source: String,
    val problems: List<FormulaProblem>,
) : IllegalArgumentException(describe(source, problems)) {
    private companion object {
        fun describe(source: String, problems: List<FormulaProblem>): String =
            "Invalid formula '${source.take(PREVIEW_LENGTH)}${if (source.length > PREVIEW_LENGTH) "..." else ""}': " +
                problems.joinToString("; ")

        const val PREVIEW_LENGTH = 80
    }
}

/**
 * Thrown while a formula is evaluated: under [UndefinedMath.Error] when an operation has no finite answer,
 * or when `random()` runs without a [FormulaInputs.random] source. Its message is `line:column: reason`.
 */
class FormulaEvaluationException internal constructor(
    internal val reason: String,
    /** The offset of the first character of the operation. */
    val start: Int,
    /** The offset after the last character of the operation. */
    val end: Int,
    /** The 1-based line the operation starts on. */
    val line: Int,
    /** The 1-based column the operation starts at. */
    val column: Int,
) : RuntimeException("$line:$column: $reason")
