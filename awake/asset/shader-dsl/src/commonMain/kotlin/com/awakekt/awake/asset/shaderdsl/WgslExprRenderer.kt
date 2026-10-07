/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape

internal fun wgslType(shape: GpuDataShape): String = when (shape) {
    GpuDataShape.Float -> "f32"
    GpuDataShape.Vec2 -> "vec2f"
    GpuDataShape.Vec3 -> "vec3f"
    GpuDataShape.Vec4 -> "vec4f"
    GpuDataShape.Mat4 -> "mat4x4<f32>"
    GpuDataShape.UInt4 -> "vec4<u32>"
}

@Suppress("CyclomaticComplexMethod")
internal fun wgslTypeName(type: AslType): String = when (type) {
    is AslType.Data -> wgslType(type.shape)
    AslType.I32 -> "i32"
    AslType.U32 -> "u32"
    AslType.Bool -> "bool"
    AslType.Vec2U -> "vec2<u32>"
    is AslType.ArrayData -> "array<${wgslType(type.shape)}, ${type.count}>"
    AslType.Texture2dF32 -> "texture_2d<f32>"
    AslType.TextureCubeF32 -> "texture_cube<f32>"
    AslType.Texture2dArrayF32 -> "texture_2d_array<f32>"
    AslType.TextureDepth2d -> "texture_depth_2d"
    AslType.TextureDepth2dArray -> "texture_depth_2d_array"
    AslType.TextureDepthCube -> "texture_depth_cube"
    AslType.Sampler -> "sampler"
    AslType.SamplerNonFiltering -> "sampler"
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

private const val LOGICAL = 1
private const val COMPARISON = 2

/** Operator precedence, loosest first: logical, comparison, additive, multiplicative. */
private fun precedenceOf(op: String): Int = when (op) {
    "||", "&&" -> LOGICAL
    "<", "<=", ">", ">=", "==", "!=" -> COMPARISON
    "+", "-" -> 3
    else -> 4
}

internal fun render(expr: AslExpr, stage: AslStage): String = when (expr) {
    is AslLiteral -> literalWgsl(expr)
    is AslRef -> expr.wgslName
    is AslVaryingRef -> if (stage == AslStage.Vertex) "output.${expr.name}" else expr.name
    is AslSwizzle -> renderSwizzle(expr, stage)
    is AslUnary -> renderUnary(expr, stage)
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

/** `-(a + b)`, and `-(-a)` because WGSL reads `--` as a decrement; `-a` stays bare. */
private fun renderUnary(expr: AslUnary, stage: AslStage): String {
    val operand = render(expr.operand, stage)
    val wrap = expr.operand is AslBinary || operand.startsWith("-")
    return if (wrap) "${expr.op}($operand)" else "${expr.op}$operand"
}

private fun renderBinary(expr: AslBinary, stage: AslStage): String {
    fun side(child: AslExpr, isRight: Boolean): String {
        val rendered = render(child, stage)
        return if (needsParentheses(expr, child, isRight)) "($rendered)" else rendered
    }
    return "${side(expr.left, false)} ${expr.op} ${side(expr.right, true)}"
}

/**
 * Whether [child], an operand of [parent], must be parenthesized for the text to evaluate the
 * tree. A looser child always is. At the same precedence, WGSL refuses `&&` mixed with `||` and
 * one comparison read by another, so those are wrapped on either side. Arithmetic is
 * left-associative, so only a right child can regroup, and it is wrapped where that changes the
 * value: `a - (b + c)`, `a / (b * c)`, and `i * (j / k)` on integers, whose division truncates.
 */
private fun needsParentheses(parent: AslBinary, child: AslExpr, isRight: Boolean): Boolean {
    if (child !is AslBinary) return false
    val parentPrecedence = precedenceOf(parent.op)
    val childPrecedence = precedenceOf(child.op)
    return when {
        childPrecedence != parentPrecedence -> childPrecedence < parentPrecedence
        parentPrecedence == LOGICAL -> child.op != parent.op
        parentPrecedence == COMPARISON -> true
        !isRight -> false
        parent.op == "-" || parent.op == "/" -> true
        else -> parent.op == "*" && child.op == "/" && (parent.type == AslType.I32 || parent.type == AslType.U32)
    }
}
