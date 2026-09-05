/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.mesh

import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.scene.rendering.RenderSystem
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose

/** Optional add-on for a [MeshRenderer] entity -- [localBounds] is that mesh's bounds in its
 * own local (pre-transform) space, typically built from the same `MeshGeometry.vertices`
 * passed to [io.github.awakelab.awake.render.renderer.Renderer.createMesh] via
 * `Aabb.fromPositions`. [RenderSystem]
 * reads it into that entity's [Transform][io.github.awakelab.awake.scene.core.transform.Transform]
 * to frustum-cull the entity when it can't possibly be visible -- an entity with no `MeshBounds`
 * is never culled (always drawn, same as before this component existed), same "system reads
 * whatever's currently set, entity opts in by adding the component" shape [SkinnedPose]/
 * [PbrMaterial] already use. */
data class MeshBounds(
    val localBounds: Aabb,
) {
    private var cachedMatrix: FloatArray? = null
    private var cachedWorldBounds: Aabb? = null

    /**
     * [localBounds] in world space under [worldMatrix], recomputed only when that matrix changes.
     *
     * `Aabb.transformed` transforms eight corners and allocates a box, and both the culling test
     * and the spatial index want the same answer for the same entity in the same frame. Most
     * entities in an open world do not move at all, so the common case becomes sixteen float
     * comparisons rather than a rebuild -- and the per-entity, per-frame allocation this used to
     * cost disappears with it.
     */
    fun worldBounds(worldMatrix: Mat4): Aabb {
        val cached = cachedWorldBounds
        val previous = cachedMatrix
        if (cached != null && previous != null && previous.contentEquals(worldMatrix.data)) return cached
        val bounds = localBounds.transformed(worldMatrix)
        cachedMatrix = worldMatrix.data.copyOf()
        cachedWorldBounds = bounds
        return bounds
    }
}
