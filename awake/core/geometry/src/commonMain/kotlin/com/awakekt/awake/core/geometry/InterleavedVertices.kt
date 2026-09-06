/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

/**
 * Builds an interleaved vertex buffer by naming attributes, not by counting floats.
 *
 * [VertexFormat] already knows every attribute's offset and the stride; until this existed nothing
 * used it to *write*, so every producer hand-packed `vertices[cursor++] = ...` and had to remember
 * that its write order matched the format it declared. When those two disagree the mesh still
 * loads, still draws, and renders as garbage -- colours in the normal slot, UVs in the colour slot
 * -- with nothing pointing at the packing loop. Naming the attribute makes that disagreement
 * impossible: the offset comes from the format.
 *
 * Writes are by vertex index rather than sequential, so a producer can fill positions in one pass
 * and normals in another -- which is what an importer with separate source arrays actually has.
 *
 * ```
 * val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, count)
 * for (i in 0 until count) {
 *     vertices.put(i, VertexSemantic.Position, x, y, z)
 *     vertices.put(i, VertexSemantic.Normal, nx, ny, nz)
 * }
 * vertices.fill(VertexSemantic.Color, 1f, 1f, 1f)
 * return vertices.build(indices)
 * ```
 */
class InterleavedVertices(val format: VertexFormat, val vertexCount: Int) {
    private val stride = format.strideFloats
    private val floats = FloatArray(vertexCount * stride)

    /** Float offset of each semantic within a vertex, or -1 when the format has no such slot. */
    private val offsets = IntArray(VertexSemantic.entries.size) { -1 }
    private val components = IntArray(VertexSemantic.entries.size)

    init {
        require(vertexCount >= 0) { "vertexCount cannot be negative: $vertexCount" }
        for (entry in format.entries) {
            val slot = entry.attribute.semantic.ordinal
            offsets[slot] = entry.offsetBytes / Float.SIZE_BYTES
            components[slot] = entry.attribute.format.componentCount
        }
    }

    /** Whether this format carries [semantic] at all, for a producer with more data than slots. */
    fun has(semantic: VertexSemantic): Boolean = offsets[semantic.ordinal] >= 0

    fun put(vertex: Int, semantic: VertexSemantic, x: Float) = write(vertex, semantic, x, 0f, 0f, 0f, 1)

    fun put(vertex: Int, semantic: VertexSemantic, x: Float, y: Float) =
        write(vertex, semantic, x, y, 0f, 0f, 2)

    fun put(vertex: Int, semantic: VertexSemantic, x: Float, y: Float, z: Float) =
        write(vertex, semantic, x, y, z, 0f, 3)

    @Suppress("LongParameterList") // A vec4 is four components; naming them is the point.
    fun put(vertex: Int, semantic: VertexSemantic, x: Float, y: Float, z: Float, w: Float) =
        write(vertex, semantic, x, y, z, w, 4)

    /**
     * Copies one attribute for every vertex out of a tightly packed source array.
     *
     * What an importer has: positions in one array, normals in another, each `componentsPerVertex`
     * floats per vertex. A `null` source writes [defaults] to every vertex instead, which is the
     * "this glTF file had no normals" case.
     */
    fun copyOrFill(
        semantic: VertexSemantic,
        source: FloatArray?,
        componentsPerVertex: Int,
        vararg defaults: Float,
    ) {
        if (!has(semantic)) return
        if (source == null) {
            fill(semantic, *defaults)
            return
        }
        val slot = semantic.ordinal
        val width = minOf(components[slot], componentsPerVertex)
        val offset = offsets[slot]
        for (vertex in 0 until vertexCount) {
            val from = vertex * componentsPerVertex
            val to = vertex * stride + offset
            for (component in 0 until width) floats[to + component] = source[from + component]
        }
    }

    /** Writes the same value to every vertex -- a constant colour, an up normal, a zero UV. */
    fun fill(semantic: VertexSemantic, vararg values: Float) {
        if (!has(semantic)) return
        val slot = semantic.ordinal
        val width = minOf(components[slot], values.size)
        val offset = offsets[slot]
        for (vertex in 0 until vertexCount) {
            val to = vertex * stride + offset
            for (component in 0 until width) floats[to + component] = values[component]
        }
    }

    /** The packed buffer itself, for a caller that wants the floats rather than a [MeshGeometry]. */
    fun toFloatArray(): FloatArray = floats

    fun build(indices: IntArray): MeshGeometry = MeshGeometry(floats, indices, format)

    @Suppress("LongParameterList") // Four components and an arity; the overloads above are the API.
    private fun write(
        vertex: Int,
        semantic: VertexSemantic,
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        arity: Int,
    ) {
        val slot = semantic.ordinal
        val offset = offsets[slot]
        require(offset >= 0) {
            "this format has no $semantic attribute, so there is nowhere to put one: $format"
        }
        val base = vertex * stride + offset
        val width = minOf(components[slot], arity)
        if (width > 0) floats[base] = x
        if (width > 1) floats[base + 1] = y
        if (width > 2) floats[base + 2] = z
        if (width > 3) floats[base + 3] = w
    }
}
