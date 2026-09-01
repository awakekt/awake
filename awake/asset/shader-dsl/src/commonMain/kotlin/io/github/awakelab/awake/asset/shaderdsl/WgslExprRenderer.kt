/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape

internal fun wgslType(shape: GpuDataShape): String = when (shape) {
    GpuDataShape.Float -> "f32"
    GpuDataShape.Vec2 -> "vec2f"
    GpuDataShape.Vec3 -> "vec3f"
    GpuDataShape.Vec4 -> "vec4f"
    GpuDataShape.Mat4 -> "mat4x4<f32>"
    GpuDataShape.UInt4 -> "vec4<u32>"
}

internal fun wgslTypeName(type: AslType): String = when (type) {
    is AslType.Data -> wgslType(type.shape)
    AslType.I32 -> "i32"
    AslType.U32 -> "u32"
    AslType.Bool -> "bool"
    AslType.Vec2U -> "vec2<u32>"
    is AslType.ArrayData -> "array<${wgslType(type.shape)}, ${type.count}>"
    AslType.Texture2dF32 -> "texture_2d<f32>"
    AslType.Texture2dArrayF32 -> "texture_2d_array<f32>"
    AslType.TextureDepth2d -> "texture_depth_2d"
    AslType.TextureDepth2dArray -> "texture_depth_2d_array"
    AslType.Sampler -> "sampler"
    AslType.SamplerComparison -> "sampler_comparison"
}

internal fun floatWgsl(value: Float): String {
    val whole = value.toLong()
    return if (whole.toFloat() == value) "$whole.0" else value.toString()
}

internal fun literalWgsl(literal: AslLiteral): String = when (literal.type) {
    AslType.I32 -> literal.value.toInt().toString()
    AslType.U32 -> "${literal.value.toUInt()}u"
    else -> floatWgsl(literal.value)
}

/** Operator precedence for minimal parentheses: logical, comparison, additive,
 * multiplicative, unary/atom. */
private fun precedenceOf(op: String): Int = when (op) {
    "||", "&&" -> 1
    "<", "<=", ">", ">=", "==", "!=" -> 2
    "+", "-" -> 3
    else -> 4
}

internal fun render(expr: AslExpr, stage: AslStage): String = when (expr) {
    is AslLiteral -> literalWgsl(expr)
    is AslRef -> expr.wgslName
    is AslVaryingRef -> if (stage == AslStage.Vertex) "output.${expr.name}" else expr.name
    is AslSwizzle -> renderSwizzle(expr, stage)
    is AslUnary -> "${expr.op}${render(expr.operand, stage)}"
    is AslIndex -> "${expr.arrayName}[${render(expr.index, stage)}]"
    is AslChainIndex ->
        "${expr.varName}[${render(expr.outer, stage)}].${expr.fieldName}[${render(expr.inner, stage)}]"
    is AslBinary -> renderBinary(expr, stage)
    is AslArrayLiteral ->
        "array<${wgslType(expr.shape)}, ${expr.elements.size}>(${expr.elements.joinToString { render(it, stage) }})"
    is AslCall -> "${expr.function}(${expr.args.joinToString { render(it, stage) }})"
    is AslConstruct -> "${wgslType(expr.shape)}(${expr.args.joinToString { render(it, stage) }})"
}

private fun renderSwizzle(expr: AslSwizzle, stage: AslStage): String {
    val base = render(expr.base, stage)
    val wrapped = if (expr.base is AslBinary || expr.base is AslUnary) "($base)" else base
    return "$wrapped.${expr.components}"
}

private fun renderBinary(expr: AslBinary, stage: AslStage): String {
    val precedence = precedenceOf(expr.op)
    fun side(child: AslExpr, isRight: Boolean): String {
        val rendered = render(child, stage)
        val childPrecedence = when (child) {
            is AslBinary -> precedenceOf(child.op)
            else -> Int.MAX_VALUE
        }
        val nonAssociative = isRight && (expr.op == "-" || expr.op == "/")
        val wrap = childPrecedence < precedence ||
            (childPrecedence == precedence && nonAssociative)
        return if (wrap) "($rendered)" else rendered
    }
    return "${side(expr.left, false)} ${expr.op} ${side(expr.right, true)}"
}
