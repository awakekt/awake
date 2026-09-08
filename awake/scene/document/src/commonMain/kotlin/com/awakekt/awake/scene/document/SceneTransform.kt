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
 * @property rotation Local Euler angles rotation vector in degrees.
 * @property scale Local scaling vector.
 */
@Serializable
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(),
    val rotation: SceneVec3 = SceneVec3(),
    val scale: SceneVec3 = SceneVec3(1f, 1f, 1f),
)

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
