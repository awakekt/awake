/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/** Where a block of statements is checked. */
internal class BlockEnv(
    val stage: Stage,
    val scope: Scope<CheckedLocal>,
    /** How many times the block runs per vertex or pixel. */
    val weight: Long,
    /** Loops around the block. */
    val loopDepth: Int,
    /** Whether the block is the stage's own, not inside an `if` or a loop. */
    val topLevel: Boolean,
) {
    val expressions: ExpressionEnv get() = ExpressionEnv(stage, scope, weight)
}

/** Checks statements: names and scoping, assignments, conditions, loop bounds and costs, discards. */
internal class StatementChecker(private val context: CheckContext, private val expressions: ExpressionChecker) {
    fun checkBlock(statements: List<ShaderStatement>, path: String, env: BlockEnv) {
        statements.forEachIndexed { i, statement -> check(statement, "$path[$i]", env) }
    }

    private fun check(statement: ShaderStatement, path: String, env: BlockEnv) {
        context.nodes++
        context.weightedCost += env.weight
        when (statement) {
            is ShaderStatement.Let -> declare(statement.name, statement.value, LocalKind.Let, path, env)
            is ShaderStatement.Var -> declare(statement.name, statement.value, LocalKind.Var, path, env)
            is ShaderStatement.Assign -> assign(statement, path, env)
            is ShaderStatement.If -> {
                condition(statement.condition, "$path.condition", env, "'if'")
                checkBlock(statement.then, "$path.then", BlockEnv(env.stage, env.scope.child(), env.weight, env.loopDepth, topLevel = false))
            }
            is ShaderStatement.For -> loop(statement, path, env)
            is ShaderStatement.DiscardIf -> {
                if (env.stage != Stage.Fragment || !env.topLevel) {
                    context.issue(path, "'discard_if' belongs in the fragment stage's own statements, not in a vertex stage, an 'if' or a loop")
                }
                condition(statement.condition, "$path.condition", env, "'discard_if'")
            }
        }
    }

    private fun declare(name: String, value: ShaderExpr, kind: LocalKind, path: String, env: BlockEnv) {
        val typed = expressions.check(value, "$path.value", env.expressions)
        if (isNewLocal(name, "$path.name", env)) {
            // A var can change, so only a let's value is known before the shader runs.
            val constant = if (kind == LocalKind.Let) typed?.constant else null
            env.scope.declare(name, CheckedLocal(typed?.shape ?: ValueShape.F32, kind, constant))
        }
    }

    private fun assign(statement: ShaderStatement.Assign, path: String, env: BlockEnv) {
        val typed = expressions.check(statement.value, "$path.value", env.expressions)
        val target = env.scope.find(statement.name)
        when {
            target == null || target.kind != LocalKind.Var ->
                context.issue("$path.name", "'set' changes a 'var', and '${statement.name}' is not one visible here")
            typed != null && typed.shape != target.shape ->
                context.issue("$path.value", "'${statement.name}' holds ${target.shape}, and this is ${typed.shape}")
        }
    }

    private fun loop(statement: ShaderStatement.For, path: String, env: BlockEnv) {
        val limits = context.limits
        val iterations = statement.until.toLong() - statement.from
        when {
            statement.from < 0 -> context.issue("$path.from", "a loop starts at 0 or more, got ${statement.from}")
            iterations < 1 -> context.issue("$path.until", "a loop's 'until' must be more than its 'from'")
            iterations > limits.maxLoopIterations ->
                context.issue("$path.until", "the loop runs $iterations times; the limit is ${limits.maxLoopIterations}")
        }
        if (env.loopDepth + 1 > limits.maxLoopNesting) {
            context.issue(path, "loops nest more than ${limits.maxLoopNesting} deep")
        }
        val body = env.scope.child()
        if (isNewLocal(statement.counter, "$path.counter", env)) {
            body.declare(statement.counter, CheckedLocal(ValueShape.F32, LocalKind.Counter, constant = null))
        }
        val weight = env.weight * iterations.coerceIn(1L, limits.maxLoopIterations.toLong())
        checkBlock(statement.body, "$path.body", BlockEnv(env.stage, body, weight, env.loopDepth + 1, topLevel = false))
    }

    private fun condition(expr: ShaderExpr, path: String, env: BlockEnv, what: String) {
        val typed = expressions.check(expr, path, env.expressions)
        if (typed != null && typed.shape != ValueShape.Bool) {
            context.issue(path, "$what takes a condition, got ${typed.shape}")
        }
    }

    /** Whether [name] is a valid new local here, recording why when it is not. Counts it toward the locals limit. */
    private fun isNewLocal(name: String, path: String, env: BlockEnv): Boolean {
        context.locals++
        val problem = when {
            !DOCUMENT_NAME.matches(name) -> "'$name' is not a valid name: a letter, then letters, digits or underscores, 32 at most"
            env.scope.find(name) != null -> "'$name' is already declared and visible here"
            else -> null
        }
        problem?.let { context.issue(path, it) }
        return problem == null
    }
}
