/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape

// Vector constructors and conversions -- split from AslBuiltins.kt only to stay under the
// per-file function budget. The texture builtins live in AslTextureBuiltins.kt.

/** Constructs a `vec2f` from the provided [args]. */
fun vec2(vararg args: AslExpr): AslExpr = AslConstruct(GpuDataShape.Vec2, args.toList())

/** Constructs a `vec3f` from the provided [args]. */
fun vec3(vararg args: AslExpr): AslExpr = AslConstruct(GpuDataShape.Vec3, args.toList())

/** Constructs a `vec4f` from the provided [args]. */
fun vec4(vararg args: AslExpr): AslExpr = AslConstruct(GpuDataShape.Vec4, args.toList())

/**
 * Constructs a `mat4x4<f32>` from four column vectors.
 *
 * @param columns The four column [AslExpr]s (must be `vec4f`).
 * @return The matrix expression.
 */
fun mat4(vararg columns: AslExpr): AslExpr = AslConstruct(GpuDataShape.Mat4, columns.toList())

/**
 * Converts a value to `f32`.
 *
 * @param value The expression to convert.
 * @return The `f32` expression.
 */
fun toF32(value: AslExpr): AslExpr = AslCall("f32", listOf(value), F32)

/**
 * Converts a value to `u32`.
 *
 * The counterpart to [toF32], for the one place a float has to become an index: WGSL array
 * subscripts take `i32`/`u32`, so a value carried through a float vertex channel needs this
 * before it can address a uniform array.
 *
 * @param value The expression to convert.
 * @return The `u32` expression.
 */
fun toU32(value: AslExpr): AslExpr = AslCall("u32", listOf(value), AslType.U32)

/**
 * WGSL `select` builtin -- `select(ifFalse, ifTrue, condition)`.
 *
 * @param ifFalse The value to return if condition is false.
 * @param ifTrue The value to return if condition is true.
 * @param condition The boolean condition expression.
 * @return The selected expression.
 */
fun select(ifFalse: AslExpr, ifTrue: AslExpr, condition: AslExpr): AslExpr {
    if (ifFalse.type != ifTrue.type || condition.type != AslType.Bool) {
        throw AslDefinitionException(
            "select needs matching branches and a bool, got " +
                "${ifFalse.type}/${ifTrue.type}/${condition.type}.",
        )
    }
    return AslCall("select", listOf(ifFalse, ifTrue, condition), ifFalse.type)
}

