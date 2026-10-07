/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/** Where an expression is checked: its stage, the locals visible to it, and how often it runs. */
internal class ExpressionEnv(
    val stage: Stage,
    val scope: Scope<CheckedLocal>,
    /** How many times it runs per vertex or pixel: the product of the iterations of the loops around it. */
    val weight: Long,
)

/**
 * Types each expression against WGSL's rules, records what each stage reads, counts each node's
 * cost, and folds the expressions whose value is known before the shader runs.
 *
 * Every problem becomes an issue with the node's path, and the node checks as `null`, so one mistake
 * is reported once rather than again by everything above it.
 */
@Suppress("TooManyFunctions") // One check per node type, plus the helpers they share.
internal class ExpressionChecker(private val context: CheckContext) {
    fun check(expr: ShaderExpr, path: String, env: ExpressionEnv): Typed? = check(expr, path, env, depth = 1)

    @Suppress("CyclomaticComplexMethod") // One branch per node type, each a call.
    private fun check(expr: ShaderExpr, path: String, env: ExpressionEnv, depth: Int): Typed? {
        context.nodes++
        context.weightedCost += env.weight
        if (depth > context.limits.maxExpressionDepth) {
            context.issue(path, "expressions nest more than ${context.limits.maxExpressionDepth} deep")
            return null
        }
        val children = { child: ShaderExpr, childPath: String -> check(child, childPath, env, depth + 1) }
        return when (expr) {
            is ShaderExpr.Constant -> constant(expr, path)
            is ShaderExpr.Construct -> construct(expr, path, children)
            is ShaderExpr.Param -> param(expr, path, env)
            is ShaderExpr.Input -> input(expr, path, env)
            is ShaderExpr.Local -> local(expr, path, env)
            is ShaderExpr.Arithmetic -> arithmetic(expr, path, children)
            is ShaderExpr.Negate -> negate(expr, path, children)
            is ShaderExpr.Comparison -> operands(expr.a, expr.b, path, children) { a, b ->
                ensure(a.shape == ValueShape.F32 && b.shape == ValueShape.F32, path) {
                    "'${expr.symbol}' compares two scalars, got ${a.shape} and ${b.shape}"
                }?.let { Typed(ValueShape.Bool) }
            }
            is ShaderExpr.Logical -> operands(expr.a, expr.b, path, children) { a, b ->
                ensure(a.shape == ValueShape.Bool && b.shape == ValueShape.Bool, path) {
                    "'${expr.symbol}' combines two conditions, got ${a.shape} and ${b.shape}"
                }?.let { Typed(ValueShape.Bool) }
            }
            is ShaderExpr.Not -> children(expr.value, "$path.value")?.let { value ->
                ensure(value.shape == ValueShape.Bool, path) { "'not' takes a condition, got ${value.shape}" }
                    ?.let { Typed(ValueShape.Bool) }
            }
            is ShaderExpr.Swizzle -> swizzle(expr, path, children)
            is ShaderExpr.Call -> call(expr, path, children)
            is ShaderExpr.Sample -> sample(expr, path, env, children)
        }
    }

    private fun constant(expr: ShaderExpr.Constant, path: String): Typed? = when {
        expr.value.size !in 1..4 -> null.also { context.issue(path, "a constant holds 1 to 4 numbers, got ${expr.value.size}") }
        expr.value.any { !it.isFinite() } -> null.also { context.issue(path, "a constant must be a finite number") }
        else -> Typed(ValueShape.ofComponents(expr.value.size), expr.value.toFloatArray())
    }

    private fun construct(expr: ShaderExpr.Construct, path: String, children: Children): Typed? {
        val args = expr.args.mapIndexed { i, arg -> children(arg, "$path.args[$i]") }
        if (args.any { it == null }) return null
        val parts = args.requireNoNulls()
        val total = parts.sumOf { it.shape.components }
        val splat = parts.size == 1 && parts[0].shape == ValueShape.F32
        val fits = parts.all { it.shape.isNumeric } && (total == expr.size || splat)
        return ensure(fits, path) {
            "vec${expr.size} needs parts adding up to ${expr.size} components, or one scalar, got ${parts.joinToString { it.shape.toString() }}"
        }?.let { folded(ValueShape.ofComponents(expr.size), path, parts) { foldConstruct(it, expr.size) } }
    }

    private fun param(expr: ShaderExpr.Param, path: String, env: ExpressionEnv): Typed? {
        val index = context.parameterIndex[expr.name]
            ?: return null.also { context.issue(path, "'${expr.name}' is not one of this document's parameters") }
        context.usage(env.stage).parameters += index
        return Typed(context.document.parameters[index].type.shape)
    }

    private fun input(expr: ShaderExpr.Input, path: String, env: ExpressionEnv): Typed? {
        val input = expr.input
        val plane = context.document.surface == ShaderSurface.Plane
        val problem = when {
            input in PLANE_ONLY && !plane -> "input '${input.serialName}' exists on a plane only"
            input in FRAGMENT_ONLY && env.stage == Stage.Vertex -> "input '${input.serialName}' exists in the fragment stage only"
            else -> null
        }
        return if (problem != null) {
            null.also { context.issue(path, problem) }
        } else {
            context.usage(env.stage).inputs += input
            Typed(input.shape)
        }
    }

    private fun local(expr: ShaderExpr.Local, path: String, env: ExpressionEnv): Typed? {
        val local = env.scope.find(expr.name)
            ?: return null.also { context.issue(path, "'${expr.name}' is not a local or loop counter declared before this point") }
        return Typed(local.shape, local.constant)
    }

    private fun arithmetic(expr: ShaderExpr.Arithmetic, path: String, children: Children): Typed? =
        operands(expr.a, expr.b, path, children) { a, b ->
            val shape = arithmeticShape(a.shape, b.shape)
            if (shape == null) {
                null.also { context.issue(path, "'${expr.symbol}' cannot combine ${a.shape} with ${b.shape}") }
            } else {
                folded(shape, path, listOf(a, b)) { (x, y) -> foldArithmetic(expr, x, y) }
            }
        }

    private fun negate(expr: ShaderExpr.Negate, path: String, children: Children): Typed? =
        children(expr.value, "$path.value")?.let { value ->
            ensure(value.shape.isNumeric, path) { "'neg' takes a number, got ${value.shape}" }
                ?.let { folded(value.shape, path, listOf(value)) { (x) -> foldNegate(x) } }
        }

    private fun swizzle(expr: ShaderExpr.Swizzle, path: String, children: Children): Typed? =
        children(expr.value, "$path.value")?.let { value ->
            val problem = swizzleError(value.shape, expr.components)
            ensure(problem == null, path) { problem.orEmpty() }
                ?.let { folded(ValueShape.ofComponents(expr.components.length), path, listOf(value)) { (x) -> foldSwizzle(x, expr.components) } }
        }

    private fun call(expr: ShaderExpr.Call, path: String, children: Children): Typed? {
        val args = expr.args.mapIndexed { i, arg -> children(arg, "$path.args[$i]") }
        if (args.any { it == null }) return null
        val typed = args.requireNoNulls()
        val problem = callError(expr.fn, typed.map { it.shape })
        return ensure(problem == null, path) { problem.orEmpty() }
            ?.let { folded(callShape(expr.fn, typed.map { it.shape }), path, typed) { foldCall(expr.fn, it) } }
    }

    private fun sample(expr: ShaderExpr.Sample, path: String, env: ExpressionEnv, children: Children): Typed? {
        val uv = children(expr.uv, "$path.uv") ?: return null
        val known = context.document.textures.any { it.name == expr.texture }
        val problem = when {
            env.stage != Stage.Fragment -> "'sample' reads a texture in the fragment stage only"
            !known -> "'${expr.texture}' is not one of this document's textures"
            uv.shape != ValueShape.Vec2 -> "'sample' reads at a 2-component coordinate, got ${uv.shape}"
            else -> null
        }
        context.weightedSamples += env.weight
        context.sampledTextures += expr.texture
        return ensure(problem == null, path) { problem.orEmpty() }?.let { Typed(ValueShape.Vec4) }
    }

    /** The value of a node whose [inputs] are all known, folded by [fold], or the node unfolded. A fold that is not finite is an issue. */
    private fun folded(shape: ValueShape, path: String, inputs: List<Typed>, fold: (List<FloatArray>) -> FloatArray?): Typed? {
        val constants = inputs.map { it.constant }
        val value = if (constants.all { it != null }) fold(constants.requireNoNulls()) else null
        return when {
            value == null -> Typed(shape)
            value.allFinite() -> Typed(shape, value)
            else -> null.also {
                context.issue(path, "evaluates, before the shader runs, to a value that is not a finite number (a division by zero, a square root of a negative, an overflow)")
            }
        }
    }

    private fun operands(a: ShaderExpr, b: ShaderExpr, path: String, children: Children, then: (Typed, Typed) -> Typed?): Typed? {
        val left = children(a, "$path.a")
        val right = children(b, "$path.b")
        return if (left == null || right == null) null else then(left, right)
    }

    /** `Unit` when [condition] holds, else null after recording [message] at [path]: `ensure(...)?.let { result }`. */
    private inline fun ensure(condition: Boolean, path: String, message: () -> String): Unit? =
        if (condition) Unit else null.also { context.issue(path, message()) }
}

private typealias Children = (ShaderExpr, String) -> Typed?

/** Inputs a full-screen surface does not have. */
private val PLANE_ONLY = setOf(ShaderInput.WorldPosition, ShaderInput.Normal)

/** Inputs a vertex stage does not have. */
private val FRAGMENT_ONLY = setOf(ShaderInput.ScreenUv, ShaderInput.ViewDirection)

/** The input's name in a document. */
internal val ShaderInput.serialName: String get() = name.replaceFirstChar(Char::lowercaseChar)
