/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

// The checked tree a compiled formula runs. Every node is immutable and reads only its inputs, so one
// tree is safe to evaluate from many threads, and evaluating allocates nothing. Every value is finite:
// inputs are checked when they are set, and an operation that could leave the finite range asks its
// Site what to give instead.

internal abstract class NumberNode {
    abstract fun evaluate(inputs: FormulaInputs): Double
}

internal abstract class BooleanNode {
    abstract fun test(inputs: FormulaInputs): Boolean
}

/** Where an operation is in the source, and what it gives when it has no finite answer. */
internal class Site(
    private val operation: String,
    private val start: Int,
    private val end: Int,
    private val line: Int,
    private val column: Int,
    private val policy: UndefinedMath,
) {
    fun undefined(reason: String): Double = when (policy) {
        UndefinedMath.Zero -> 0.0
        UndefinedMath.Error -> throw error("$operation $reason")
    }

    fun finite(value: Double): Double = if (value.isFinite()) value else undefined("overflows")

    fun error(reason: String): FormulaEvaluationException = FormulaEvaluationException(reason, start, end, line, column)
}

internal class Constant(val value: Double) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = value
}

internal class Variable(private val index: Int) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = inputs.values[index]
}

internal class Negate(private val operand: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = -operand.evaluate(inputs)
}

internal class Add(private val left: NumberNode, private val right: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = site.finite(left.evaluate(inputs) + right.evaluate(inputs))
}

internal class Subtract(private val left: NumberNode, private val right: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = site.finite(left.evaluate(inputs) - right.evaluate(inputs))
}

internal class Multiply(private val left: NumberNode, private val right: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = site.finite(left.evaluate(inputs) * right.evaluate(inputs))
}

internal class Divide(private val left: NumberNode, private val right: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val dividend = left.evaluate(inputs)
        val divisor = right.evaluate(inputs)
        return if (divisor == 0.0) site.undefined("divides by zero") else site.finite(dividend / divisor)
    }
}

/** The remainder of truncated division: it takes the sign of the dividend, so `-7 % 3` is -1. */
internal class Remainder(private val left: NumberNode, private val right: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val dividend = left.evaluate(inputs)
        val divisor = right.evaluate(inputs)
        return if (divisor == 0.0) site.undefined("divides by zero") else dividend % divisor
    }
}

internal class Power(private val base: NumberNode, private val exponent: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val x = base.evaluate(inputs)
        val result = DeterministicMath.pow(x, exponent.evaluate(inputs))
        return when {
            result.isNaN() -> site.undefined("of a negative number to a fractional power is undefined")
            result.isInfinite() && x == 0.0 -> site.undefined("of zero to a negative power divides by zero")
            else -> site.finite(result)
        }
    }
}

internal class NumberIf(
    private val condition: BooleanNode,
    private val then: NumberNode,
    private val otherwise: NumberNode,
) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double =
        if (condition.test(inputs)) then.evaluate(inputs) else otherwise.evaluate(inputs)
}

internal class Min(private val arguments: Array<NumberNode>) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        var result = arguments[0].evaluate(inputs)
        for (i in 1 until arguments.size) {
            val value = arguments[i].evaluate(inputs)
            if (value < result) result = value
        }
        return result
    }
}

internal class Max(private val arguments: Array<NumberNode>) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        var result = arguments[0].evaluate(inputs)
        for (i in 1 until arguments.size) {
            val value = arguments[i].evaluate(inputs)
            if (value > result) result = value
        }
        return result
    }
}

internal class Clamp(private val x: NumberNode, private val low: NumberNode, private val high: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val value = x.evaluate(inputs)
        val lowest = low.evaluate(inputs)
        val highest = high.evaluate(inputs)
        return when {
            value > highest -> if (highest < lowest) lowest else highest
            value < lowest -> lowest
            else -> value
        }
    }
}

internal class Floor(private val x: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = floor(x.evaluate(inputs))
}

internal class Ceil(private val x: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = ceil(x.evaluate(inputs))
}

internal class Round(private val x: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = DeterministicMath.round(x.evaluate(inputs))
}

internal class Abs(private val x: NumberNode) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double = abs(x.evaluate(inputs))
}

internal class Sqrt(private val x: NumberNode, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val value = x.evaluate(inputs)
        return if (value < 0.0) site.undefined("of a negative number is undefined") else sqrt(value)
    }
}

internal class Log(private val x: NumberNode, private val base: NumberNode?, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val value = x.evaluate(inputs)
        val b = base?.evaluate(inputs)
        return when {
            value <= 0.0 -> site.undefined("of zero or a negative number is undefined")
            b == null -> DeterministicMath.log(value)
            b <= 0.0 || b == 1.0 -> site.undefined("to a base of zero or less, or of 1, is undefined")
            else -> site.finite(DeterministicMath.log(value, b))
        }
    }
}

internal class Lerp(
    private val a: NumberNode,
    private val b: NumberNode,
    private val t: NumberNode,
    private val site: Site,
) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val from = a.evaluate(inputs)
        return site.finite(from + (b.evaluate(inputs) - from) * t.evaluate(inputs))
    }
}

/** `random()` and `random(low, high)`; it reads the inputs' source, so it is never folded into a constant. */
internal class RandomNumber(private val low: NumberNode?, private val high: NumberNode?, private val site: Site) : NumberNode() {
    override fun evaluate(inputs: FormulaInputs): Double {
        val lowest = low?.evaluate(inputs) ?: 0.0
        val highest = high?.evaluate(inputs) ?: 1.0
        val source = inputs.random ?: throw site.error("random() needs a random source: set FormulaInputs.random")
        val unit = source.nextDouble()
        if (!(unit >= 0.0 && unit < 1.0)) throw site.error("random source gave a number outside 0 until 1")
        return if (low == null) unit else site.finite(lowest + (highest - lowest) * unit)
    }
}

internal class BooleanConstant(val value: Boolean) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = value
}

internal class BooleanVariable(private val index: Int) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = inputs.values[index] != 0.0
}

internal class Not(private val operand: BooleanNode) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = !operand.test(inputs)
}

internal class And(private val left: BooleanNode, private val right: BooleanNode) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = left.test(inputs) && right.test(inputs)
}

internal class Or(private val left: BooleanNode, private val right: BooleanNode) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = left.test(inputs) || right.test(inputs)
}

internal class Compare(
    private val operator: BinaryOperator,
    private val left: NumberNode,
    private val right: NumberNode,
) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean {
        val a = left.evaluate(inputs)
        val b = right.evaluate(inputs)
        return when (operator) {
            BinaryOperator.Equal -> a == b
            BinaryOperator.NotEqual -> a != b
            BinaryOperator.Less -> a < b
            BinaryOperator.LessOrEqual -> a <= b
            BinaryOperator.Greater -> a > b
            BinaryOperator.GreaterOrEqual -> a >= b
            else -> error("$operator is not a comparison")
        }
    }
}

internal class BooleanEqual(
    private val left: BooleanNode,
    private val right: BooleanNode,
    private val equal: Boolean,
) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean = (left.test(inputs) == right.test(inputs)) == equal
}

internal class BooleanIf(
    private val condition: BooleanNode,
    private val then: BooleanNode,
    private val otherwise: BooleanNode,
) : BooleanNode() {
    override fun test(inputs: FormulaInputs): Boolean =
        if (condition.test(inputs)) then.test(inputs) else otherwise.test(inputs)
}
