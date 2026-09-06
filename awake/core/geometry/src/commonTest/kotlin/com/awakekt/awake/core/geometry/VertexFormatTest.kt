/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class VertexFormatTest {

    @Test
    fun positionColorMatchesUnlitLayout() {
        val format = VertexFormat.PositionColor

        assertEquals(24, format.strideBytes)
        assertEquals(listOf(0, 12), format.entries.map { it.offsetBytes })
        assertEquals(
            listOf(VertexSemantic.Position, VertexSemantic.Color),
            format.entries.map { it.attribute.semantic },
        )
    }

    @Test
    fun positionColorUvMatchesTheLayoutEveryBackendPreviouslyHardcoded() {
        val format = VertexFormat.PositionColorUv

        assertEquals(32, format.strideBytes)
        assertEquals(listOf(0, 12, 24), format.entries.map { it.offsetBytes })
        assertEquals(
            listOf(VertexSemantic.Position, VertexSemantic.Color, VertexSemantic.Uv),
            format.entries.map { it.attribute.semantic },
        )
    }

    @Test
    fun offsetsAccumulateInDeclarationOrder() {
        val format = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Normal, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.JointIndices, GpuDataShape.UInt4, location = 2),
                VertexAttribute(VertexSemantic.JointWeights, GpuDataShape.Vec4, location = 3),
            ),
        )

        assertEquals(listOf(0, 12, 24, 40), format.entries.map { it.offsetBytes })
        assertEquals(56, format.strideBytes)
    }

    /** A vertex-generating shader (full-screen triangle) has nothing to stride over, and the
     * backends key "declare no vertex binding" off exactly this emptiness. */
    @Test
    fun noneHasNoAttributesAndZeroStride() {
        assertEquals(emptyList(), VertexFormat.None.entries)
        assertEquals(0, VertexFormat.None.strideBytes)
        assertEquals(0, VertexFormat.None.strideFloats)
    }
}
