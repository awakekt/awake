/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

/**
 * WGSL `normalize` builtin.
 *
 * @param value The expression to normalize.
 * @return The normalized expression.
 */
fun normalize(value: AslExpr): AslExpr = AslCall("normalize", listOf(value), value.type)

/**
 * WGSL `dot` builtin.
 *
 * @param a First vector.
 * @param b Second vector.
 * @return The scalar dot product.
 */
fun dot(a: AslExpr, b: AslExpr): AslExpr {
    if (a.type != b.type || a.type == F32 || a.type.dataShapeOrNull() == null) {
        throw AslDefinitionException("dot needs two vectors of one shape, got ${a.type}/${b.type}.")
    }
    return AslCall("dot", listOf(a, b), F32)
}

/**
 * WGSL `max` builtin.
 *
 * @param a First operand.
 * @param b Second operand.
 * @return The maximum value.
 */
fun max(a: AslExpr, b: AslExpr): AslExpr = AslCall("max", listOf(a, b), AslBinary("+", a, b).type)

/** WGSL `min` builtin. */
fun min(a: AslExpr, b: AslExpr): AslExpr = AslCall("min", listOf(a, b), AslBinary("+", a, b).type)

/** WGSL `abs` builtin. */
fun abs(value: AslExpr): AslExpr = AslCall("abs", listOf(value), value.type)

/**
 * WGSL `floor` builtin.
 *
 * @param value The expression to floor.
 * @return The floored expression.
 */
fun floor(value: AslExpr): AslExpr = AslCall("floor", listOf(value), value.type)

/**
 * WGSL `fract` builtin.
 *
 * @param value The expression to fract.
 * @return The fractional part of the expression.
 */
fun fract(value: AslExpr): AslExpr = AslCall("fract", listOf(value), value.type)

/**
 * WGSL `mix` builtin -- `mix(a, b, t)`.
 *
 * @param a First operand.
 * @param b Second operand.
 * @param t Blend factor (matching vector or scalar).
 * @return The blended value.
 */
fun mix(a: AslExpr, b: AslExpr, t: AslExpr): AslExpr {
    if (a.type != b.type || (t.type != a.type && t.type != F32)) {
        throw AslDefinitionException("mix types ${a.type}/${b.type}/${t.type} do not combine.")
    }
    return AslCall("mix", listOf(a, b, t), a.type)
}
