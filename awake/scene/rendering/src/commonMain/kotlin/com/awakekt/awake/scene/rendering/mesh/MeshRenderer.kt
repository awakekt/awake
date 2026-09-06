/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.CullMode

/** [cullMode] defaults to [CullMode.None] -- exactly this mesh's behavior before per-mesh
 * culling existed, so adding this field changes nothing for an existing scene until an author
 * opts in. See [CullMode]'s own doc comment for when [CullMode.Back] is the right choice (a
 * solid, correctly-wound opaque mesh). */
data class MeshRenderer(
    val mesh: Mesh,
    val material: Material,
    val cullMode: CullMode = CullMode.None,
    /** xyz is a shader-defined vertex-effect parameter triplet; zero disables the effect. */
    val vertexAnimation: Vec3f = Vec3f.ZERO,
    /**
     * Draw this mesh in the transparent pass -- alpha-blended, depth-tested but not
     * depth-written, sorted back-to-front against the camera.
     *
     * Per-entity, the same way [cullMode] is, and explicit rather than inferred from a material's
     * alpha: a material can be opaque and still want blending, and one can carry an alpha it
     * never uses. See `DrawCall.transparent`.
     */
    val transparent: Boolean = false,
    /** Skips this entity's draw call when off, without removing the component -- an editor's
     * visibility toggle flips this rather than adding/removing [MeshRenderer] itself. */
    val visible: Boolean = true,
)
