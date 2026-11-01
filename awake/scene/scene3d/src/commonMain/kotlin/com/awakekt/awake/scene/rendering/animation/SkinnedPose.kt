/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer

/**
 * Optional add-on for a [MeshRenderer] entity whose mesh uses a GPU-skinned vertex format.
 * [RenderSystem3D] reads [jointPalette] into that entity's `RenderDrawCommand.extraUniformFloats` every frame.
 */
data class SkinnedPose(
    var jointPalette: FloatArray,
) {
    /** Whether a renderer drew a mesh with this pose since [AnimationSystem] last ran. */
    internal var drawn = false

    /** Whether a renderer culled a mesh with this pose since [AnimationSystem] last ran. */
    internal var culled = false

    /**
     * Distance in world units from the camera eye to this model when a renderer last drew it, written
     * alongside [drawn]. [AnimationSystem] reads it through an [AnimationUpdateRate]; it is 0 for a
     * model no renderer has drawn, as in a headless world.
     */
    internal var cameraDistance = 0f

    /**
     * Seconds until [AnimationSystem] next recomputes this pose when an [AnimationUpdateRate] holds it
     * between poses. 0 recomputes on the next update; unused when the rate poses every frame.
     */
    internal var nextPoseIn = 0f

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
