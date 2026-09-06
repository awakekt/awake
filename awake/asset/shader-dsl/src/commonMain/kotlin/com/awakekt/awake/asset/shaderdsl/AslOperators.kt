/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

/**
 * Lifts a [Float] literal to an [AslExpr].
 */
val Float.lit: AslExpr get() = AslLiteral(this)

/**
 * Lifts an [Int] literal to an [AslExpr] of type [AslType.I32].
 */
val Int.lit: AslExpr get() = AslLiteral(toFloat(), AslType.I32)

/**
 * Lifts a [UInt] literal to an [AslExpr] of type [AslType.U32].
 */
val UInt.lit: AslExpr get() = AslLiteral(toFloat(), AslType.U32)

/**
 * Binary addition operator.
 */
operator fun AslExpr.plus(other: AslExpr): AslExpr = AslBinary("+", this, other)

/**
 * Binary subtraction operator.
 */
operator fun AslExpr.minus(other: AslExpr): AslExpr = AslBinary("-", this, other)

/**
 * Binary multiplication operator.
 */
operator fun AslExpr.times(other: AslExpr): AslExpr = AslBinary("*", this, other)

/**
 * Binary division operator.
 */
operator fun AslExpr.div(other: AslExpr): AslExpr = AslBinary("/", this, other)

/**
 * Unary minus operator.
 */
operator fun AslExpr.unaryMinus(): AslExpr = AslUnary("-", this)

/** Swizzle for the x component. */
val AslExpr.x: AslExpr get() = AslSwizzle(this, "x")

/** Swizzle for the y component. */
val AslExpr.y: AslExpr get() = AslSwizzle(this, "y")

/** Swizzle for the z component. */
val AslExpr.z: AslExpr get() = AslSwizzle(this, "z")

/** Swizzle for the w component. */
val AslExpr.w: AslExpr get() = AslSwizzle(this, "w")

/** Swizzle for the red component. */
val AslExpr.r: AslExpr get() = AslSwizzle(this, "r")

/** Swizzle for the green component. */
val AslExpr.g: AslExpr get() = AslSwizzle(this, "g")

/** Swizzle for the blue component. */
val AslExpr.b: AslExpr get() = AslSwizzle(this, "b")

/** Swizzle for the alpha component. */
val AslExpr.a: AslExpr get() = AslSwizzle(this, "a")

/** Swizzle for the xy components. */
val AslExpr.xy: AslExpr get() = AslSwizzle(this, "xy")

/** Swizzle for the zw components. */
val AslExpr.zw: AslExpr get() = AslSwizzle(this, "zw")

/** Swizzle for the xyz components. */
val AslExpr.xyz: AslExpr get() = AslSwizzle(this, "xyz")

/** Swizzle for the rgb components. */
val AslExpr.rgb: AslExpr get() = AslSwizzle(this, "rgb")

/**
 * Accesses one column of a matrix-typed local.
 *
 * @param matrix The matrix expression (must be a named reference).
 * @param index The column index.
 * @return An [AslExpr] for the accessed column.
 */
fun column(matrix: AslExpr, index: Int): AslExpr {
    val ref = matrix as? AslRef
        ?: throw AslDefinitionException("column() needs a named matrix (let/var), got ${'$'}{matrix.type}.")
    if (ref.type != AslType.Data(com.awakekt.awake.core.geometry.GpuDataShape.Mat4)) {
        throw AslDefinitionException("column() needs a Mat4, got ${'$'}{ref.type}.")
    }
    return AslIndex(ref.wgslName, AslLiteral(index.toFloat(), AslType.I32), com.awakekt.awake.core.geometry.GpuDataShape.Vec4)
}
