/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Poolable
import kotlin.math.sqrt

/**
 * Core 3D transformation component defining position, Euler rotation, scale, and hierarchy.
 *
 * @property position Local position vector.
 * @property rotation Euler rotation angles in radians, in world axes when [inheritRotation] is false.
 * @property scale Local scaling factor vector.
 * @property parent Optional parent [Entity] in the scene hierarchy.
 * @property worldMatrix Computed world transformation matrix cached by [TransformSystem].
 * @property inheritRotation Whether the parent turns this transform's axes. Position still
 * follows the full parent transform. When false, [rotation] is in world axes and the parent's
 * world scale is kept along the world axes, not its own: each [scale] component is multiplied by
 * how far the parent's world matrix stretches the matching world axis (the length of that row of
 * its basis, the extent of its scaled unit sphere along X, Y or Z). A parent scaled (2, 3, 4) and
 * turned a quarter about Z stretches world X by 3 and world Y by 2, so this transform's X is scaled
 * by 3 and its Y by 2. A uniform parent scale is kept unchanged whatever the parent's rotation, and
 * a parent's mirroring is dropped. [computeWorldMatrix] is the composition.
 */
data class Transform(
    var position: Vec3f = Vec3f(0f, 0f, 0f),
    var rotation: Vec3f = Vec3f(0f, 0f, 0f),
    var scale: Vec3f = Vec3f(1f, 1f, 1f),
    var parent: Entity? = null,
    var worldMatrix: Mat4 = Mat4(),
    var inheritRotation: Boolean = true,
) : Poolable {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        position: Vec3f = Vec3f(0f, 0f, 0f),
        rotation: Vec3f = Vec3f(0f, 0f, 0f),
        scale: Vec3f = Vec3f(1f, 1f, 1f),
        parent: Entity? = null,
        worldMatrix: Mat4 = Mat4(),
    ) : this(position, rotation, scale, parent, worldMatrix, true)

    /** Copies this transform while preserving its rotation inheritance setting. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        position: Vec3f = this.position,
        rotation: Vec3f = this.rotation,
        scale: Vec3f = this.scale,
        parent: Entity? = this.parent,
        worldMatrix: Mat4 = this.worldMatrix,
    ): Transform = copy(position, rotation, scale, parent, worldMatrix, inheritRotation)

    /**
     * How many times [TransformSystem] has rebuilt [worldMatrix]. Unchanged means the matrix is too,
     * so a consumer can key a cache on it.
     */
    var worldVersion: Int = 0
        internal set

    /** Set by [reset], so a pooled transform is rebuilt even when its values match its last use. */
    internal var rebuildRequested: Boolean = true

    /**
     * Computes the local transform matrix into [target] with zero allocations.
     *
     * @param target The target [Mat4] matrix to write the computed local transformation into.
     * @return The [target] matrix containing the computed local transformation.
     */
    fun computeLocalMatrix(target: Mat4 = worldMatrix): Mat4 {
        target.setEulerTRS(
            position.x, position.y, position.z,
            rotation.x, rotation.y, rotation.z,
            scale.x, scale.y, scale.z,
        )
        return target
    }

    /**
     * Computes this transform's world matrix under [parentWorld] into [target] with zero
     * allocations, as [TransformSystem] builds [worldMatrix]: the parent's world matrix times the
     * local matrix, or, when [inheritRotation] is false, the position carried through the parent
     * and the rotation and scale kept in world axes as [inheritRotation] describes.
     *
     * @param parentWorld The parent's world matrix, or null for a transform with no parent.
     * @param target The matrix to write into; it must be neither [parentWorld] nor [scratch].
     * @param scratch Holds the local matrix of a transform that inherits rotation.
     * @return The [target] matrix containing the computed world transformation.
     */
    fun computeWorldMatrix(parentWorld: Mat4?, target: Mat4, scratch: Mat4): Mat4 = when {
        parentWorld == null -> computeLocalMatrix(target)
        inheritRotation -> Mat4.multiplyInPlace(computeLocalMatrix(scratch), parentWorld, target)
        else -> computeWorldAlignedMatrix(parentWorld, target)
    }

    private fun computeWorldAlignedMatrix(parentWorld: Mat4, target: Mat4): Mat4 {
        val (x, y, z) = position
        return target.setEulerTRS(
            parentWorld.m00 * x + parentWorld.m01 * y + parentWorld.m02 * z + parentWorld.m03,
            parentWorld.m10 * x + parentWorld.m11 * y + parentWorld.m12 * z + parentWorld.m13,
            parentWorld.m20 * x + parentWorld.m21 * y + parentWorld.m22 * z + parentWorld.m23,
            rotation.x, rotation.y, rotation.z,
            scale.x * worldAxisStretch(parentWorld, 0),
            scale.y * worldAxisStretch(parentWorld, 1),
            scale.z * worldAxisStretch(parentWorld, 2),
        )
    }

    /**
     * Computes and returns a newly allocated local matrix.
     *
     * Prefer [computeLocalMatrix] in hot loops to avoid per-frame allocations.
     *
     * @return A fresh [Mat4] instance containing this transform's local matrix.
     */
    fun localMatrix(): Mat4 = computeLocalMatrix(Mat4())

    override fun reset() {
        position.x = 0f
        position.y = 0f
        position.z = 0f
        rotation.x = 0f
        rotation.y = 0f
        rotation.z = 0f
        scale.x = 1f
        scale.y = 1f
        scale.z = 1f
        parent = null
        inheritRotation = true
        worldMatrix.identity()
        rebuildRequested = true
    }
}

/**
 * How far [matrix] stretches world axis [axis] (0 for X, 1 for Y, 2 for Z): the length of that row
 * of its 3x3 basis, which is the extent along the axis of the unit sphere [matrix] maps. It is the
 * world scale a transform with [Transform.inheritRotation] false keeps from its parent.
 *
 * @param matrix A world matrix.
 * @param axis The world axis, 0, 1 or 2.
 * @return The stretch, never negative.
 */
fun worldAxisStretch(matrix: Mat4, axis: Int): Float = when (axis) {
    0 -> length(matrix.m00, matrix.m01, matrix.m02)
    1 -> length(matrix.m10, matrix.m11, matrix.m12)
    2 -> length(matrix.m20, matrix.m21, matrix.m22)
    else -> throw IllegalArgumentException("World axis must be 0, 1 or 2, was $axis")
}

private fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
