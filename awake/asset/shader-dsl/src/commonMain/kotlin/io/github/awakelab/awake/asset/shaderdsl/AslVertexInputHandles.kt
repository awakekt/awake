/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic

/**
 * Vertex inputs declared from a [VertexFormat].
 *
 * Looked up by semantic, so a shader body can't read an attribute the format doesn't carry.
 */
class AslVertexInputHandles internal constructor(
    private val inputs: Map<VertexSemantic, AslRef>,
) {
    /**
     * Gets a vertex input handle by [semantic].
     *
     * @param semantic The vertex semantic.
     * @return The input reference.
     */
    fun input(semantic: VertexSemantic): AslRef = inputs[semantic]
        ?: throw AslDefinitionException("Vertex format has no $semantic attribute.")
}

/**
 * Declare this stage's inputs straight from [format] -- the same [VertexFormat] the pipeline
 * is keyed on, so locations and shapes cannot drift from what the mesh actually interleaves.
 *
 * Parameter names derive from the semantic ("in" + semantic: `inPosition`, `inNormal`, ...).
 *
 * @param format The vertex format to derive inputs from.
 * @return The generated [AslVertexInputHandles].
 */
fun AslVertexBuilder.inputsFrom(format: VertexFormat): AslVertexInputHandles {
    recordFormat(format)
    val handles = format.attributes.associate { attribute ->
        val provider = input(attribute.format, attribute.location)
        val ref = provider.register("in${attribute.semantic.name}")
        attribute.semantic to ref
    }
    return AslVertexInputHandles(handles)
}

/**
 * The per-instance model matrix arriving as four instance-rate vec4 columns at
 * [startLocation]..+3 (column-major, `Mat4.data`'s own order).
 *
 * Returns the `mat4x4<f32>(model0, ..., model3)` construction for the body to `let`.
 *
 * @param startLocation The starting attribute location index.
 * @return An [AslExpr] representing the constructed model matrix.
 */
fun AslVertexBuilder.instanceModelMatrix(startLocation: Int): AslExpr {
    val columns = (0..3).map { column ->
        input(GpuDataShape.Vec4, startLocation + column).register("model$column")
    }
    return mat4(columns[0], columns[1], columns[2], columns[3])
}
