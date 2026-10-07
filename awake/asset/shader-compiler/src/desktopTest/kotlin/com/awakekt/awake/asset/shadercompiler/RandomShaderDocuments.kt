/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdocument.ShaderBlend
import com.awakekt.awake.asset.shaderdocument.ShaderDocument
import com.awakekt.awake.asset.shaderdocument.ShaderExpr
import com.awakekt.awake.asset.shaderdocument.ShaderFragmentStage
import com.awakekt.awake.asset.shaderdocument.ShaderFunction
import com.awakekt.awake.asset.shaderdocument.ShaderInput
import com.awakekt.awake.asset.shaderdocument.ShaderParameter
import com.awakekt.awake.asset.shaderdocument.ShaderPlane
import com.awakekt.awake.asset.shaderdocument.ShaderStatement
import com.awakekt.awake.asset.shaderdocument.ShaderSurface
import com.awakekt.awake.asset.shaderdocument.ShaderTexture
import com.awakekt.awake.asset.shaderdocument.ShaderValueType
import com.awakekt.awake.asset.shaderdocument.ShaderVertexStage
import kotlin.random.Random

/**
 * Shader documents built at random, well-typed by construction, so naga sees the emitter's output for
 * combinations no hand-written test would think of. A document can still be one the checker rejects,
 * for instance a constant that folds to a division by zero; callers keep only those that compile.
 */
internal class RandomShaderDocuments(seed: Int) {
    private val random = Random(seed)

    /** Locals visible while generating: name to how many components it holds, 0 for a condition. */
    private class Env(val stage: String, val locals: MutableMap<String, Int>, val vars: MutableSet<String>)

    private lateinit var surface: ShaderSurface
    private lateinit var parameters: List<ShaderParameter>
    private lateinit var textures: List<ShaderTexture>
    private var nextName = 0

    fun next(): ShaderDocument {
        nextName = 0
        surface = ShaderSurface.entries.random(random)
        parameters = List(random.nextInt(0, 4)) { i ->
            val type = ShaderValueType.entries.random(random)
            ShaderParameter("p$i", type, List(components(type)) { random.nextDouble(0.0, 1.0).toFloat() })
        }
        textures = List(if (random.nextBoolean()) random.nextInt(0, 3) else 0) { ShaderTexture("tex$it") }
        val vertex = if (surface == ShaderSurface.Plane && random.nextBoolean()) vertexStage() else null
        val fragmentEnv = Env("fragment", mutableMapOf(), mutableSetOf())
        val statements = statements(fragmentEnv, depth = 0, loops = 0)
        val sampled = textures.map { texture -> sample(texture.name, fragmentEnv, 1) }
        val color = sampled.fold(expr(4, fragmentEnv, 3)) { acc, s -> ShaderExpr.Add(acc, s) }
        return ShaderDocument(
            name = "random",
            surface = surface,
            blend = blendFor(surface),
            plane = if (surface == ShaderSurface.Plane) ShaderPlane(listOf(2f, 3f), random.nextInt(1, 5)) else null,
            parameters = parameters,
            textures = textures,
            vertex = vertex,
            fragment = ShaderFragmentStage(statements, color),
        )
    }

    private fun blendFor(surface: ShaderSurface): ShaderBlend = when (surface) {
        ShaderSurface.Background -> ShaderBlend.Opaque
        ShaderSurface.Overlay -> ShaderBlend.Alpha
        ShaderSurface.Plane -> ShaderBlend.entries.random(random)
    }

    private fun vertexStage(): ShaderVertexStage {
        val env = Env("vertex", mutableMapOf(), mutableSetOf())
        return ShaderVertexStage(statements(env, depth = 0, loops = 0), if (random.nextBoolean()) expr(1, env, 3) else null)
    }

    private fun statements(env: Env, depth: Int, loops: Int): List<ShaderStatement> = List(random.nextInt(0, if (depth == 0) 5 else 3)) {
        statement(env, depth, loops)
    }.filterNotNull()

    @Suppress("CyclomaticComplexMethod")
    private fun statement(env: Env, depth: Int, loops: Int): ShaderStatement? = when (random.nextInt(6)) {
        0 -> declare(env, mutable = false)
        1 -> declare(env, mutable = true)
        2 -> env.vars.randomOrNull(random)?.let { name -> ShaderStatement.Assign(name, valueOf(env.locals.getValue(name), env)) }
        3 -> if (depth < 2) ShaderStatement.If(condition(env, 2), statements(Env(env.stage, env.locals.toMutableMap(), env.vars.toMutableSet()), depth + 1, loops)) else null
        4 -> if (loops < 2) loop(env, depth, loops) else null
        else -> if (env.stage == "fragment" && depth == 0) ShaderStatement.DiscardIf(condition(env, 2)) else null
    }

    /** A value of [size] components, or a condition when [size] is 0. */
    private fun valueOf(size: Int, env: Env): ShaderExpr = if (size == 0) condition(env, 2) else expr(size, env, 2)

    private fun declare(env: Env, mutable: Boolean): ShaderStatement {
        val size = random.nextInt(0, 5)
        val value = valueOf(size, env)
        val name = "l${nextName++}"
        env.locals[name] = size
        if (mutable) env.vars += name
        return if (mutable) ShaderStatement.Var(name, value) else ShaderStatement.Let(name, value)
    }

    private fun loop(env: Env, depth: Int, loops: Int): ShaderStatement {
        val counter = "c${nextName++}"
        val inner = Env(env.stage, env.locals.toMutableMap().apply { put(counter, 1) }, env.vars.toMutableSet())
        val from = random.nextInt(0, 3)
        return ShaderStatement.For(counter, from, from + random.nextInt(1, 5), statements(inner, depth + 1, loops + 1))
    }

    private fun condition(env: Env, depth: Int): ShaderExpr = when {
        depth > 0 && random.nextInt(3) == 0 -> ShaderExpr.And(condition(env, depth - 1), condition(env, depth - 1))
        depth > 0 && random.nextInt(3) == 0 -> ShaderExpr.Or(condition(env, depth - 1), ShaderExpr.Not(condition(env, depth - 1)))
        else -> {
            val bools = env.locals.filterValues { it == 0 }.keys
            if (bools.isNotEmpty() && random.nextBoolean()) {
                ShaderExpr.Local(bools.random(random))
            } else {
                listOf(::lt, ::gt, ::eq).random(random)(expr(1, env, depth), expr(1, env, depth))
            }
        }
    }

    private fun lt(a: ShaderExpr, b: ShaderExpr): ShaderExpr = ShaderExpr.Lt(a, b)
    private fun gt(a: ShaderExpr, b: ShaderExpr): ShaderExpr = ShaderExpr.Ge(a, b)
    private fun eq(a: ShaderExpr, b: ShaderExpr): ShaderExpr = ShaderExpr.Ne(a, b)

    /** A numeric expression of [size] components. */
    @Suppress("CyclomaticComplexMethod")
    private fun expr(size: Int, env: Env, depth: Int): ShaderExpr {
        if (depth <= 0) return leaf(size, env)
        val d = depth - 1
        return when (random.nextInt(11)) {
            10 -> ShaderExpr.Div(expr(size, env, d), positive(1, env, d))
            0 -> ShaderExpr.Add(expr(size, env, d), expr(if (random.nextBoolean()) size else 1, env, d))
            1 -> ShaderExpr.Mul(expr(if (random.nextBoolean()) size else 1, env, d), expr(size, env, d))
            2 -> ShaderExpr.Sub(expr(size, env, d), expr(size, env, d))
            3 -> ShaderExpr.Negate(expr(size, env, d))
            4 -> unary(size, env, d)
            5 -> binary(size, env, d)
            6 -> if (size == 1) reduce(env, d) else construct(size, env, d)
            7 -> swizzle(size, env, d)
            8 -> ShaderExpr.Call(ShaderFunction.Select, listOf(expr(size, env, d), expr(size, env, d), condition(env, 1)))
            else -> if (size == 4 && env.stage == "fragment" && textures.isNotEmpty()) sample(textures.random(random).name, env, d) else leaf(size, env)
        }
    }

    private fun unary(size: Int, env: Env, depth: Int): ShaderExpr {
        val fns = listOf(ShaderFunction.Sin, ShaderFunction.Cos, ShaderFunction.Abs, ShaderFunction.Floor, ShaderFunction.Fract, ShaderFunction.Saturate) +
            if (size > 1) listOf(ShaderFunction.Normalize) else emptyList()
        val fn = fns.random(random)
        // normalize of a constant zero vector folds to NaN; offset it so a constant argument is never zero.
        val arg = if (fn == ShaderFunction.Normalize) ShaderExpr.Add(expr(size, env, depth), ShaderExpr.Constant(listOf(0.5f))) else expr(size, env, depth)
        return ShaderExpr.Call(fn, listOf(arg))
    }

    /** A value that is never zero or negative: `abs(x) + 0.5`. */
    private fun positive(size: Int, env: Env, depth: Int): ShaderExpr =
        ShaderExpr.Add(ShaderExpr.Call(ShaderFunction.Abs, listOf(expr(size, env, depth))), ShaderExpr.Constant(listOf(0.5f)))

    private fun binary(size: Int, env: Env, depth: Int): ShaderExpr = when (random.nextInt(7)) {
        5 -> ShaderExpr.Call(ShaderFunction.Pow, listOf(positive(size, env, depth), ShaderExpr.Constant(listOf(random.nextInt(1, 4).toFloat()))))
        6 -> ShaderExpr.Call(ShaderFunction.Sqrt, listOf(positive(size, env, depth)))
        0 -> ShaderExpr.Call(listOf(ShaderFunction.Min, ShaderFunction.Max).random(random), listOf(expr(size, env, depth), expr(if (random.nextBoolean()) size else 1, env, depth)))
        1 -> ShaderExpr.Call(ShaderFunction.Step, listOf(expr(if (random.nextBoolean()) size else 1, env, depth), expr(size, env, depth)))
        2 -> ShaderExpr.Call(ShaderFunction.Clamp, listOf(expr(size, env, depth), ShaderExpr.Constant(listOf(0f)), expr(if (random.nextBoolean()) size else 1, env, depth)))
        3 -> ShaderExpr.Call(ShaderFunction.Mix, listOf(expr(size, env, depth), expr(size, env, depth), expr(if (random.nextBoolean()) size else 1, env, depth)))
        else -> ShaderExpr.Call(ShaderFunction.Smoothstep, listOf(ShaderExpr.Constant(listOf(0f)), ShaderExpr.Constant(listOf(1f)), expr(size, env, depth)))
    }

    /** A scalar from a vector: a length or a dot product. */
    private fun reduce(env: Env, depth: Int): ShaderExpr {
        val size = random.nextInt(2, 5)
        return if (random.nextBoolean()) {
            ShaderExpr.Call(ShaderFunction.Length, listOf(expr(size, env, depth)))
        } else {
            ShaderExpr.Call(ShaderFunction.Dot, listOf(expr(size, env, depth), expr(size, env, depth)))
        }
    }

    private fun construct(size: Int, env: Env, depth: Int): ShaderExpr {
        val parts = if (random.nextInt(3) == 0) listOf(expr(1, env, depth)) else split(size).map { expr(it, env, depth) }
        return when (size) {
            2 -> ShaderExpr.Vec2(parts)
            3 -> if (random.nextInt(4) == 0) ShaderExpr.Call(ShaderFunction.Cross, listOf(expr(3, env, depth), expr(3, env, depth))) else ShaderExpr.Vec3(parts)
            else -> ShaderExpr.Vec4(parts)
        }
    }

    private fun split(size: Int): List<Int> {
        val parts = mutableListOf<Int>()
        var left = size
        while (left > 0) {
            val part = random.nextInt(1, left + 1).coerceAtMost(4)
            parts += part
            left -= part
        }
        return parts
    }

    private fun swizzle(size: Int, env: Env, depth: Int): ShaderExpr {
        val from = random.nextInt(maxOf(2, size), 5)
        val letters = if (random.nextBoolean()) "xyzw" else "rgba"
        val components = String(CharArray(size) { letters[random.nextInt(from)] })
        return ShaderExpr.Swizzle(expr(from, env, depth), components)
    }

    private fun sample(texture: String, env: Env, depth: Int): ShaderExpr = ShaderExpr.Sample(texture, expr(2, env, depth))

    private fun leaf(size: Int, env: Env): ShaderExpr {
        val locals = env.locals.filterValues { it == size }.keys
        val params = parameters.filter { components(it.type) == size }
        val inputs = inputsFor(env.stage).filter { it.second == size }.map { it.first }
        return when (random.nextInt(4)) {
            0 -> locals.randomOrNull(random)?.let { ShaderExpr.Local(it) }
            1 -> params.randomOrNull(random)?.let { ShaderExpr.Param(it.name) }
            2 -> inputs.randomOrNull(random)?.let { ShaderExpr.Input(it) }
            else -> null
        } ?: ShaderExpr.Constant(List(size) { (random.nextInt(-20, 21) / 4f) })
    }

    private fun inputsFor(stage: String): List<Pair<ShaderInput, Int>> {
        val plane = surface == ShaderSurface.Plane
        val fragment = stage == "fragment"
        return buildList {
            add(ShaderInput.Uv to 2)
            add(ShaderInput.Time to 1)
            add(ShaderInput.DeltaTime to 1)
            add(ShaderInput.Resolution to 2)
            add(ShaderInput.CameraPosition to 3)
            add(ShaderInput.SunDirection to 3)
            if (fragment) add(ShaderInput.ScreenUv to 2)
            if (fragment) add(ShaderInput.ViewDirection to 3)
            if (plane) add(ShaderInput.WorldPosition to 3)
            if (plane) add(ShaderInput.Normal to 3)
        }
    }

    private fun components(type: ShaderValueType): Int = when (type) {
        ShaderValueType.Float -> 1
        ShaderValueType.Vec2 -> 2
        ShaderValueType.Vec3 -> 3
        ShaderValueType.Vec4, ShaderValueType.Color -> 4
    }
}
