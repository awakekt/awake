/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable

/**
 * Serializable 2-component floating-point vector for scene documents (e.g. UV scales, 2D planar offsets).
 */
@Serializable
data class SceneVec2(
    val x: Float = 0f,
    val y: Float = 0f,
)

/**
 * Serializable 4-component quaternion descriptor for scene documents.
 */
@Serializable
data class SceneQuat(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 1f,
)
