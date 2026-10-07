/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

// The shape rules a document's operators and functions follow. They are WGSL's own rules, so a
// document that passes them emits WGSL that type-checks, which matters because WebGPU does not report
// a shader it rejects until the frame that draws it.

/** The WGSL operator an arithmetic node is. */
internal val ShaderExpr.Arithmetic.symbol: String
    get() = when (this) {
        is ShaderExpr.Add -> "+"
        is ShaderExpr.Sub -> "-"
        is ShaderExpr.Mul -> "*"
        is ShaderExpr.Div -> "/"
    }

/** The WGSL operator a comparison node is. */
internal val ShaderExpr.Comparison.symbol: String
    get() = when (this) {
        is ShaderExpr.Lt -> "<"
        is ShaderExpr.Le -> "<="
        is ShaderExpr.Gt -> ">"
        is ShaderExpr.Ge -> ">="
        is ShaderExpr.Eq -> "=="
        is ShaderExpr.Ne -> "!="
    }

/** The WGSL operator a logical node is. */
internal val ShaderExpr.Logical.symbol: String
    get() = when (this) {
        is ShaderExpr.And -> "&&"
        is ShaderExpr.Or -> "||"
    }

/** The shape of `a op b` for an arithmetic operator: equal shapes, or a scalar with anything. Null when they do not combine. */
internal fun arithmeticShape(a: ValueShape, b: ValueShape): ValueShape? = when {
    !a.isNumeric || !b.isNumeric -> null
    a == b -> a
    a == ValueShape.F32 -> b
    b == ValueShape.F32 -> a
    else -> null
}

/** Why [components] cannot be read from a value of [base], or null when it can. */
internal fun swizzleError(base: ValueShape, components: String): String? = when {
    !base.isVector -> "only a vector has components to pick, and this is $base"
    components.length !in 1..4 -> "picks ${components.length} components; pick 1 to 4"
    !(components.all { it in "xyzw" } || components.all { it in "rgba" }) ->
        "'$components' must use only xyzw or only rgba"
    swizzleIndices(components).any { it >= base.components } -> "'$components' reaches past $base"
    else -> null
}

/** The component each letter of a valid swizzle reads. */
internal fun swizzleIndices(components: String): IntArray =
    IntArray(components.length) { i -> "xyzw".indexOf(components[i]).takeIf { it >= 0 } ?: "rgba".indexOf(components[i]) }

/** Why [args] do not fit [fn], or null when they do. */
internal fun callError(fn: ShaderFunction, args: List<ValueShape>): String? {
    if (args.size != fn.arity) return "${fn.wgslName} takes ${fn.arity} argument(s), got ${args.size}"
    return shapeError(fn, args)
}

@Suppress("CyclomaticComplexMethod") // One branch per function family; the families are the spec.
private fun shapeError(fn: ShaderFunction, args: List<ValueShape>): String? {
    val numeric = if (fn == ShaderFunction.Select) args.take(2) else args
    val name = fn.wgslName
    return when {
        numeric.any { !it.isNumeric } -> "$name takes numbers, got ${args.joinToString { it.toString() }}"
        fn == ShaderFunction.Normalize && !args[0].isVector -> "normalize takes a vector, got ${args[0]}"
        fn == ShaderFunction.Length && !args[0].isVector -> "length takes a vector, got ${args[0]}"
        fn == ShaderFunction.Dot && (!args[0].isVector || args[0] != args[1]) ->
            "dot takes two vectors of one size, got ${args[0]} and ${args[1]}"
        fn == ShaderFunction.Cross && (args[0] != ValueShape.Vec3 || args[1] != ValueShape.Vec3) ->
            "cross takes two 3-component vectors, got ${args[0]} and ${args[1]}"
        fn in SAME_OR_SCALAR_SECOND && !fits(args[1], args[0]) ->
            "$name's second argument must be a scalar or ${args[0]}, got ${args[1]}"
        fn == ShaderFunction.Step && !fits(args[0], args[1]) -> "step's edge must be a scalar or ${args[1]}, got ${args[0]}"
        fn == ShaderFunction.Clamp && !(fits(args[1], args[0]) && fits(args[2], args[0])) ->
            "clamp's bounds must each be a scalar or ${args[0]}, got ${args[1]} and ${args[2]}"
        fn == ShaderFunction.Smoothstep && !(fits(args[0], args[2]) && fits(args[1], args[2])) ->
            "smoothstep's edges must each be a scalar or ${args[2]}, got ${args[0]} and ${args[1]}"
        fn == ShaderFunction.Mix && (args[0] != args[1] || !fits(args[2], args[0])) ->
            "mix takes two values of one shape and a scalar or that shape, got ${args.joinToString { it.toString() }}"
        fn == ShaderFunction.Select && (args[0] != args[1] || args[2] != ValueShape.Bool) ->
            "select takes two values of one shape and a condition, got ${args.joinToString { it.toString() }}"
        else -> null
    }
}

/** The shape [fn] gives for [args], which [callError] accepted. */
internal fun callShape(fn: ShaderFunction, args: List<ValueShape>): ValueShape = when (fn) {
    ShaderFunction.Length, ShaderFunction.Dot -> ValueShape.F32
    ShaderFunction.Step -> args[1]
    ShaderFunction.Smoothstep -> args[2]
    else -> args[0]
}

/** The arguments of [fn] that are splatted to the result's shape when they are scalars. */
internal fun splattedArguments(fn: ShaderFunction): Set<Int> = when (fn) {
    ShaderFunction.Min, ShaderFunction.Max, ShaderFunction.Pow -> setOf(1)
    ShaderFunction.Step -> setOf(0)
    ShaderFunction.Clamp -> setOf(1, 2)
    ShaderFunction.Smoothstep -> setOf(0, 1)
    else -> emptySet()
}

/** The function's WGSL name, which is also its name in a document. */
internal val ShaderFunction.wgslName: String get() = name.replaceFirstChar(Char::lowercaseChar)

private val SAME_OR_SCALAR_SECOND = setOf(ShaderFunction.Min, ShaderFunction.Max, ShaderFunction.Pow)

private fun fits(argument: ValueShape, target: ValueShape) = argument == target || argument == ValueShape.F32
