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
    /** View-projection matrices for each active shadow cascade. */
    val viewProjections: List<Mat4>,
    /** Far split distances in view space for each cascade. */
    val splitDistances: FloatArray,
    /** Normalization scale along the depth axis for each cascade. */
    val depthScales: FloatArray = FloatArray(viewProjections.size) { scaleAlong(viewProjections[it], DEPTH_AXIS) },
    /** World-space width extents for texel size calculations across cascades. */
    val worldExtents: FloatArray = FloatArray(viewProjections.size) { 2f / scaleAlong(viewProjections[it], WIDTH_AXIS) },
    /** Distance at which transition blending begins between adjacent cascades. */
    val blendStartDistances: FloatArray,
) {
    /** Retains the original constructor and defaults for source and binary compatibility. */
    constructor(
        viewProjections: List<Mat4>,
        splitDistances: FloatArray,
        depthScales: FloatArray = FloatArray(viewProjections.size) { scaleAlong(viewProjections[it], DEPTH_AXIS) },
        worldExtents: FloatArray = FloatArray(viewProjections.size) { 2f / scaleAlong(viewProjections[it], WIDTH_AXIS) },
    ) : this(viewProjections, splitDistances, depthScales, worldExtents, splitDistances.copyOf())

    /**
     * The share of the last cascade's reach, from 0 to under 1, over which a shadow fades out, so it
     * does not end on a hard line at the shadow distance. 0 keeps the hard end. Set once by whoever
     * fits the cascades, before this is handed to a backend.
     */
    var shadowFade: Float = 0f

    /**
     * [count] and [shadowFade] as the one float a shader reads from `cameraForward.w`: the whole part
     * is the cascade count and the fractional part the fade, capped below 1 so it never carries into it.
     */
    val countAndFade: Float get() = count.toFloat() + shadowFade.coerceIn(0f, MAX_FADE)

    init {
        require(viewProjections.isNotEmpty()) { "A shadow needs at least one cascade." }
        require(viewProjections.size == splitDistances.size) {
            "Every cascade needs its own split distance: ${viewProjections.size} matrices, " +
                "${splitDistances.size} splits."
        }
        require(viewProjections.size <= MAX_SHADOW_CASCADES) {
            "${viewProjections.size} cascades exceeds the $MAX_SHADOW_CASCADES the uniform block holds."
        }
        require(blendStartDistances.size == splitDistances.size) {
            "Every cascade needs its own blend start: ${splitDistances.size} splits, " +
                "${blendStartDistances.size} blend starts."
        }
    }

    /** How many cascades a depth pass actually renders; the rest are padding. */
    val count: Int get() = viewProjections.size

    /** One vec4 per cascade: depth scale in x, world extent in y, split far in z, blend start in w. */
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
                z = splitDistances[cascade],
                w = blendStartDistances[cascade],
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

    /** Predefined shadow cascade payloads. */
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
private const val MAX_FADE = 0.99f
