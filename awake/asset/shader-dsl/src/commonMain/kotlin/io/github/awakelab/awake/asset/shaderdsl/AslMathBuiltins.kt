/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

// More WGSL math builtins -- split from AslBuiltins.kt only for the per-file function budget.

/**
 * WGSL `pow` builtin.
 *
 * @param base The base value.
 * @param exponent The exponent value.
 * @return The result of base raised to the power of exponent.
 */
fun pow(base: AslExpr, exponent: AslExpr): AslExpr =
    AslCall("pow", listOf(base, exponent), AslBinary("+", base, exponent).type)

/**
 * WGSL `clamp` builtin.
 *
 * @param value The value to clamp.
 * @param low The lower bound.
 * @param high The upper bound.
 * @return The clamped value.
 */
fun clamp(value: AslExpr, low: AslExpr, high: AslExpr): AslExpr =
    AslCall("clamp", listOf(value, low, high), value.type)

/**
 * WGSL `length` builtin.
 *
 * @param value The vector expression.
 * @return The scalar length of the vector.
 */
fun length(value: AslExpr): AslExpr {
    if (value.type.dataShapeOrNull() == null || value.type == F32) {
        throw AslDefinitionException("length needs a vector, got ${value.type}.")
    }
    return AslCall("length", listOf(value), F32)
}

/**
 * WGSL `exp` builtin.
 *
 * @param value The exponent value.
 * @return e raised to the power of value.
 */
fun exp(value: AslExpr): AslExpr = AslCall("exp", listOf(value), value.type)

/**
 * WGSL `sin` builtin.
 *
 * @param value The angle in radians.
 * @return The sine of the angle.
 */
fun sin(value: AslExpr): AslExpr = AslCall("sin", listOf(value), value.type)

/**
 * WGSL `cos` builtin.
 *
 * @param value The angle in radians.
 * @return The cosine of the angle.
 */
fun cos(value: AslExpr): AslExpr = AslCall("cos", listOf(value), value.type)

/**
 * WGSL `saturate` builtin -- `clamp(x, 0.0, 1.0)`.
 *
 * @param value The value to saturate.
 * @return The saturated value.
 */
fun saturate(value: AslExpr): AslExpr = AslCall("saturate", listOf(value), value.type)

/**
 * WGSL `cross` builtin.
 *
 * @param a First Vec3.
 * @param b Second Vec3.
 * @return The cross product Vec3.
 */
fun cross(a: AslExpr, b: AslExpr): AslExpr {
    val vec3 = AslType.Data(io.github.awakelab.awake.core.geometry.GpuDataShape.Vec3)
    if (a.type != vec3 || b.type != vec3) {
        throw AslDefinitionException("cross needs two Vec3, got ${'$'}{a.type}/${'$'}{b.type}.")
    }
    return AslCall("cross", listOf(a, b), vec3)
}

/**
 * WGSL `sqrt` builtin.
 *
 * @param value The expression.
 * @return The square root of the value.
 */
fun sqrt(value: AslExpr): AslExpr = AslCall("sqrt", listOf(value), value.type)

/**
 * WGSL `inverseSqrt` builtin.
 *
 * @param value The expression.
 * @return The reciprocal square root of the value.
 */
fun inverseSqrt(value: AslExpr): AslExpr = AslCall("inverseSqrt", listOf(value), value.type)

/**
 * WGSL `smoothstep` builtin -- `smoothstep(low, high, x)`.
 *
 * @param low The lower edge.
 * @param high The upper edge.
 * @param x The interpolation value.
 * @return The smoothed value.
 */
fun smoothstep(low: AslExpr, high: AslExpr, x: AslExpr): AslExpr =
    AslCall("smoothstep", listOf(low, high, x), x.type)

/** WGSL `step` builtin. */
fun step(edge: AslExpr, value: AslExpr): AslExpr =
    AslCall("step", listOf(edge, value), value.type)

/**
 * WGSL `dpdx` builtin.
 *
 * @param value The expression.
 * @return The partial derivative with respect to x.
 */
fun dpdx(value: AslExpr): AslExpr = AslCall("dpdx", listOf(value), value.type)

/**
 * WGSL `dpdy` builtin.
 *
 * @param value The expression.
 * @return The partial derivative with respect to y.
 */
fun dpdy(value: AslExpr): AslExpr = AslCall("dpdy", listOf(value), value.type)

/**
 * WGSL `fwidth` builtin -- the sum of the absolute screen-space derivatives.
 *
 * Derivatives are fragment-stage only and are intentionally emitted as-is. The CPU evaluator
 * does not model fragment quads, so derivative expressions are for backend shader generation.
 */
fun fwidth(value: AslExpr): AslExpr = AslCall("fwidth", listOf(value), value.type)
