/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat

/**
 * Entry point: build and structurally validate a shader definition.
 *
 * @param name must match the `shaderSet(name)` lookup and the emitted `.wgsl` filename,
 * exactly like a hand-written file.
 * @param block The builder block to define the shader.
 * @return A validated [AslShaderDefinition].
 */
fun shader(name: String, block: AslShaderBuilder.() -> Unit): AslShaderDefinition =
    AslShaderBuilder(name).apply(block).build()

/**
 * Statement sink shared by stage, function, `if`, and loop bodies -- one grammar everywhere.
 */
@AslDsl
open class AslBlockBuilder internal constructor() {
    internal val statements = mutableListOf<AslStatement>()

    /**
     * Declares an immutable local variable -- WGSL `let`.
     *
     * @param name The name of the variable.
     * @param value The expression value to assign.
     * @return An [AslExpr] reference to the declared variable.
     */
    fun let(name: String, value: AslExpr): AslExpr {
        statements += AslLet(name, value)
        return AslRef(name, value.type)
    }

    /**
     * Declares a mutable local variable -- WGSL `var`. Reassign through [assign].
     *
     * @param name The name of the variable.
     * @param value The initial expression value.
     * @return An [AslExpr] reference to the declared variable.
     */
    fun variable(name: String, value: AslExpr): AslExpr {
        statements += AslVar(name, value)
        return AslRef(name, value.type)
    }

    /**
     * Reassigns a value to a mutable [variable].
     *
     * @param target The variable reference to assign to.
     * @param value The new expression value.
     */
    fun assign(target: AslExpr, value: AslExpr) {
        val ref = target as? AslRef
            ?: throw AslDefinitionException("Only local `variable`s can be assigned.")
        if (ref.type != value.type) {
            throw AslDefinitionException("Assigning ${value.type} into ${ref.type} '${ref.wgslName}'.")
        }
        statements += AslSet(ref.wgslName, value)
    }

    /**
     * An `if` statement block.
     *
     * @param condition The boolean expression condition.
     * @param block The block to execute if the condition is true.
     */
    fun iff(condition: AslExpr, block: AslBlockBuilder.() -> Unit) {
        if (condition.type != AslType.Bool) {
            throw AslDefinitionException("iff needs a bool condition, got ${condition.type}.")
        }
        statements += AslIf(condition, AslBlockBuilder().apply(block).statements)
    }

    /**
     * A `for` loop with an `i32` counter.
     *
     * `for (var c = start; c <= endInclusive; c = c + 1)`
     *
     * @param name The name of the counter variable.
     * @param start The starting expression value.
     * @param endInclusive The inclusive end expression value.
     * @param block The block to execute, receiving the counter reference.
     */
    fun loopI32(name: String, start: AslExpr, endInclusive: AslExpr, block: AslBlockBuilder.(AslExpr) -> Unit) {
        val counter = AslRef(name, AslType.I32)
        statements += AslForI32(name, start, endInclusive, AslBlockBuilder().apply { block(counter) }.statements)
    }

    /**
     * A `for` loop with a `u32` counter.
     *
     * `for (var c = start; c < endExclusive; c = c + 1u)`
     *
     * @param name The name of the counter variable.
     * @param start The starting expression value.
     * @param endExclusive The exclusive end expression value.
     * @param block The block to execute, receiving the counter reference.
     */
    fun loopU32(name: String, start: AslExpr, endExclusive: AslExpr, block: AslBlockBuilder.(AslExpr) -> Unit) {
        val counter = AslRef(name, AslType.U32)
        statements += AslForU32(name, start, endExclusive, AslBlockBuilder().apply { block(counter) }.statements)
    }

    /**
     * Returns a value from a function.
     *
     * @param value The expression value to return.
     */
    fun returnValue(value: AslExpr) {
        statements += AslReturn(value)
    }

    /**
     * Continues to the next iteration of the enclosing loop.
     */
    fun continueLoop() {
        statements += AslContinue
    }

    /**
     * Declares a local fixed-size constant array.
     *
     * `var name = array<shape, N>(...)`; read via indexing.
     *
     * @param name The name of the array.
     * @param shape The shape of the array elements.
     * @param elements The expression elements of the array.
     * @return An [AslArrayHandle] for the declared array.
     */
    fun localArray(name: String, shape: GpuDataShape, vararg elements: AslExpr): AslArrayHandle {
        statements += AslVar(name, AslArrayLiteral(shape, elements.toList()))
        return AslArrayHandle(name, shape, elements.size)
    }
}

/**
 * Builder for top-level shader definitions (consts, uniforms, varyings, functions, stages).
 */
@AslDsl
class AslShaderBuilder internal constructor(private val name: String) {
    private val consts = mutableListOf<AslConst>()
    private val uniformBlocks = mutableListOf<AslUniformBlock>()
    internal val textureBindings = mutableListOf<AslTextureBinding>()
    internal val storageBindings = mutableListOf<AslStorageBinding>()
    private val functions = mutableListOf<AslFunctionDef>()
    private var varyings: AslVaryings? = null
    private var vertexStage: AslVertexStage? = null
    private var fragmentStage: AslFragmentStage? = null

    /**
     * Declares a uniform block at the specified [group] and [binding].
     *
     * @param structName The name of the WGSL struct.
     * @param group The bind group index.
     * @param binding The binding index within the group.
     * @return An [AslUniformBlock] to declare fields in.
     */
    fun uniformBlock(structName: String, group: Int, binding: Int): AslUniformBlock {
        if (uniformBlocks.any { it.group == group && it.binding == binding }) {
            throw AslDefinitionException("Binding ($group, $binding) declared twice in '$name'.")
        }
        return AslUniformBlock(structName, group, binding).also { uniformBlocks += it }
    }

    /**
     * Declares the varyings struct shared between vertex and fragment stages.
     *
     * @param structName The name of the varyings struct.
     * @return An [AslVaryings] to declare varying fields in.
     */
    fun varyings(structName: String): AslVaryings {
        if (varyings != null) {
            throw AslDefinitionException("Shader '$name' already declared a varyings struct.")
        }
        return AslVaryings(structName).also { varyings = it }
    }

    /**
     * Declares a module-scope float constant.
     *
     * @param name The name of the constant.
     * @param value The float value.
     * @return An [AslExpr] reference to the constant.
     */
    fun const(name: String, value: Float): AslExpr = const(name, AslLiteral(value))

    /**
     * Declares a module-scope expression-valued constant.
     *
     * `const LIGHT_DIRECTION : vec3f = vec3f(...)`.
     *
     * @param name The name of the constant.
     * @param value The expression value.
     * @return An [AslExpr] reference to the constant.
     */
    fun const(name: String, value: AslExpr): AslExpr {
        consts += AslConst(name, value)
        return AslRef(name, value.type)
    }

    /**
     * Declares a module-scope `i32` constant.
     *
     * @param name The name of the constant.
     * @param value The integer value.
     * @return An [AslExpr] reference to the constant.
     */
    fun constI32(name: String, value: Int): AslExpr =
        const(name, AslLiteral(value.toFloat(), AslType.I32))

    /**
     * Declares a module-scope helper function.
     *
     * `fn name(...) -> returns { ... }`, called via the returned [AslFunctionHandle].
     *
     * @param name The name of the function.
     * @param returns The return type of the function.
     * @param block The builder block for the function body.
     * @return An [AslFunctionHandle] to call the function.
     */
    fun fn(name: String, returns: AslType = F32, block: AslFunctionBuilder.() -> Unit): AslFunctionHandle {
        val builder = AslFunctionBuilder().apply(block)
        val def = AslFunctionDef(name, builder.params, returns, builder.statements)
        functions += def
        return AslFunctionHandle(def)
    }

    /**
     * Defines the vertex stage of the shader.
     *
     * @param entryPoint The entry point function name.
     * @param block The builder block for the vertex stage.
     */
    fun vertex(entryPoint: String = "vertexMain", block: AslVertexBuilder.() -> Unit) {
        vertexStage = AslVertexBuilder().apply(block).build(entryPoint)
    }

    /**
     * Defines the fragment stage of the shader.
     *
     * @param entryPoint The entry point function name.
     * @param block The builder block for the fragment stage.
     */
    fun fragment(entryPoint: String = "fragmentMain", block: AslFragmentBuilder.() -> Unit) {
        fragmentStage = AslFragmentBuilder().apply(block).build(entryPoint)
    }

    internal fun build(): AslShaderDefinition {
        val vertexStage = vertexStage
        val fragmentStage = fragmentStage
        if (vertexStage == null || fragmentStage == null) {
            throw AslDefinitionException("Shader '$name' needs a vertex and a fragment stage.")
        }
        val definition = AslShaderDefinition(
            name = name,
            consts = consts,
            uniformBlocks = uniformBlocks,
            textures = textureBindings,
            storageBindings = storageBindings,
            functions = functions,
            varyings = varyings,
            vertexStage = vertexStage,
            fragmentStage = fragmentStage,
        )
        validateStages(definition)
        return definition
    }
}

/**
 * Builder for helper functions.
 */
@AslDsl
class AslFunctionBuilder internal constructor() : AslBlockBuilder() {
    internal val params = mutableListOf<AslParam>()

    /**
     * Declares a function parameter with the specified [type].
     *
     * @param type The type of the parameter.
     * @return A handle provider for the parameter reference.
     */
    fun param(type: AslType): AslHandleProvider<AslRef> = AslHandleProvider { name ->
        params += AslParam(name, type)
        AslRef(name, type)
    }

    /**
     * Declares a function parameter with the specified [shape].
     *
     * @param shape The data shape of the parameter.
     * @return A handle provider for the parameter reference.
     */
    fun param(shape: GpuDataShape): AslHandleProvider<AslRef> = param(AslType.Data(shape))
}

/**
 * Builder for the vertex stage.
 */
@AslDsl
class AslVertexBuilder internal constructor() : AslBlockBuilder() {
    internal val inputs = mutableListOf<AslVertexInput>()
    private var positionOnly: AslExpr? = null
    private var vertexFormat: VertexFormat? = null
    private var usesInstanceIndex = false
    private var usesVertexIndex = false

    /**
     * Accesses `@builtin(instance_index)` as a `u32` parameter.
     *
     * @return An [AslExpr] reference to the instance index.
     */
    fun instanceIndex(): AslExpr {
        usesInstanceIndex = true
        return AslRef("instanceIndex", AslType.U32)
    }

    /**
     * Accesses `@builtin(vertex_index)` as a `u32` parameter.
     *
     * @return An [AslExpr] reference to the vertex index.
     */
    fun vertexIndex(): AslExpr {
        usesVertexIndex = true
        return AslRef("vertexIndex", AslType.U32)
    }

    internal fun recordFormat(format: VertexFormat) {
        if (vertexFormat != null) {
            throw AslDefinitionException("inputsFrom called twice in one vertex stage.")
        }
        vertexFormat = format
    }

    /**
     * Declares a vertex input at the specified [location].
     *
     * @param shape The data shape of the input.
     * @param location The attribute location index.
     * @return A handle provider for the input reference.
     */
    fun input(shape: GpuDataShape, location: Int): AslHandleProvider<AslRef> {
        if (inputs.any { it.location == location }) {
            throw AslDefinitionException("Vertex input location $location declared twice.")
        }
        return AslHandleProvider { name ->
            inputs += AslVertexInput(name, shape, location)
            AslRef(name, shape)
        }
    }

    /**
     * Writes a value to a varying reference.
     *
     * @param value The expression value to write.
     */
    infix fun AslVaryingRef.set(value: AslExpr) {
        if (AslType.Data(shape) != value.type) {
            throw AslDefinitionException("Writing ${value.type} into $shape varying '$name'.")
        }
        statements += AslAssign(this, value)
    }

    /**
     * Sets the direct `@builtin(position)` return value.
     *
     * Used in no-varyings form where the stage returns `vec4f` directly.
     *
     * @param value The `Vec4` expression value.
     */
    fun returnPosition(value: AslExpr) {
        if (value.type != AslType.Data(GpuDataShape.Vec4)) {
            throw AslDefinitionException("returnPosition needs Vec4, got ${value.type}.")
        }
        positionOnly = value
    }

    internal fun build(entryPoint: String): AslVertexStage =
        AslVertexStage(
            entryPoint,
            inputs,
            statements,
            positionOnly,
            vertexFormat,
            usesInstanceIndex,
            usesVertexIndex,
        )
}

/**
 * Builder for the fragment stage.
 */
@AslDsl
class AslFragmentBuilder internal constructor() : AslBlockBuilder() {
    private var output: AslExpr? = null
    private var usesPosition = false

    /** Accesses the fragment's pixel position via WGSL `@builtin(position)`. */
    fun position(): AslExpr {
        usesPosition = true
        return AslRef("position", GpuDataShape.Vec4)
    }

    /**
     * Sets the fragment color output value (`@location(0)`).
     *
     * Omit entirely for a depth-only pass.
     *
     * @param value The `Vec4` expression value.
     */
    fun colorOutput(value: AslExpr) {
        if (output != null) {
            throw AslDefinitionException("colorOutput set twice; one color target for now.")
        }
        if (value.type != AslType.Data(GpuDataShape.Vec4)) {
            throw AslDefinitionException("colorOutput needs Vec4, got ${value.type}.")
        }
        output = value
    }

    internal fun build(entryPoint: String): AslFragmentStage =
        AslFragmentStage(entryPoint, statements, output, usesPosition)
}

/**
 * Declares a `texture_2d<f32>` resource at the specified [group] and [binding].
 *
 * @param group The bind group index.
 * @param binding The binding index.
 * @return A handle provider for the texture reference.
 */
fun AslShaderBuilder.texture2d(group: Int, binding: Int): AslHandleProvider<AslRef> =
    AslHandleProvider { name ->
        textureBindings += AslTextureBinding(name, group, binding, AslType.Texture2dF32)
        AslRef(name, AslType.Texture2dF32)
    }

/**
 * Declares a `texture_2d_array<f32>` resource at the specified [group] and [binding].
 *
 * One binding holding N same-sized layers a shader indexes, rather than N separate bindings --
 * a terrain splat's diffuse layers being the case this exists for. Sampled through
 * [textureSampleArrayLevel].
 *
 * @param group The bind group index.
 * @param binding The binding index.
 * @return A handle provider for the texture reference.
 */
fun AslShaderBuilder.texture2dArray(group: Int, binding: Int): AslHandleProvider<AslRef> =
    AslHandleProvider { name ->
        textureBindings += AslTextureBinding(name, group, binding, AslType.Texture2dArrayF32)
        AslRef(name, AslType.Texture2dArrayF32)
    }

/**
 * Declares a `texture_depth_2d` resource at the specified [group] and [binding].
 *
 * @param group The bind group index.
 * @param binding The binding index.
 * @return A handle provider for the texture reference.
 */
fun AslShaderBuilder.textureDepth2d(group: Int, binding: Int): AslHandleProvider<AslRef> =
    AslHandleProvider { name ->
        textureBindings += AslTextureBinding(name, group, binding, AslType.TextureDepth2d)
        AslRef(name, AslType.TextureDepth2d)
    }

/**
 * Declares a sampler resource at the specified [group] and [binding].
 *
 * @param group The bind group index.
 * @param binding The binding index.
 * @return A handle provider for the sampler reference.
 */
fun AslShaderBuilder.sampler(group: Int, binding: Int): AslHandleProvider<AslRef> =
    AslHandleProvider { name ->
        textureBindings += AslTextureBinding(name, group, binding, AslType.Sampler)
        AslRef(name, AslType.Sampler)
    }

/**
 * Handle for accessing runtime-sized storage arrays of single-array-field structs.
 */
class AslStorageArrays internal constructor(private val binding: AslStorageBinding) {
    /**
     * Accesses an element from the storage array: `palettes[outer].fieldName[inner]`.
     *
     * @param outer The index into the outer storage array.
     * @param inner The index into the inner fixed-size array.
     * @return An [AslExpr] representing the accessed element.
     */
    fun element(outer: AslExpr, inner: AslExpr): AslExpr =
        AslChainIndex(binding.varName, outer, binding.fieldName, inner, binding.elementShape)
}

/**
 * Declares a runtime-sized storage array of structs containing a fixed-size array.
 *
 * Used for joint palettes: `palettes[instance].joints[joint]`.
 */
@Suppress("LongParameterList") // Mirrors the WGSL declaration's own seven facts one-to-one.
fun AslShaderBuilder.storageArrayOfArrays(
    structName: String,
    varName: String,
    group: Int,
    binding: Int,
    fieldName: String,
    elementShape: GpuDataShape,
    elementCount: Int,
): AslStorageArrays {
    val record = AslStorageBinding(varName, structName, group, binding, fieldName, elementShape, elementCount)
    storageBindings += record
    return AslStorageArrays(record)
}

/**
 * The NDC corner of a vertex-less full-screen triangle, for this invocation's `vertex_index`.
 *
 * `(-1,-1)`, `(3,-1)`, `(-1,3)`: one oversized triangle rather than a quad's two, whose
 * off-screen corners cover the viewport without a shared diagonal seam where adjacent
 * interpolation can disagree. No vertex buffer is bound, so the pipeline is `VertexFormat.None`.
 *
 * Returns the corner instead of writing `@builtin(position)` itself. Depth is the part callers
 * genuinely differ on -- a sky writes z = 1 so it sits on the far plane, a compositing pass
 * writes z = 0 -- and burying that in a helper would hide the one line worth reading.
 *
 * ```
 * vertex {
 *     val corner = fullScreenTriangleCorner()
 *     out.position set vec4(corner, 0f.lit, 1f.lit)
 * }
 * ```
 */
fun AslVertexBuilder.fullScreenTriangleCorner(): AslExpr {
    val index = vertexIndex()
    val corners = localArray(
        "fullScreenCorners",
        GpuDataShape.Vec2,
        vec2((-1f).lit, (-1f).lit),
        vec2(3f.lit, (-1f).lit),
        vec2((-1f).lit, 3f.lit),
    )
    return let("fullScreenCorner", corners[index])
}
