/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Constant folding: the value of an expression whose operands are all known before the shader runs.
//
// WGSL evaluates such an expression when the shader is created, and a NaN or an infinity there is a
// shader-creation error. A document folds every such expression here first: the checker rejects a
// value that is not finite, naming where it is, and the lowering writes the folded value as a literal,
// so no expression the shader compiler could fold is left for it to fail on. Conditions are not
// folded; they are always finite.

/** `a op b`, component by component, a scalar applying to every component of the other side. */
internal fun foldArithmetic(node: ShaderExpr.Arithmetic, a: FloatArray, b: FloatArray): FloatArray = when (node) {
    is ShaderExpr.Add -> zipComponents(a, b) { x, y -> x + y }
    is ShaderExpr.Sub -> zipComponents(a, b) { x, y -> x - y }
    is ShaderExpr.Mul -> zipComponents(a, b) { x, y -> x * y }
    is ShaderExpr.Div -> zipComponents(a, b) { x, y -> x / y }
}

/** `-value`. */
internal fun foldNegate(value: FloatArray): FloatArray = FloatArray(value.size) { -value[it] }

/** The components [components] picks from [value]. */
internal fun foldSwizzle(value: FloatArray, components: String): FloatArray {
    val indices = swizzleIndices(components)
    return FloatArray(indices.size) { value[indices[it]] }
}

/** A vector of [size] from [parts], or one scalar repeated. */
internal fun foldConstruct(parts: List<FloatArray>, size: Int): FloatArray {
    val flat = parts.flatMap { it.asList() }
    return if (flat.size == 1) FloatArray(size) { flat[0] } else flat.toFloatArray()
}

/** [fn] applied to [args], or null for [ShaderFunction.Select], whose condition is never folded. */
@Suppress("CyclomaticComplexMethod") // One branch per builtin.
internal fun foldCall(fn: ShaderFunction, args: List<FloatArray>): FloatArray? = when (fn) {
    ShaderFunction.Sin -> mapComponents(args[0], ::sin)
    ShaderFunction.Cos -> mapComponents(args[0], ::cos)
    ShaderFunction.Abs -> mapComponents(args[0], ::abs)
    ShaderFunction.Floor -> mapComponents(args[0], ::floor)
    ShaderFunction.Fract -> mapComponents(args[0]) { it - floor(it) }
    ShaderFunction.Sqrt -> mapComponents(args[0], ::sqrt)
    ShaderFunction.Exp -> mapComponents(args[0], ::exp)
    ShaderFunction.Saturate -> mapComponents(args[0]) { it.coerceIn(0f, 1f) }
    ShaderFunction.Normalize -> lengthOf(args[0]).let { l -> mapComponents(args[0]) { it / l } }
    ShaderFunction.Length -> floatArrayOf(lengthOf(args[0]))
    ShaderFunction.Dot -> floatArrayOf(dotOf(args[0], args[1]))
    ShaderFunction.Cross -> crossOf(args[0], args[1])
    ShaderFunction.Min -> zipComponents(args[0], args[1], ::min)
    ShaderFunction.Max -> zipComponents(args[0], args[1], ::max)
    ShaderFunction.Pow -> zipComponents(args[0], args[1]) { x, y -> x.pow(y) }
    ShaderFunction.Step -> zipComponents(args[0], args[1]) { edge, x -> if (edge <= x) 1f else 0f }
    ShaderFunction.Clamp -> zipComponents(zipComponents(args[0], args[1], ::max), args[2], ::min)
    ShaderFunction.Smoothstep -> smoothstepOf(args[0], args[1], args[2])
    ShaderFunction.Mix -> mixOf(args[0], args[1], args[2])
    ShaderFunction.Select -> null
}

/** Whether every component is a finite number. */
internal fun FloatArray.allFinite(): Boolean = all { it.isFinite() }
