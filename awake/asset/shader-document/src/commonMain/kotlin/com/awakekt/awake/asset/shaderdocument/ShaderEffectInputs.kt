/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.core.math.Mat4

/**
 * What a running shader document reads each frame that the engine does not supply: its clock, where its
 * plane is, its parameter values, and whether to draw at all.
 *
 * Whoever drives the effect, such as a scene system, writes these between frames, and the effect's
 * content feature copies them into its uniform block when it records. Make one with
 * [CompiledShaderDocument.newInputs], which also fills in the parameters' defaults.
 */
class ShaderEffectInputs internal constructor(parameterCount: Int) {
    /** Seconds since the effect started: the document's `time` input. */
    var timeSeconds: Float = 0f

    /** Seconds since the previous frame: the document's `deltaTime` input. */
    var deltaSeconds: Float = 0f

    /** Whether the effect draws. */
    var visible: Boolean = true

    /** Where a plane is, as a column-major model matrix. Identity until [setModel] is called. */
    val model: FloatArray = FloatArray(MAT4_FLOATS).also { for (i in 0 until MAT4_FLOATS step IDENTITY_STRIDE) it[i] = 1f }

    /**
     * The parameter values in declaration order, four floats each, unused components zero. Write them
     * with [CompiledShaderDocument.packParameters], which checks names and sizes.
     */
    val parameters: FloatArray = FloatArray(parameterCount * FLOATS_PER_PARAMETER)

    /** Copies [matrix] into [model], allocating nothing. */
    fun setModel(matrix: Mat4) {
        matrix.data.copyInto(model)
    }
}

/** Floats in one parameter's slot: a `vec4`, whatever the parameter's type. */
internal const val FLOATS_PER_PARAMETER = 4

private const val MAT4_FLOATS = 16

/** The diagonal of a column-major 4 x 4 matrix is every fifth float. */
private const val IDENTITY_STRIDE = 5
