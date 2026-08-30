/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.render.renderer.UniformLayout

/**
 * Field handles declared from a [UniformLayout].
 *
 * Looked up by name so a renamed or re-kinded layout field breaks the shader definition
 * loudly at build time.
 */
class AslLayoutHandles internal constructor(
    private val values: Map<String, AslRef>,
    private val arrays: Map<String, AslArrayHandle>,
) {
    /**
     * Gets a scalar, vector, or matrix field handle by [name].
     *
     * @param name The name of the field.
     * @return The field handle.
     */
    fun value(name: String): AslRef = values[name]
        ?: throw AslDefinitionException("Layout has no scalar/vector/matrix field '$name'.")

    /**
     * Gets an array field handle by [name].
     *
     * @param name The name of the field.
     * @return The array field handle.
     */
    fun array(name: String): AslArrayHandle = arrays[name]
        ?: throw AslDefinitionException("Layout has no array field '$name'.")
}

/**
 * Declare this block's fields straight from [layout] -- the renderer's own packing
 * description (names, shapes, array counts) becomes the WGSL struct, so the struct cannot
 * drift from what the CPU side writes.
 *
 * @param layout The uniform layout to derive fields from.
 * @param throughField Optional name of a field to stop at (inclusive).
 * @return The generated [AslLayoutHandles].
 */
fun AslUniformBlock.fieldsFrom(layout: UniformLayout, throughField: String? = null): AslLayoutHandles {
    val values = mutableMapOf<String, AslRef>()
    val arrays = mutableMapOf<String, AslArrayHandle>()
    for (field in layout.fields) {
        if (field.count > 1) {
            arrays[field.name] = namedFieldArray(field.name, field.type, field.count)
        } else {
            values[field.name] = namedField(field.name, field.type)
        }
        if (field.name == throughField) return AslLayoutHandles(values, arrays)
    }
    if (throughField != null) {
        throw AslDefinitionException("Layout has no field '$throughField' to stop at.")
    }
    return AslLayoutHandles(values, arrays)
}
