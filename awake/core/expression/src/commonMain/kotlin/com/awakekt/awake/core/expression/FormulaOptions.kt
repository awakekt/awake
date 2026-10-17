/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * What an operation with no finite answer gives: `x / 0`, `x % 0`, `sqrt` of a negative number, `log` of
 * zero or less, a negative number to a fractional power, or a result too large for a double.
 */
enum class UndefinedMath {
    /** The operation gives 0 and evaluation goes on. */
    Zero,

    /**
     * Evaluation throws [FormulaEvaluationException] naming the operation and where it is. A part of the
     * formula that is undefined whatever the inputs, such as `1 / 0`, is rejected when it is compiled.
     */
    Error,
}

/**
 * Limits and policy for compiling a formula. A formula has no loops, so it runs at most one step per part:
 * [maxNodes] is its step limit.
 *
 * @property maxLength The most characters a formula may have.
 * @property maxDepth How deeply parentheses, calls, `not`, signs and powers may nest.
 * @property maxNodes The most parts (numbers, names, operators and calls) a formula may have.
 * @property undefinedMath What an operation with no finite answer gives.
 */
class FormulaOptions(
    val maxLength: Int = DEFAULT_MAX_LENGTH,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val maxNodes: Int = DEFAULT_MAX_NODES,
    val undefinedMath: UndefinedMath = UndefinedMath.Zero,
) {
    init {
        require(maxLength > 0) { "maxLength must be positive, was $maxLength" }
        require(maxDepth > 0) { "maxDepth must be positive, was $maxDepth" }
        require(maxNodes > 0) { "maxNodes must be positive, was $maxNodes" }
    }

    override fun equals(other: Any?): Boolean =
        other is FormulaOptions &&
            other.maxLength == maxLength &&
            other.maxDepth == maxDepth &&
            other.maxNodes == maxNodes &&
            other.undefinedMath == undefinedMath

    override fun hashCode(): Int = ((maxLength * 31 + maxDepth) * 31 + maxNodes) * 31 + undefinedMath.hashCode()

    override fun toString(): String =
        "FormulaOptions(maxLength=$maxLength, maxDepth=$maxDepth, maxNodes=$maxNodes, undefinedMath=$undefinedMath)"

    /** The default limits. */
    companion object {
        /** The default [maxLength]. */
        const val DEFAULT_MAX_LENGTH: Int = 1024

        /** The default [maxDepth]. */
        const val DEFAULT_MAX_DEPTH: Int = 32

        /** The default [maxNodes]. */
        const val DEFAULT_MAX_NODES: Int = 512

        /** The default limits, with undefined operations giving 0. */
        val Default: FormulaOptions = FormulaOptions()
    }
}
