// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.scene.rendering.components

import io.github.ronjunevaldoz.awake.render.material.Material
import io.github.ronjunevaldoz.awake.render.mesh.Mesh
import io.github.ronjunevaldoz.awake.render.renderer.CullMode

/** [cullMode] defaults to [CullMode.None] -- exactly this mesh's behavior before per-mesh
 * culling existed, so adding this field changes nothing for an existing scene until an author
 * opts in. See [CullMode]'s own doc comment for when [CullMode.Back] is the right choice (a
 * solid, correctly-wound opaque mesh). */
data class MeshRenderer(
    val mesh: Mesh,
    val material: Material,
    val cullMode: CullMode = CullMode.None,
    /**
     * Draw this mesh in the transparent pass -- alpha-blended, depth-tested but not
     * depth-written, sorted back-to-front against the camera.
     *
     * Per-entity, the same way [cullMode] is, and explicit rather than inferred from a material's
     * alpha: a material can be opaque and still want blending, and one can carry an alpha it
     * never uses. See `DrawCall.transparent`.
     */
    val transparent: Boolean = false,
)
