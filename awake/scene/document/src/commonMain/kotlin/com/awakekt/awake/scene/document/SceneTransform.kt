/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable

/**
 * Serializable 3D local transform spatial descriptor.
 *
 * @property position Local translation vector.
 * @property rotation Euler angles in radians, applied X, then Y, then Z (`Mat4.setEulerTRS`).
 * The angles are in world axes when [inheritRotation] is false.
 * @property scale Local scaling vector.
 * @property inheritRotation Whether the parent's rotation turns the node's axes. The parent
 * still transforms the node's position, and its world scale is kept along the world axes: each
 * [scale] component is multiplied by how far the parent stretches the matching world axis, so a
 * uniform parent scale is kept whatever the parent's rotation.
 */
@Serializable
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(),
    val rotation: SceneVec3 = SceneVec3(),
    val scale: SceneVec3 = SceneVec3(1f, 1f, 1f),
    val inheritRotation: Boolean = true,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        position: SceneVec3 = SceneVec3(),
        rotation: SceneVec3 = SceneVec3(),
        scale: SceneVec3 = SceneVec3(1f, 1f, 1f),
    ) : this(position, rotation, scale, true)

    /** Copies this transform while preserving its rotation inheritance setting. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        position: SceneVec3 = this.position,
        rotation: SceneVec3 = this.rotation,
        scale: SceneVec3 = this.scale,
    ): SceneTransform = copy(position, rotation, scale, inheritRotation)
}

/**
 * Serializable 3-component floating-point vector.
 *
 * @property x X component.
 * @property y Y component.
 * @property z Z component.
 */
@Serializable
data class SceneVec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)
