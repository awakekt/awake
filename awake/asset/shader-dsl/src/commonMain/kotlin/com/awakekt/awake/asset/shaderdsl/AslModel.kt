/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat

/**
 * Marker for ASL DSL classes.
 */
@DslMarker
annotation class AslDsl

/**
 * Base interface for all ASL statements.
 */
sealed interface AslStatement

/**
 * Vertex-stage varying write -- `output.<name> = value`.
 *
 * @property target The varying reference to assign to.
 * @property value The expression value to write.
 */
class AslAssign(val target: AslVaryingRef, val value: AslExpr) : AslStatement

/**
 * Immutable local variable declaration -- `let name = value;`.
 *
 * @property name The name of the variable.
 * @property value The expression value.
 */
class AslLet(val name: String, val value: AslExpr) : AslStatement

/**
 * Mutable local variable declaration -- `var name = value;`.
 *
 * @property name The name of the variable.
 * @property value The initial expression value.
 */
class AslVar(val name: String, val value: AslExpr) : AslStatement

/**
 * Reassignment to a mutable local variable -- `name = value;`.
 *
 * @property name The name of the variable.
 * @property value The new expression value.
 */
class AslSet(val name: String, val value: AslExpr) : AslStatement

/**
 * An `if` statement.
 *
 * @property condition The boolean condition expression.
 * @property body The list of statements in the `if` block.
 */
class AslIf(val condition: AslExpr, val body: List<AslStatement>) : AslStatement

/**
 * A `for` loop with an `i32` counter.
 *
 * `for (var c = start; c <= endInclusive; c = c + 1)`
 *
 * @property counter The name of the counter variable.
 * @property start The starting value.
 * @property endInclusive The inclusive end value.
 * @property body The list of statements in the loop body.
 */
class AslForI32(
    val counter: String,
    val start: AslExpr,
    val endInclusive: AslExpr,
    val body: List<AslStatement>,
) : AslStatement

/**
 * A `for` loop with a `u32` counter.
 *
 * `for (var c = start; c < endExclusive; c = c + 1u)`
 *
 * @property counter The name of the counter variable.
 * @property start The starting value.
 * @property endExclusive The exclusive end value.
 * @property body The list of statements in the loop body.
 */
class AslForU32(
    val counter: String,
    val start: AslExpr,
    val endExclusive: AslExpr,
    val body: List<AslStatement>,
) : AslStatement

/**
 * A `return` statement.
 *
 * @property value The expression value to return, or null for a bare `return;`.
 */
class AslReturn(val value: AslExpr?) : AslStatement

/**
 * A `continue` statement.
 */
object AslContinue : AslStatement

/**
 * Definition of a field within a uniform block.
 *
 * @property name The name of the field.
 * @property type The data shape of the field.
 * @property count The number of elements if it's an array field.
 */
class AslUniformField(val name: String, val type: AslType, val count: Int = 1) {
    init {
        if (count < 1) throw AslDefinitionException("Field '$name' declares count=$count.")
    }
}

/**
 * Definition of a varying field shared between stages.
 *
 * @property name The name of the field.
 * @property shape The data shape of the field.
 * @property location The `@location` index.
 */
class AslVaryingField(val name: String, val shape: GpuDataShape, val location: Int)

/**
 * Definition of a vertex stage input attribute.
 *
 * @property name The name of the input.
 * @property shape The data shape of the input.
 * @property location The `@location` index.
 */
class AslVertexInput(val name: String, val shape: GpuDataShape, val location: Int)

/**
 * Module-scope `const` definition.
 *
 * @property name The name of the constant.
 * @property value The constant-evaluable expression value.
 */
class AslConst(val name: String, val value: AslExpr)

/**
 * A texture or sampler resource binding.
 *
 * @property name The name of the resource.
 * @property group The bind group index.
 * @property binding The binding index.
 * @property type The resource type (texture or sampler).
 */
class AslTextureBinding(val name: String, val group: Int, val binding: Int, val type: AslType)

/**
 * A storage buffer binding representing an array of structures.
 *
 * @property varName The name of the storage variable.
 * @property structName The name of the WGSL struct.
 * @property group The bind group index.
 * @property binding The binding index.
 * @property fieldName The name of the field in the structure.
 * @property elementShape The data shape of the array elements.
 * @property elementCount The number of elements in the array.
 */
@Suppress("LongParameterList") // Mirrors the WGSL declaration's own seven facts one-to-one.
class AslStorageBinding(
    val varName: String,
    val structName: String,
    val group: Int,
    val binding: Int,
    val fieldName: String,
    val elementShape: GpuDataShape,
    val elementCount: Int,
)

/**
 * A function parameter definition.
 *
 * @property name The name of the parameter.
 * @property type The type of the parameter.
 */
class AslParam(val name: String, val type: AslType)

/**
 * A module-scope helper function definition.
 *
 * @property name The name of the function.
 * @property params The list of function parameters.
 * @property returnType The function return type.
 * @property body The list of statements in the function body.
 */
class AslFunctionDef(
    val name: String,
    val params: List<AslParam>,
    val returnType: AslType,
    val body: List<AslStatement>,
)

/**
 * Definition of a vertex stage.
 *
 * @property entryPoint The entry point function name.
 * @property inputs The list of input attributes.
 * @property statements The list of statements in the stage body.
 * @property positionOnly The expression returning `@builtin(position)` if no varyings struct is used.
 * @property vertexFormat The vertex format if derived from inputs.
 * @property usesInstanceIndex Whether the stage uses `@builtin(instance_index)`.
 * @property usesVertexIndex Whether the stage uses `@builtin(vertex_index)`.
 */
class AslVertexStage(
    val entryPoint: String,
    val inputs: List<AslVertexInput>,
    val statements: List<AslStatement>,
    val positionOnly: AslExpr? = null,
    val vertexFormat: VertexFormat? = null,
    val usesInstanceIndex: Boolean = false,
    val usesVertexIndex: Boolean = false,
)

/**
 * Definition of a fragment stage.
 *
 * @property entryPoint The entry point function name.
 * @property statements The list of statements in the stage body.
 * @property returnValue The expression returning the color output value.
 * @property usesPosition Whether the stage reads the fragment's built-in position.
 */
class AslFragmentStage(
    val entryPoint: String,
    val statements: List<AslStatement>,
    val returnValue: AslExpr?,
    val usesPosition: Boolean = false,
)

/**
 * A complete, validated shader definition.
 *
 * Everything past [name] is the builder's validated output, kept internal because a definition is
 * emitted rather than inspected: [toWgsl] is the supported way to read one.
 *
 * @property name The name of the shader.
 * @property consts Compile-time constants declared at module scope.
 * @property uniformBlocks Uniform blocks, in binding order.
 * @property textures Texture bindings, in binding order.
 * @property storageBindings Storage buffer bindings, in binding order.
 * @property functions Free functions callable from either stage.
 * @property varyings The interpolants passed from the vertex stage to the fragment stage.
 * @property vertexStage The vertex stage.
 * @property fragmentStage The fragment stage.
 */
@Suppress("LongParameterList") // Pure aggregation of the builder's parts; one internal call site.
class AslShaderDefinition internal constructor(
    val name: String,
    internal val consts: List<AslConst>,
    internal val uniformBlocks: List<AslUniformBlock>,
    internal val textures: List<AslTextureBinding>,
    internal val storageBindings: List<AslStorageBinding>,
    internal val functions: List<AslFunctionDef>,
    internal val varyings: AslVaryings?,
    internal val vertexStage: AslVertexStage,
    internal val fragmentStage: AslFragmentStage,
) {
    /**
     * The [VertexFormat] derived from the vertex stage inputs, if available.
     */
    val vertexFormat: VertexFormat? get() = vertexStage.vertexFormat

    /**
     * Emits the complete WGSL source code for this shader definition.
     *
     * @return The emitted WGSL string.
     */
    fun emitWgsl(): String = WgslEmitter(this).emit()
}
