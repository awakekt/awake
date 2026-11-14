/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.CullMode

/** [cullMode] defaults to [CullMode.None] -- exactly this mesh's behavior before per-mesh
 * culling existed, so adding this field changes nothing for an existing scene until an author
 * opts in. See [CullMode]'s own doc comment for when [CullMode.Back] is the right choice (a
 * solid, correctly-wound opaque mesh). */
data class MeshRenderer(
    /** The 3D mesh geometry to draw. */
    val mesh: Mesh,
    /** The material applied when rendering [mesh]. */
    val material: Material,
    /** Face culling mode applied to this mesh during rendering. */
    val cullMode: CullMode = CullMode.None,
    /** xyz is a shader-defined vertex-effect parameter triplet; zero disables the effect. */
    val vertexAnimation: Vec3f = Vec3f.ZERO,
    /**
     * Draw this mesh in the transparent pass -- alpha-blended, depth-tested but not
     * depth-written, sorted back-to-front against the camera.
     *
     * Per-entity, the same way [cullMode] is, and explicit rather than inferred from a material's
     * alpha: a material can be opaque and still want blending, and one can carry an alpha it
     * never uses. See `RenderDrawCommand.transparent`.
     */
    val transparent: Boolean = false,
    /** Skips this entity's draw call when off, without removing the component -- an editor's
     * visibility toggle flips this rather than adding/removing [MeshRenderer] itself. */
    val visible: Boolean = true,
    /** With [transparent], adds its colour to what is behind it rather than covering it -- glows,
     * fire and light shafts, which only brighten. A textured mesh then draws unlit, adding its base
     * and emissive colour only, so its black texels add nothing, unless its [PbrMaterial.litWhenAdditive]
     * keeps it lit. See `RenderDrawCommand.additive`. */
    val additive: Boolean = false,
    /**
     * Faces the camera: drawn at this entity's position and scale, turned so its local +Z points
     * back at the eye. Its own rotation is ignored. A billboard is never culled, because its
     * bounds turn with the camera.
     */
    val billboard: Boolean = false,
    /**
     * The factors [material] was authored with (an imported glTF material's), drawn when the entity
     * has no [PbrMaterial] of its own. Comes from the asset, so it is not saved with the scene.
     */
    val defaultMaterial: PbrMaterial? = null,
    /**
     * Draws this mesh into the shadow maps, in view or off screen. Off for what should not shade
     * the scene: a decal, a ground card, a small prop, distant filler. The mesh itself still draws
     * and still receives shadows. A [transparent] mesh never casts, whatever this says.
     */
    val castsShadows: Boolean = true,
    /**
     * Past this distance from the camera eye to the entity's position, the mesh draws nowhere: not
     * in the scene and not in the shadow maps. `0`, the default, draws it at any distance. Small
     * clutter takes a short distance and a landmark a long one, within one streamed cell.
     */
    val maxDrawDistance: Float = 0f,
) {
    /** [MeshRenderer] as it was before [maxDrawDistance]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        mesh: Mesh,
        material: Material,
        cullMode: CullMode = CullMode.None,
        vertexAnimation: Vec3f = Vec3f.ZERO,
        transparent: Boolean = false,
        visible: Boolean = true,
        additive: Boolean = false,
        billboard: Boolean = false,
        defaultMaterial: PbrMaterial? = null,
        castsShadows: Boolean = true,
    ) : this(mesh, material, cullMode, vertexAnimation, transparent, visible, additive, billboard, defaultMaterial, castsShadows, 0f)

    /** [copy] as it was before [maxDrawDistance]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        mesh: Mesh = this.mesh,
        material: Material = this.material,
        cullMode: CullMode = this.cullMode,
        vertexAnimation: Vec3f = this.vertexAnimation,
        transparent: Boolean = this.transparent,
        visible: Boolean = this.visible,
        additive: Boolean = this.additive,
        billboard: Boolean = this.billboard,
        defaultMaterial: PbrMaterial? = this.defaultMaterial,
        castsShadows: Boolean = this.castsShadows,
    ): MeshRenderer = MeshRenderer(
        mesh, material, cullMode, vertexAnimation, transparent, visible, additive, billboard, defaultMaterial, castsShadows, maxDrawDistance,
    )
}

/** Whether [camera]'s eye is farther than [MeshRenderer.maxDrawDistance] from [transform]'s position; never with no limit. */
internal fun MeshRenderer.beyondDrawDistance(camera: com.awakekt.awake.scene.rendering.camera.Camera, transform: com.awakekt.awake.scene.core.transform.Transform): Boolean {
    if (maxDrawDistance <= 0f) return false
    val eye = camera.lens.eye
    val world = transform.worldMatrix
    val dx = world.m03 - eye.x
    val dy = world.m13 - eye.y
    val dz = world.m23 - eye.z
    return dx * dx + dy * dy + dz * dz > maxDrawDistance * maxDrawDistance
}
