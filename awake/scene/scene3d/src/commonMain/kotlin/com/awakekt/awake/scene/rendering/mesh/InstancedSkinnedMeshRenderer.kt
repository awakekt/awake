/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.rendering.animation.SkinnedPose

/**
 * One [transform] + one joint palette for one instance of an [InstancedSkinnedMeshRenderer]
 * batch -- see [SkinnedPose.jointPalette] for the single-instance equivalent this mirrors.
 * Advancing this instance's own animation clock and writing its sampled pose into
 * [jointPalette] each frame is gameplay/demo code's job, same as [SkinnedPose] today.
 */
data class SkinnedInstance(
    val transform: Mat4,
    val jointPalette: FloatArray,
)

/**
 * [InstancedMeshRenderer]'s animated counterpart -- N independently-posed copies of
 * [mesh]/[material] drawn in one GPU instanced draw call, each [instances] entry supplying its
 * own transform and joint palette (see [RenderDrawCommand.instanceJointPalettes]'s doc comment for the
 * storage-buffer mechanism a backend uses to deliver these). A dedicated component rather than
 * folding animated instances into [InstancedMeshRenderer]: static instancing has no per-instance
 * extra data slot at all, so mixing the two would make every [InstancedMeshRenderer] consumer
 * pay for a joint-palette list it never uses.
 */
data class InstancedSkinnedMeshRenderer(
    val mesh: Mesh,
    val material: Material,
    val instances: List<SkinnedInstance>,
) {
    private var cachedTransforms: List<Mat4>? = null
    private var cachedPalettes: List<FloatArray>? = null
    private var cachedFor: List<SkinnedInstance>? = null

    internal fun transforms(): List<Mat4> {
        val current = instances
        val cached = cachedTransforms
        if (cached != null && cachedFor === current) return cached
        return current.map { it.transform }.also {
            cachedTransforms = it
            cachedFor = current
        }
    }

    internal fun jointPalettes(): List<FloatArray> {
        val current = instances
        val cached = cachedPalettes
        if (cached != null && cachedFor === current) return cached
        return current.map { it.jointPalette }.also {
            cachedPalettes = it
            cachedFor = current
        }
    }
}
