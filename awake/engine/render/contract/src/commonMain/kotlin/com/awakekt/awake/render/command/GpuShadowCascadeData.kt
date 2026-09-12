/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.math.sqrt

/** Hardware-ready shadow cascade data. Scene fitting produces this payload; backends consume it. */
class GpuShadowCascadeData(
    val viewProjections: List<Mat4>,
    val splitDistances: FloatArray,
    val depthScales: FloatArray = FloatArray(viewProjections.size) { scaleAlong(viewProjections[it], DEPTH_AXIS) },
    val worldExtents: FloatArray = FloatArray(viewProjections.size) { 2f / scaleAlong(viewProjections[it], WIDTH_AXIS) },
) {
    init {
        require(viewProjections.isNotEmpty()) { "A shadow needs at least one cascade." }
        require(viewProjections.size == splitDistances.size) {
            "Every cascade needs its own split distance: ${viewProjections.size} matrices, " +
                "${splitDistances.size} splits."
        }
        require(viewProjections.size <= MAX_SHADOW_CASCADES) {
            "${viewProjections.size} cascades exceeds the $MAX_SHADOW_CASCADES the uniform block holds."
        }
    }

    /** How many cascades a depth pass actually renders; the rest are padding. */
    val count: Int get() = viewProjections.size

    /** One vec4 per cascade: depth scale in x, world extent in y, the rest padding. */
    fun depthScaleFloats(): FloatArray {
        val field = UniformFields.CascadeDepthScales
        val floats = FloatArray(field.floats)
        for (index in 0 until MAX_SHADOW_CASCADES) {
            val cascade = minOf(index, count - 1)
            field.writeVec4Element(
                destination = floats,
                index = index,
                x = depthScales[cascade],
                y = worldExtents[cascade],
                z = 0f,
                w = 0f,
            )
        }
        return floats
    }

    /** MAX_SHADOW_CASCADES matrices concatenated for the shader uniform block. */
    fun matrixFloats(): FloatArray {
        val field = UniformFields.CascadeViewProjections
        val floats = FloatArray(field.floats)
        for (index in 0 until MAX_SHADOW_CASCADES) {
            field.writeMat4Element(floats, index, viewProjections[minOf(index, count - 1)])
        }
        return floats
    }

    companion object {
        /** A valid payload for a shader whose shadow feature is disabled. */
        val UNSHADOWED: GpuShadowCascadeData = GpuShadowCascadeData(
            viewProjections = listOf(Mat4().apply { data.fill(0f) }),
            splitDistances = floatArrayOf(Float.MAX_VALUE),
            depthScales = floatArrayOf(1f),
            worldExtents = floatArrayOf(1f),
        )

        private const val WIDTH_AXIS = 0
        private const val DEPTH_AXIS = 2
    }
}

private fun scaleAlong(matrix: Mat4, axis: Int): Float {
    val x = matrix.data[axis]
    val y = matrix.data[axis + ROW_STRIDE]
    val z = matrix.data[axis + 2 * ROW_STRIDE]
    return sqrt(x * x + y * y + z * z)
}

private const val ROW_STRIDE = 4
