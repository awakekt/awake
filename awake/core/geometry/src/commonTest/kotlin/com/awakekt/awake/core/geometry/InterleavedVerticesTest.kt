/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That an attribute lands where its format says, not where the caller counted to.
 *
 * The bug this exists to make impossible is silent: a producer whose write order disagrees with
 * the format it declares still produces a mesh that loads and draws, with colours read as normals.
 * Nothing throws and nothing points at the packing loop.
 */
class InterleavedVerticesTest {

    @Test
    fun anAttributeLandsAtTheOffsetTheFormatDeclares() {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, vertexCount = 2)

        // Written out of order on purpose: the format decides placement, not the call sequence.
        vertices.put(1, VertexSemantic.Color, 0.1f, 0.2f, 0.3f)
        vertices.put(0, VertexSemantic.Position, 1f, 2f, 3f)
        vertices.put(0, VertexSemantic.Normal, 0f, 1f, 0f)

        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0f, 1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0.2f, 0.3f,
            ),
            vertices.toFloatArray(),
        )
    }

    @Test
    fun theStrideComesFromTheFormat() {
        val positionOnly = InterleavedVertices(VertexFormat.PositionUv, vertexCount = 3)

        // Position vec3 plus uv vec2 is five floats -- the number no caller should be restating.
        assertEquals(3 * 5, positionOnly.toFloatArray().size)
    }

    @Test
    fun writingAnAttributeTheFormatDoesNotHaveIsRefused() {
        val vertices = InterleavedVertices(VertexFormat.PositionColor, vertexCount = 1)

        assertFalse(vertices.has(VertexSemantic.Uv))
        // Loudly, rather than by scribbling over whatever occupies those floats. A caller that
        // wants to skip a missing attribute has `has` to ask with.
        assertFailsWith<IllegalArgumentException> {
            vertices.put(0, VertexSemantic.Uv, 0.5f, 0.5f)
        }
    }

    @Test
    fun aMissingSourceArrayBecomesItsDefault() {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, vertexCount = 2)

        vertices.copyOrFill(VertexSemantic.Position, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), 3)
        // The importer case: this file carried no normals, so every vertex gets up.
        vertices.copyOrFill(VertexSemantic.Normal, null, 3, 0f, 1f, 0f)
        vertices.fill(VertexSemantic.Color, 1f, 1f, 1f)

        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0f, 1f, 0f, 1f, 1f, 1f,
                4f, 5f, 6f, 0f, 1f, 0f, 1f, 1f, 1f,
            ),
            vertices.toFloatArray(),
        )
    }

    @Test
    fun copyingAnAttributeTheFormatDoesNotHaveIsSkippedRatherThanRefused() {
        val vertices = InterleavedVertices(VertexFormat.PositionColor, vertexCount = 1)

        // Unlike put: a bulk copy is how an importer offers everything the file had, and a format
        // with fewer slots than the file is ordinary rather than a mistake.
        vertices.copyOrFill(VertexSemantic.Uv, floatArrayOf(0.5f, 0.5f), 2)
        vertices.put(0, VertexSemantic.Position, 1f, 1f, 1f)

        assertContentEquals(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f), vertices.toFloatArray())
    }

    @Test
    fun anAttributeOffsetCanBeLookedUpForCodeThatEditsAPackedBuffer() {
        val format = VertexFormat.PositionNormalColorUv

        // Position(3) + Normal(3) puts colour at 6 -- the number a clipmap builder used to keep as
        // its own constant beside its own stride.
        assertEquals(0, format.floatOffsetOf(VertexSemantic.Position))
        assertEquals(6, format.floatOffsetOf(VertexSemantic.Color))
        assertEquals(9, format.floatOffsetOf(VertexSemantic.Uv))
        // Absent rather than zero, so a caller cannot mistake "not here" for "at the start".
        assertEquals(-1, format.floatOffsetOf(VertexSemantic.JointWeights))
    }

    @Test
    fun theBuiltGeometryCarriesTheFormatItWasWrittenThrough() {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColorUv, vertexCount = 3)
        vertices.fill(VertexSemantic.Color, 1f, 1f, 1f)

        val geometry = vertices.build(intArrayOf(0, 1, 2))

        // The pairing that made hand-packing dangerous: buffer and format travel together, so
        // nothing downstream has to be told which layout it was handed.
        assertEquals(VertexFormat.PositionNormalColorUv, geometry.format)
        assertTrue(geometry.vertices.size == 3 * VertexFormat.PositionNormalColorUv.strideFloats)
    }
}
