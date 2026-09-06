/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * A structurally invalid shader definition -- thrown while the Kotlin definition is being
 * built, before naga ever sees the emitted WGSL. Genuine type errors stay naga's job.
 */
class AslDefinitionException(message: String) : IllegalStateException(message)

/**
 * One node of an ASL expression tree. Every node knows its [AslType]; that type powers
 * structural checks (arity, undeclared varyings) and the CPU evaluator, not a type system.
 */
sealed interface AslExpr {
    /** The inferred or declared type of this expression. */
    val type: AslType
}

/** Float data shape of this expression, or a structural error where non-data sneaks into a
 * data-only position (varying write, constructor argument, swizzle base). */
internal fun AslExpr.dataShape(): GpuDataShape = type.dataShapeOrNull()
    ?: throw AslDefinitionException("Expected float data here, got $type.")

/**
 * A literal value in a shader expression.
 *
 * @property value The numeric value.
 * @property type The type of the literal (defaulting to [F32]).
 */
class AslLiteral(val value: Float, override val type: AslType = F32) : AslExpr

/**
 * A named reference in a shader expression.
 *
 * Used for uniform fields, stage inputs, local variables, constants, or function parameters.
 *
 * @property wgslName The verbatim name used in the emitted WGSL.
 * @property type The type of the reference.
 */
class AslRef(val wgslName: String, override val type: AslType) : AslExpr {
    /** Creates a reference with a [shape] converted to [AslType.Data]. */
    constructor(wgslName: String, shape: GpuDataShape) : this(wgslName, AslType.Data(shape))
}

/**
 * A reference to a varying variable shared between shader stages.
 *
 * @property name The name of the varying.
 * @property shape The data shape of the varying.
 */
class AslVaryingRef(val name: String, val shape: GpuDataShape) : AslExpr {
    override val type: AslType get() = AslType.Data(shape)
}

/**
 * A vector or matrix swizzle expression (e.g., `vec.xyz`).
 *
 * @property base The base expression being swizzled.
 * @property components The swizzle components string (e.g., "xyz").
 */
class AslSwizzle(val base: AslExpr, val components: String) : AslExpr {
    override val type: AslType = swizzleType(base.type, components)

    init {
        val limit = base.type.componentCountOrZero()
        val valid = base.type.dataShapeOrNull() != GpuDataShape.Mat4 &&
            components.all { swizzleIndex(it) < limit }
        if (!valid) {
            throw AslDefinitionException("Cannot swizzle '$components' from ${base.type}.")
        }
    }
}

/** A uint vector component reads as `u32` (joint indices); float data swizzles as usual. */
private fun swizzleType(base: AslType, components: String): AslType =
    if (base == AslType.Vec2U || base.dataShapeOrNull() == GpuDataShape.UInt4) {
        if (components.length != 1) {
            throw AslDefinitionException("Multi-component uint swizzles are unsupported.")
        }
        AslType.U32
    } else {
        AslType.Data(shapeOfComponents(components.length))
    }

/**
 * A binary operation expression (e.g., `a + b`).
 *
 * @property op The operator symbol.
 * @property left The left operand.
 * @property right The right operand.
 */
class AslBinary(val op: String, val left: AslExpr, val right: AslExpr) : AslExpr {
    override val type: AslType = binaryType(op, left.type, right.type)
}

/**
 * A unary operation expression (e.g., `-a`).
 *
 * @property op The operator symbol.
 * @property operand The operand expression.
 */
class AslUnary(val op: String, val operand: AslExpr) : AslExpr {
    override val type: AslType = operand.type
}

/**
 * An array indexing expression (e.g., `array[index]`).
 *
 * @property arrayName The name of the array being indexed.
 * @property index The expression representing the index.
 * @property elementShape The data shape of the array elements.
 */
class AslIndex(val arrayName: String, val index: AslExpr, val elementShape: GpuDataShape) : AslExpr {
    override val type: AslType get() = AslType.Data(elementShape)

    init {
        if (index.type != AslType.I32 && index.type != AslType.U32) {
            throw AslDefinitionException("Array index must be i32/u32, got ${index.type}.")
        }
    }
}

/**
 * A chained indexing expression for nested structures (e.g., `storage[i].field[j]`).
 *
 * @property varName The name of the storage variable.
 * @property outer The outer index expression.
 * @property fieldName The name of the field in the outer structure.
 * @property inner The inner index expression.
 * @property elementShape The data shape of the final elements.
 */
class AslChainIndex(
    val varName: String,
    val outer: AslExpr,
    val fieldName: String,
    val inner: AslExpr,
    val elementShape: GpuDataShape,
) : AslExpr {
    override val type: AslType get() = AslType.Data(elementShape)

    init {
        listOf(outer, inner).forEach {
            if (it.type != AslType.I32 && it.type != AslType.U32) {
                throw AslDefinitionException("Storage indices must be i32/u32, got ${it.type}.")
            }
        }
    }
}

/**
 * A literal array initializer expression.
 *
 * @property shape The shape of the array elements.
 * @property elements The list of expression elements.
 */
class AslArrayLiteral(val shape: GpuDataShape, val elements: List<AslExpr>) : AslExpr {
    override val type: AslType get() = AslType.ArrayData(shape, elements.size)

    init {
        elements.forEach {
            if (it.type != AslType.Data(shape)) {
                throw AslDefinitionException("Array literal wants ${'$'}shape, got ${'$'}{it.type}.")
            }
        }
    }
}

/**
 * A function call expression.
 *
 * @property function The name of the function being called.
 * @property args The list of argument expressions.
 * @property type The return type of the function.
 */
class AslCall(
    val function: String,
    val args: List<AslExpr>,
    override val type: AslType,
) : AslCallExpr

/** Workaround for Dokka's handling of duplicated class names in some toolchains. */
internal sealed interface AslCallExpr : AslExpr

/**
 * A vector construction expression (e.g., `vec3f(1.0, 2.0, 3.0)`).
 *
 * @property shape The target data shape.
 * @property args The list of component expressions.
 */
class AslConstruct(val shape: GpuDataShape, val args: List<AslExpr>) : AslExpr {
    override val type: AslType get() = AslType.Data(shape)

    init {
        val total = args.sumOf { it.type.componentCountOrZero() }
        val splat = args.size == 1 && args[0].type == F32
        val convert = args.size == 1 && args[0].type == AslType.Vec2U && shape == GpuDataShape.Vec2
        if (total != shape.componentCount && !splat && !convert) {
            throw AslDefinitionException(
                "${shape.name} takes ${shape.componentCount} components, got $total.",
            )
        }
    }
}

internal fun swizzleIndex(component: Char): Int = when (component) {
    'x', 'r' -> 0
    'y', 'g' -> 1
    'z', 'b' -> 2
    'w', 'a' -> 3
    else -> throw AslDefinitionException("Unknown swizzle component '$component'.")
}

private fun shapeOfComponents(count: Int): GpuDataShape = when (count) {
    1 -> GpuDataShape.Float
    2 -> GpuDataShape.Vec2
    3 -> GpuDataShape.Vec3
    4 -> GpuDataShape.Vec4
    else -> throw AslDefinitionException("Swizzles produce 1..4 components, got $count.")
}

private val COMPARISON_OPS = setOf("<", "<=", ">", ">=", "==", "!=")
private val LOGICAL_OPS = setOf("||", "&&")

/** Result type of `left op right`. Float data follows componentwise/broadcast/mat4 rules;
 * i32/u32 combine only with themselves; comparisons and logical ops produce [AslType.Bool].
 * Anything else is a structural mistake worth failing before naga. */
private fun binaryType(op: String, left: AslType, right: AslType): AslType = when {
    op in LOGICAL_OPS ->
        if (left == AslType.Bool && right == AslType.Bool) {
            AslType.Bool
        } else {
            throw AslDefinitionException("'$op' needs two bools, got $left/$right.")
        }
    op in COMPARISON_OPS ->
        if (left == right && (left.isScalarNumeric() || left == AslType.Bool)) {
            AslType.Bool
        } else {
            throw AslDefinitionException("'$op' needs matching scalars, got $left/$right.")
        }
    left == AslType.I32 && right == AslType.I32 -> AslType.I32
    left == AslType.U32 && right == AslType.U32 -> AslType.U32
    else -> AslType.Data(binaryDataShape(op, left, right))
}

private fun binaryDataShape(op: String, left: AslType, right: AslType): GpuDataShape {
    val l = left.dataShapeOrNull()
    val r = right.dataShapeOrNull()
    val result = when {
        l == null || r == null -> null
        l == GpuDataShape.UInt4 || r == GpuDataShape.UInt4 -> null
        l == GpuDataShape.Mat4 && r == GpuDataShape.Vec4 && op == "*" -> GpuDataShape.Vec4
        l == r -> l
        l == GpuDataShape.Float -> r
        r == GpuDataShape.Float -> l
        else -> null
    }
    return result ?: throw AslDefinitionException("No '$op' between $left and $right.")
}
