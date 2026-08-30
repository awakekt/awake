/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.exp as mathExp
import kotlin.math.floor as mathFloor

// Value-level operations behind AslEvaluator -- split out only for the per-file budget.

internal fun binaryValues(op: String, left: FloatArray, right: FloatArray): FloatArray {
    fun at(values: FloatArray, i: Int) = if (values.size == 1) values[0] else values[i]
    val compared = compareValues(op, left, right)
    if (compared != null) return compared
    return FloatArray(maxOf(left.size, right.size)) {
        val a = at(left, it)
        val b = at(right, it)
        when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            else -> a / b
        }
    }
}

private fun compareValues(op: String, left: FloatArray, right: FloatArray): FloatArray? {
    val compare: ((Float, Float) -> Boolean) = when (op) {
        "<" -> { a, b -> a < b }
        "<=" -> { a, b -> a <= b }
        ">" -> { a, b -> a > b }
        ">=" -> { a, b -> a >= b }
        "==" -> { a, b -> a == b }
        "!=" -> { a, b -> a != b }
        "||" -> { a, b -> a != 0f || b != 0f }
        "&&" -> { a, b -> a != 0f && b != 0f }
        else -> return null
    }
    return floatArrayOf(if (compare(left[0], right[0])) 1f else 0f)
}

private fun zip2(a: FloatArray, b: FloatArray, op: (Float, Float) -> Float) =
    FloatArray(maxOf(a.size, b.size)) {
        op(if (a.size == 1) a[0] else a[it], if (b.size == 1) b[0] else b[it])
    }

@Suppress("CyclomaticComplexMethod")
internal fun builtinCall(function: String, args: List<FloatArray>): FloatArray = when (function) {
    "normalize" -> {
        val v = args[0]
        val length = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        FloatArray(v.size) { v[it] / length }
    }
    "dot" -> floatArrayOf(args[0].zip(args[1]) { a, b -> a * b }.sum())
    "max" -> zip2(args[0], args[1]) { a, b -> maxOf(a, b) }
    "floor" -> FloatArray(args[0].size) { mathFloor(args[0][it]) }
    "fract" -> FloatArray(args[0].size) { args[0][it] - mathFloor(args[0][it]) }
    "mix" -> {
        val (a, b, t) = args
        FloatArray(a.size) { i ->
            val blend = if (t.size == 1) t[0] else t[i]
            a[i] * (1f - blend) + b[i] * blend
        }
    }
    "pow" -> zip2(args[0], args[1]) { a, b -> a.pow(b) }
    "clamp" -> FloatArray(args[0].size) { i ->
        val low = if (args[1].size == 1) args[1][0] else args[1][i]
        val high = if (args[2].size == 1) args[2][0] else args[2][i]
        args[0][i].coerceIn(low, high)
    }
    "length" -> floatArrayOf(sqrt(args[0].sumOf { (it * it).toDouble() }).toFloat())
    "exp" -> FloatArray(args[0].size) { mathExp(args[0][it]) }
    "saturate" -> FloatArray(args[0].size) { args[0][it].coerceIn(0f, 1f) }
    "select" -> if (args[2][0] != 0f) args[1] else args[0]
    "f32" -> args[0]
    "textureSampleLevel", "textureDimensions" ->
        throw AslDefinitionException("Evaluator has no texture support; probe math around it.")
    else -> throw AslDefinitionException("Evaluator has no builtin '$function'.")
}

/** Splat `vec3f(1.0)` expands the one scalar; anything else concatenates as written. */
internal fun constructValues(expr: AslConstruct): List<AslExpr> =
    if (expr.args.size == 1 && expr.args[0].type == F32 && expr.shape.componentCount > 1) {
        List(expr.shape.componentCount) { expr.args[0] }
    } else {
        expr.args
    }
