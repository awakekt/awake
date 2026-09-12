/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh

/**
 * A high-level scene draw call before compilation down to hardware [com.awakekt.awake.render.command.GpuDrawCommand].
 */
data class DrawCall(
    val mesh: Mesh,
    val material: Material,
    val model: Mat4 = Mat4(),
    val extraUniformFloats: FloatArray = FloatArray(0),
    val instanceModels: List<Mat4>? = null,
    val instanceJointPalettes: List<FloatArray>? = null,
    val instanceColors: List<Vec4>? = null,
    val instanceFrames: List<Float>? = null,
    val transparent: Boolean = false,
    val depthSortPoint: Vec3f? = null,
    val timeSeconds: Float = 0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawCall) return false

        if (mesh != other.mesh) return false
        if (material != other.material) return false
        if (model != other.model) return false
        if (!extraUniformFloats.contentEquals(other.extraUniformFloats)) return false
        if (instanceModels != other.instanceModels) return false
        if (instanceJointPalettes != other.instanceJointPalettes) return false
        if (instanceColors != other.instanceColors) return false
        if (instanceFrames != other.instanceFrames) return false
        if (transparent != other.transparent) return false
        if (depthSortPoint != other.depthSortPoint) return false
        if (timeSeconds != other.timeSeconds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mesh.hashCode()
        result = 31 * result + material.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + extraUniformFloats.contentHashCode()
        result = 31 * result + (instanceModels?.hashCode() ?: 0)
        result = 31 * result + (instanceJointPalettes?.hashCode() ?: 0)
        result = 31 * result + (instanceColors?.hashCode() ?: 0)
        result = 31 * result + (instanceFrames?.hashCode() ?: 0)
        result = 31 * result + transparent.hashCode()
        result = 31 * result + (depthSortPoint?.hashCode() ?: 0)
        result = 31 * result + timeSeconds.hashCode()
        return result
    }
}
