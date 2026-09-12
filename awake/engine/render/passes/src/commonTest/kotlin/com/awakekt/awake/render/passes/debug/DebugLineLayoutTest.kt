/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.debug

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the debug-line vertex layout, which both backends' line pipelines now derive from.
 *
 * They used to hardcode it -- locations, formats and byte offsets restated in Vulkan's
 * `LineRenderPipeline` and again in WebGPU's, so the same layout existed in three places and
 * only agreed by luck. `awake-render-pipeline`'s "never hardcode vertex attribute descriptions"
 * rule names lines specifically, for exactly this reason.
 *
 * With both derived, this is the single place the numbers live, so it is worth asserting them:
 * a wrong offset here now reaches both backends at once, where before it would have reached
 * neither.
 */
class DebugLineLayoutTest {

    @Test
    fun positionThenColour() {
        val entries = DebugLineLayout.Format.entries

        assertEquals(2, entries.size, "position + colour, nothing else")
        assertEquals(VertexSemantic.Position, entries[0].attribute.semantic)
        assertEquals(VertexSemantic.Color, entries[1].attribute.semantic)
    }

    /** The shader reads position at 0 and colour at 1; swapping them renders a coloured
     * position, which draws without erroring. */
    @Test
    fun locationsMatchTheShadersDeclaredInputs() {
        val entries = DebugLineLayout.Format.entries

        assertEquals(0, entries[0].attribute.location)
        assertEquals(1, entries[1].attribute.location)
    }

    @Test
    fun offsetsAndStrideAreUnpaddedFloats() {
        val entries = DebugLineLayout.Format.entries

        assertEquals(GpuDataShape.Vec3, entries[0].attribute.format)
        assertEquals(0, entries[0].offsetBytes)
        assertEquals(GpuDataShape.Vec4, entries[1].attribute.format)
        assertEquals(
            VEC3_BYTES,
            entries[1].offsetBytes,
            "colour follows position with no padding -- a vertex buffer is unpadded, unlike a " +
                "std140 uniform block",
        )
        assertEquals(VEC3_BYTES + VEC4_BYTES, DebugLineLayout.Format.strideBytes)
    }

    @Test
    fun floatsPerVertexIsDerivedFromTheStride() {
        assertEquals(
            DebugLineLayout.Format.strideBytes / Float.SIZE_BYTES,
            DebugLineLayout.FLOATS_PER_VERTEX,
        )
    }

    @Test
    fun mvpUniformSizeIsDerivedFromTheSharedLayout() {
        assertEquals(UniformFields.Mvp.floats, DebugLineUniformLayout.total)
    }

    private companion object {
        const val VEC3_BYTES = 3 * Float.SIZE_BYTES
        const val VEC4_BYTES = 4 * Float.SIZE_BYTES
    }
}
