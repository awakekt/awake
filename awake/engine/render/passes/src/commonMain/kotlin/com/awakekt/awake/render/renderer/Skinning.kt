/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * Joint-palette slot count for a skinned mesh -- bounds the fixed-size palette arrays WGSL
 * requires (no dynamically-sized uniform arrays). CesiumMan's own skeleton has 19 joints; 64
 * leaves headroom for any other rigged asset without a second shader variant. One constant
 * shared by the shader definitions (`skinned`/`skinned_instanced` array sizes) and both
 * backends' palette buffers, so the WGSL arrays and the CPU-side uploads cannot disagree --
 * same single-source rule as [MAX_POINT_LIGHTS].
 */
const val MAX_JOINTS = 64

/** `skinned.wgsl`'s uniform block -- MVP, the joint palette, the model matrix, the material's
 * base colour and emissive factors and the PBR factors, the canonical declaration its ASL definition
 * derives the WGSL struct from. The model matrix is for the shadow pass, which places the posed mesh
 * in the world and then projects it from the light. The colour factors tint the mesh as a static
 * material's do; of the PBR factors only `z`, the alpha cutoff, is read, where the textured block
 * carries it too. The palette rides in the uniform block (not a storage buffer) because one skin is
 * drawn per call on this path; the instanced variant moves it to a per-instance storage buffer
 * instead. */
object SkinnedFields {
    val JointPalette = UniformField(
        "jointPalette",
        com.awakekt.awake.core.geometry.GpuDataShape.Mat4,
        MAX_JOINTS,
    )
}

val SkinnedUniformLayout = UniformLayout(
    UniformFields.Mvp,
    SkinnedFields.JointPalette,
    UniformFields.Model,
    UniformFields.BaseColorFactor,
    UniformFields.EmissiveFactor,
    UniformFields.PbrFactors,
)

/**
 * A skinned draw's `extraUniformFloats` when its material tints it: the joint palette (padded to
 * [MAX_JOINTS]) and then the factors. A palette alone draws untinted.
 */
val SkinnedMaterialLayout = UniformLayout(SkinnedFields.JointPalette, UniformFields.BaseColorFactor, UniformFields.EmissiveFactor)
