/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GpuShadowCascadeDataTest {

    @Test
    fun cascadePayloadSizesComeFromTheDeclaredUniformFields() {
        val data = GpuShadowCascadeData(
            viewProjections = listOf(Mat4()),
            splitDistances = floatArrayOf(10f),
            depthScales = floatArrayOf(0.25f),
            worldExtents = floatArrayOf(8f),
        )

        assertEquals(UniformFields.CascadeDepthScales.floats, data.depthScaleFloats().size)
        assertEquals(UniformFields.CascadeViewProjections.floats, data.matrixFloats().size)
    }

    @Test
    fun cascadePayloadRepeatsTheLastCascadeForUniformPadding() {
        val matrix = Mat4().apply { data.fill(0f) }
        matrix.data[0] = 2f
        val data = GpuShadowCascadeData(
            viewProjections = listOf(matrix),
            splitDistances = floatArrayOf(10f),
            depthScales = floatArrayOf(0.25f),
            worldExtents = floatArrayOf(8f),
        )

        val depth = data.depthScaleFloats()
        val matrices = data.matrixFloats()
        val depthStride = UniformFields.CascadeDepthScales.floats / UniformFields.CascadeDepthScales.count
        val matrixStride = UniformFields.CascadeViewProjections.floats /
            UniformFields.CascadeViewProjections.count

        assertContentEquals(floatArrayOf(0.25f, 8f, 0f, 0f), depth.copyOfRange(0, depthStride))
        assertContentEquals(
            matrices.copyOfRange(0, matrixStride),
            matrices.copyOfRange(matrixStride, matrixStride * 2),
        )
    }
}
