/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Property-delegate provider shared by uniform fields, varyings, inputs, and resources.
 *
 * The Kotlin property's own name becomes the WGSL identifier, so `val mvp by uniforms.field(...)`
 * declares a struct field named "mvp" with no string repeated.
 */
class AslHandleProvider<T> internal constructor(
    private val registrar: (String) -> T,
) {
    /**
     * Provides the delegate for the Kotlin property.
     */
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, T> {
        val handle = register(property.name)
        return ReadOnlyProperty { _, _ -> handle }
    }

    /**
     * Explicit-name registration -- for handles derived from a format/layout rather than a
     * Kotlin property.
     *
     * @param name The WGSL identifier name.
     * @return The registered handle.
     */
    internal fun register(name: String): T = registrar(name)
}

/**
 * A fixed-size array uniform field handle.
 *
 * Indexable, never usable as a bare expression, so a whole-array read (which WGSL uniforms
 * can't do either) is unrepresentable.
 */
class AslArrayHandle internal constructor(
    internal val wgslName: String,
    internal val elementShape: GpuDataShape,
    internal val count: Int,
) {
    /**
     * Accesses an element of the array using an expression index.
     *
     * @param index The expression representing the index.
     * @return An [AslExpr] representing the accessed element.
     */
    operator fun get(index: AslExpr): AslExpr = AslIndex(wgslName, index, elementShape)

    /**
     * Accesses an element of the array using a constant integer index.
     *
     * @param index The constant integer index.
     * @return An [AslExpr] representing the accessed element.
     */
    operator fun get(index: Int): AslExpr = get(index.lit)
}

/**
 * Represents a uniform block in the shader.
 *
 * @property structName The name of the WGSL struct.
 * @property group The bind group index.
 * @property binding The binding index.
 */
@AslDsl
class AslUniformBlock internal constructor(
    val structName: String,
    val group: Int,
    val binding: Int,
) {
    /** The instance name of the uniform block in WGSL. */
    val instanceName: String = structName.replaceFirstChar { it.lowercaseChar() }
    internal val fields = mutableListOf<AslUniformField>()

    /**
     * Declares a uniform field with the specified [shape].
     *
     * @param shape The data shape of the field.
     * @return A handle provider for the field reference.
     */
    fun field(shape: GpuDataShape): AslHandleProvider<AslRef> =
        AslHandleProvider { name -> namedField(name, shape) }

    /**
     * Declares a fixed-size array uniform field.
     *
     * @param shape The data shape of the array elements.
     * @param count The number of elements in the array.
     * @return A handle provider for the array handle.
     */
    fun fieldArray(shape: GpuDataShape, count: Int): AslHandleProvider<AslArrayHandle> =
        AslHandleProvider { name -> namedFieldArray(name, shape, count) }

    /**
     * Explicitly named uniform field declaration.
     *
     * @param name The name of the field.
     * @param shape The data shape of the field.
     * @return An [AslRef] for the field.
     */
    fun namedField(name: String, shape: GpuDataShape): AslRef {
        fields += AslUniformField(name, AslType.Data(shape))
        return AslRef("$instanceName.$name", shape)
    }

    /** Declares a scalar unsigned integer uniform. */
    fun fieldU32(): AslHandleProvider<AslRef> = AslHandleProvider { name ->
        fields += AslUniformField(name, AslType.U32)
        AslRef("$instanceName.$name", AslType.U32)
    }

    /**
     * Explicitly named array uniform field declaration.
     *
     * @param name The name of the array field.
     * @param shape The data shape of the elements.
     * @param count The number of elements.
     * @return An [AslArrayHandle] for the field.
     */
    fun namedFieldArray(name: String, shape: GpuDataShape, count: Int): AslArrayHandle {
        fields += AslUniformField(name, AslType.Data(shape), count)
        return AslArrayHandle("$instanceName.$name", shape, count)
    }
}

/**
 * Represents the varyings struct shared between stages.
 *
 * @property structName The name of the WGSL struct.
 */
@AslDsl
class AslVaryings internal constructor(val structName: String) {
    /**
     * `@builtin(position)` -- always present, written by the vertex stage, never a fragment
     * input parameter.
     */
    val position: AslVaryingRef = AslVaryingRef("position", GpuDataShape.Vec4)
    internal val fields = mutableListOf<AslVaryingField>()

    /**
     * Declares a varying field with the specified [shape] at [location].
     *
     * @param shape The data shape of the varying.
     * @param location The `@location` index.
     * @return A handle provider for the varying reference.
     */
    fun varying(shape: GpuDataShape, location: Int): AslHandleProvider<AslVaryingRef> {
        if (fields.any { it.location == location }) {
            throw AslDefinitionException("Varying location $location declared twice in $structName.")
        }
        return AslHandleProvider { name ->
            fields += AslVaryingField(name, shape, location)
            AslVaryingRef(name, shape)
        }
    }
}

/**
 * Callable handle for a module-scope [AslFunctionDef].
 *
 * `sampleShadow(pos, nDotL)` becomes the WGSL call with argument count and (data-)type
 * checked at definition time.
 */
class AslFunctionHandle internal constructor(internal val def: AslFunctionDef) {
    /**
     * Invokes the function with the provided [args].
     *
     * @param args The expression arguments.
     * @return An [AslExpr] representing the function call.
     */
    operator fun invoke(vararg args: AslExpr): AslExpr {
        if (args.size != def.params.size) {
            throw AslDefinitionException(
                "${def.name} takes ${def.params.size} args, got ${args.size}.",
            )
        }
        def.params.zip(args).forEach { (param, arg) ->
            if (param.type != arg.type) {
                throw AslDefinitionException(
                    "${def.name}(${param.name}) wants ${param.type}, got ${arg.type}.",
                )
            }
        }
        return AslCall(def.name, args.toList(), def.returnType)
    }
}
