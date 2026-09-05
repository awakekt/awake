/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.animation

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.scene.rendering.RenderSystem
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer

/**
 * Optional add-on for a [MeshRenderer] entity whose mesh uses a GPU-skinned vertex format.
 * [RenderSystem] reads [jointPalette] into that entity's `DrawCall.extraUniformFloats` every frame.
 */
data class SkinnedPose(
    var jointPalette: FloatArray,
) {
    /**
     * Extracts the translation component of the joint at [jointIndex] from the palette.
     * Writes into [out] if provided, or returns a newly allocated [Vec3f].
     */
    fun getJointPosition(jointIndex: Int, out: Vec3f = Vec3f()): Vec3f {
        val offset = jointIndex * 16
        if (offset + 14 < jointPalette.size) {
            out.x = jointPalette[offset + 12]
            out.y = jointPalette[offset + 13]
            out.z = jointPalette[offset + 14]
        }
        return out
    }

    /**
     * Extracts the full 4x4 transform matrix for [jointIndex] into [out].
     */
    fun getJointMatrix(jointIndex: Int, out: Mat4): Mat4 {
        val offset = jointIndex * 16
        if (offset + 16 <= jointPalette.size) {
            jointPalette.copyInto(out.data, destinationOffset = 0, startIndex = offset, endIndex = offset + 16)
        }
        return out
    }
}
