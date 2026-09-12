/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f

/**
 * Fully pre-packed, hardware-ready pass input for the HAL.
 *
 * References ONLY HAL primitives (Mat4, Vec3f, GpuSubPass, GpuDrawCommand, FloatArray).
 * Contains NO SceneLight, Lens, EnvironmentUniforms, or content flags.
 */
data class GpuPassInput(
    /** Any sub-passes executing before the primary scene pass (e.g. depth pre-passes, shadow cascade layers). */
    val prePasses: List<GpuSubPass> = emptyList(),
    /** Combined View-Projection matrix for the primary scene pass. */
    val viewProjection: Mat4,
    /** Camera world position for distance-based calculations / sorting. */
    val cameraEye: Vec3f,
    /** Opaque draws pre-sorted and batched by pipeline/material. */
    val opaqueDraws: List<GpuDrawCommand>,
    /** Transparent draws pre-sorted back-to-front. */
    val transparentDraws: List<GpuDrawCommand>,
    /** Pre-packed uniform bytes (lighting, fog, shadow matrices) as raw floats. */
    val passUniforms: FloatArray,
) {
    companion object {
        val EMPTY = GpuPassInput(
            prePasses = emptyList(),
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 0f, 0f),
            opaqueDraws = emptyList(),
            transparentDraws = emptyList(),
            passUniforms = FloatArray(0),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpuPassInput) return false

        if (prePasses != other.prePasses) return false
        if (viewProjection != other.viewProjection) return false
        if (cameraEye != other.cameraEye) return false
        if (opaqueDraws != other.opaqueDraws) return false
        if (transparentDraws != other.transparentDraws) return false
        if (!passUniforms.contentEquals(other.passUniforms)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prePasses.hashCode()
        result = 31 * result + viewProjection.hashCode()
        result = 31 * result + cameraEye.hashCode()
        result = 31 * result + opaqueDraws.hashCode()
        result = 31 * result + transparentDraws.hashCode()
        result = 31 * result + passUniforms.contentHashCode()
        return result
    }
}
