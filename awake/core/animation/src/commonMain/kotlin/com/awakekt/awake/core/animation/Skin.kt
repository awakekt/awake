/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.inverse

/**
 * Mesh skin binding definition for GPU skeletal vertex skinning.
 *
 * [joints] is a list of bone indices (into the owning [Skeleton]'s `bones`); [inverseBindMatrices]
 * is the same length, one matrix per joint. Kept as its own type rather than folded into
 * [Skeleton]: one skeleton's node hierarchy can carry more than one skin (different joint
 * subsets/bind poses over the same bones), so a skin is a separate, possibly-repeated view over
 * a skeleton, not a field of it.
 *
 * @property joints List of bone indices within the skeleton that act as skin joints.
 * @property inverseBindMatrices Inverse bind-pose matrices for each joint in [joints], mapping mesh space to local joint space.
 */
data class Skin(
    val joints: List<Int>,
    val inverseBindMatrices: List<Mat4>,
) {
    /** Each joint's bind matrix, the inverse of its inverse bind matrix, or null where that is singular. */
    private val bindMatrices: List<Mat4?> by lazy { inverseBindMatrices.map { it.inverse() } }

    /**
     * Where joint [joint] is in the skinned mesh's space under [palette], one [AnimationPose.jointPalette]
     * wrote for this skin, as opposed to how it moves a vertex.
     *
     * A palette entry is the joint's global transform times its inverse bind matrix, so near the bind
     * pose its translation is about zero. This multiplies the bind matrix back in, giving the global
     * transform: what an attachment on the joint follows. [joint] indexes [joints], the palette's
     * order, not the skeleton's bones.
     *
     * @param palette Joint matrices, 16 column-major floats per joint of this skin.
     * @param joint Index of the joint in [joints].
     * @param out Matrix the transform is written into.
     * @return [out], or null when [joint] is outside [palette] or its inverse bind matrix is singular.
     */
    fun jointTransform(palette: FloatArray, joint: Int, out: Mat4 = Mat4()): Mat4? {
        val base = joint * MATRIX_FLOATS
        val bind = bindMatrices.getOrNull(joint)?.takeIf { base + MATRIX_FLOATS <= palette.size } ?: return null
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += palette[base + k * 4 + row] * bind.data[column * 4 + k]
                out.data[column * 4 + row] = sum
            }
        }
        return out
    }
}

private const val MATRIX_FLOATS = 16
