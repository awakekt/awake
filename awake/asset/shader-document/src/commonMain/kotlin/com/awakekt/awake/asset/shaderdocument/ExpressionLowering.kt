/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdsl.AslBinary
import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslConstruct
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslLiteral
import com.awakekt.awake.asset.shaderdsl.AslSwizzle
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.AslUnary
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.core.geometry.GpuDataShape

/** A local as the lowering sees it: what to read it as, and its value when that is known before the shader runs. */
internal class LoweredLocal(val ref: AslExpr, val constant: FloatArray?)

/** What a stage's expressions read: the prologue's parameter and input values, and the textures. */
internal class StageValues(
    val parameters: Map<String, AslExpr>,
    val inputs: Map<ShaderInput, AslExpr>,
    val textures: Map<String, AslExpr>,
    val sampler: AslExpr?,
)

/**
 * Unique WGSL identifiers. No name from a document ever reaches the shader; these replace them all.
 *
 * A name is a letter, an underscore and a number, `e_12`. Without the underscore a counter would
 * become `i32` or `f16`, which WGSL reserves as type names.
 */
internal class NameSource {
    private var next = 0

    fun next(prefix: String): String = "${prefix}_${next++}"
}

/**
 * Lowers a checked document's statements and expressions into ASL.
 *
 * Every compound value gets a `let` of its own, so an operator only ever combines names and literals.
 * That keeps the emitted WGSL unambiguous whatever the operators' precedence: a negation is a
 * multiplication by -1, and a condition is always a named value before `&&`, `||` or `!` reads it.
 * Every value known before the shader runs is written as a literal (see `ConstantFolding.kt`).
 */
internal class ExpressionLowering(private val names: NameSource, private val values: StageValues) {
    fun lowerBlock(block: AslBlockBuilder, statements: List<ShaderStatement>, scope: Scope<LoweredLocal>, discard: ((AslExpr) -> Unit)?) {
        statements.forEach { lowerStatement(block, it, scope, discard) }
    }

    fun lower(block: AslBlockBuilder, expr: ShaderExpr, scope: Scope<LoweredLocal>): AslExpr {
        val constant = constantOf(expr, scope)
        return if (constant != null) literal(constant) else lowerNode(block, expr, scope)
    }

    private fun lowerStatement(block: AslBlockBuilder, statement: ShaderStatement, scope: Scope<LoweredLocal>, discard: ((AslExpr) -> Unit)?) {
        when (statement) {
            is ShaderStatement.Let ->
                scope.declare(statement.name, LoweredLocal(lower(block, statement.value, scope), constantOf(statement.value, scope)))
            is ShaderStatement.Var ->
                scope.declare(statement.name, LoweredLocal(block.variable(names.next("v"), lower(block, statement.value, scope)), null))
            is ShaderStatement.Assign -> block.assign(local(scope, statement.name).ref, lower(block, statement.value, scope))
            is ShaderStatement.If -> block.iff(lower(block, statement.condition, scope)) {
                lowerBlock(this, statement.then, scope.child(), discard = null)
            }
            is ShaderStatement.For -> block.loopI32(names.next("i"), statement.from.lit, (statement.until - 1).lit) { counter ->
                val body = scope.child()
                body.declare(statement.counter, LoweredLocal(let(names.next("c"), toF32(counter)), null))
                lowerBlock(this, statement.body, body, discard = null)
            }
            is ShaderStatement.DiscardIf ->
                requireNotNull(discard) { "the checker allows discard_if only at a fragment stage's top level" }
                    .invoke(lower(block, statement.condition, scope))
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per node type, each one line.
    private fun lowerNode(block: AslBlockBuilder, expr: ShaderExpr, scope: Scope<LoweredLocal>): AslExpr {
        fun operand(child: ShaderExpr) = lower(block, child, scope)
        fun bind(value: AslExpr) = block.let(names.next("e"), value)
        return when (expr) {
            is ShaderExpr.Constant -> literal(expr.value.toFloatArray())
            is ShaderExpr.Construct -> bind(AslConstruct(shapeOf(expr.size), expr.args.map(::operand)))
            is ShaderExpr.Param -> values.parameters.getValue(expr.name)
            is ShaderExpr.Input -> values.inputs.getValue(expr.input)
            is ShaderExpr.Local -> local(scope, expr.name).ref
            is ShaderExpr.Arithmetic -> bind(AslBinary(expr.symbol, operand(expr.a), operand(expr.b)))
            is ShaderExpr.Negate -> bind(AslBinary("*", operand(expr.value), AslLiteral(-1f)))
            is ShaderExpr.Comparison -> bind(AslBinary(expr.symbol, operand(expr.a), operand(expr.b)))
            is ShaderExpr.Logical -> bind(AslBinary(expr.symbol, operand(expr.a), operand(expr.b)))
            is ShaderExpr.Not -> bind(AslUnary("!", operand(expr.value)))
            is ShaderExpr.Swizzle -> bind(AslSwizzle(operand(expr.value), expr.components))
            is ShaderExpr.Call -> bind(lowerCall(expr.fn, expr.args.map(::operand)))
            is ShaderExpr.Sample -> bind(
                textureSampleLevel(
                    values.textures.getValue(expr.texture),
                    requireNotNull(values.sampler) { "a document that samples declares a sampler" },
                    operand(expr.uv),
                    0f.lit,
                ),
            )
        }
    }

    /** The value of [expr] when it is known before the shader runs. Conditions are never folded. */
    private fun constantOf(expr: ShaderExpr, scope: Scope<LoweredLocal>): FloatArray? {
        fun all(parts: List<ShaderExpr>, fold: (List<FloatArray>) -> FloatArray?): FloatArray? {
            val folded = parts.map { constantOf(it, scope) }
            return if (folded.all { it != null }) fold(folded.requireNoNulls()) else null
        }
        return when (expr) {
            is ShaderExpr.Constant -> expr.value.toFloatArray()
            is ShaderExpr.Construct -> all(expr.args) { foldConstruct(it, expr.size) }
            is ShaderExpr.Local -> scope.find(expr.name)?.constant
            is ShaderExpr.Arithmetic -> all(listOf(expr.a, expr.b)) { (a, b) -> foldArithmetic(expr, a, b) }
            is ShaderExpr.Negate -> constantOf(expr.value, scope)?.let(::foldNegate)
            is ShaderExpr.Swizzle -> constantOf(expr.value, scope)?.let { foldSwizzle(it, expr.components) }
            is ShaderExpr.Call -> all(expr.args) { foldCall(expr.fn, it) }
            else -> null
        }
    }

    private fun local(scope: Scope<LoweredLocal>, name: String): LoweredLocal =
        requireNotNull(scope.find(name)) { "the checker resolved local '$name'" }
}

/** A folded value as a WGSL literal: a number, or a vector of numbers. */
internal fun literal(value: FloatArray): AslExpr =
    if (value.size == 1) AslLiteral(value[0]) else AslConstruct(shapeOf(value.size), value.map { AslLiteral(it) })

/** The ASL shape of an n-component value. */
internal fun shapeOf(components: Int): GpuDataShape = when (components) {
    1 -> GpuDataShape.Float
    2 -> GpuDataShape.Vec2
    3 -> GpuDataShape.Vec3
    else -> GpuDataShape.Vec4
}

/** The ASL data shape of a lowered value. */
internal val AslExpr.dataShape: GpuDataShape?
    get() = (type as? AslType.Data)?.shape
