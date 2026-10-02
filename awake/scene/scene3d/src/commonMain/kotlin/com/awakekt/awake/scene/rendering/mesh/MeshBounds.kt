/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlin.math.abs

/** Optional add-on for a [MeshRenderer] entity -- [localBounds] is that mesh's bounds in its
 * own local (pre-transform) space, typically built from the same `MeshGeometry.vertices`
 * passed to [com.awakekt.awake.render.renderer.Renderer.createMesh] via
 * `Aabb.fromPositions`. [RenderSystem3D]
 * reads it into that entity's [Transform][com.awakekt.awake.scene.core.transform.Transform]
 * to frustum-cull the entity when it can't possibly be visible -- an entity with no `MeshBounds`
 * is never culled (always drawn, same as before this component existed), same "system reads
 * whatever's currently set, entity opts in by adding the component" shape [SkinnedPose]/
 * [PbrMaterial] already use. */
data class MeshBounds(
    val localBounds: Aabb,
) {
    // The world box as floats. A moving entity rebuilds it every frame, and a box object stored
    // here instead would be a fresh young object hung off a long-lived one, which every young
    // collection then has to find and copy: at tens of thousands of moving entities, most of the
    // garbage collector's pause.
    private var minX = 0f
    private var minY = 0f
    private var minZ = 0f
    private var maxX = 0f
    private var maxY = 0f
    private var maxZ = 0f

    /** The box object for the floats above, kept only once the entity has held still. */
    private var cachedBox: Aabb? = null
    private var heldStill = false
    private var cachedFor: Transform? = null
    private var cachedVersion = 0
    private var cachedMatrix: FloatArray? = null

    /** Whether [cachedMatrix] is the matrix the floats were built from. */
    private var matrixCached = false

    /**
     * [localBounds] under [transform]'s world matrix, recomputed only when [TransformSystem] has
     * rebuilt that matrix: one integer comparison for an entity that has not moved. A transform
     * the system has never built falls back to comparing the matrix.
     *
     * The same box comes back for as long as the entity holds still. A moving entity gets a new
     * one each time it moved, which this does not keep.
     */
    fun worldBounds(transform: Transform): Aabb {
        refresh(transform)
        return box()
    }

    /**
     * Whether [transform]'s world box is at least partly inside [planes], without building a box
     * object: the culling test every renderable entity takes every frame.
     */
    fun intersects(transform: Transform, planes: List<Plane>): Boolean {
        refresh(transform)
        return planes.intersects(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * [localBounds] in world space under [worldMatrix], recomputed only when that matrix changes.
     *
     * Both the culling test and the spatial index want the same answer for the same entity in the
     * same frame, and most entities in an open world do not move at all, so the common case is
     * sixteen float comparisons rather than a rebuild.
     */
    fun worldBounds(worldMatrix: Mat4): Aabb {
        refreshFromMatrix(worldMatrix)
        // A box for a matrix says nothing about which transform's version it belongs to.
        cachedFor = null
        return box()
    }

    private fun refresh(transform: Transform) {
        val version = transform.worldVersion
        if (version == 0) {
            refreshFromMatrix(transform.worldMatrix)
            cachedFor = transform
            cachedVersion = 0
            return
        }
        if (cachedFor === transform && cachedVersion == version) {
            heldStill = true
            return
        }
        // The version already says the matrix changed, so comparing and copying it would be
        // sixteen floats of each per moving entity per frame for nothing.
        heldStill = cachedFor == null
        build(transform.worldMatrix)
        matrixCached = false
        cachedFor = transform
        cachedVersion = version
    }

    private fun refreshFromMatrix(worldMatrix: Mat4) {
        val previous = cachedMatrix
        if (matrixCached && previous != null && previous.contentEquals(worldMatrix.data)) {
            heldStill = true
            return
        }
        build(worldMatrix)
        // Copied into the array this entity already owns rather than a fresh copy per change.
        cachedMatrix = previous?.takeIf { it.size == worldMatrix.data.size }
            ?.also { worldMatrix.data.copyInto(it) }
            ?: worldMatrix.data.copyOf()
        matrixCached = true
        heldStill = true
    }

    /** [Aabb.transformed]'s centre and half-extents method, into the fields rather than a box. */
    private fun build(matrix: Mat4) {
        val local = localBounds
        val cx = (local.min.x + local.max.x) * HALF
        val cy = (local.min.y + local.max.y) * HALF
        val cz = (local.min.z + local.max.z) * HALF
        val ex = (local.max.x - local.min.x) * HALF
        val ey = (local.max.y - local.min.y) * HALF
        val ez = (local.max.z - local.min.z) * HALF
        val centerX = matrix.m00 * cx + matrix.m01 * cy + matrix.m02 * cz + matrix.m03
        val centerY = matrix.m10 * cx + matrix.m11 * cy + matrix.m12 * cz + matrix.m13
        val centerZ = matrix.m20 * cx + matrix.m21 * cy + matrix.m22 * cz + matrix.m23
        val extentX = abs(matrix.m00) * ex + abs(matrix.m01) * ey + abs(matrix.m02) * ez
        val extentY = abs(matrix.m10) * ex + abs(matrix.m11) * ey + abs(matrix.m12) * ez
        val extentZ = abs(matrix.m20) * ex + abs(matrix.m21) * ey + abs(matrix.m22) * ez
        minX = centerX - extentX
        minY = centerY - extentY
        minZ = centerZ - extentZ
        maxX = centerX + extentX
        maxY = centerY + extentY
        maxZ = centerZ + extentZ
        cachedBox = null
    }

    private fun box(): Aabb = cachedBox
        ?: Aabb(Vec3f(minX, minY, minZ), Vec3f(maxX, maxY, maxZ)).also { if (heldStill) cachedBox = it }

    private companion object {
        const val HALF = 0.5f
    }
}
