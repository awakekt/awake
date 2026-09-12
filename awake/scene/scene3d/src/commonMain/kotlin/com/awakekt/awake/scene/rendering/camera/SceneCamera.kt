/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable camera component.
 *
 * @property eye World-space camera eye position.
 * @property center World-space look-at center point.
 * @property up Camera up vector.
 * @property fovYDegrees Vertical field of view in degrees.
 * @property near Near clipping plane distance.
 * @property far Far clipping plane distance.
 * @property primary Whether this camera acts as the primary viewport camera.
 */
@Serializable
@SerialName("camera")
data class SceneCamera(
    val eye: SceneVec3 = SceneVec3(0f, 0f, 5f),
    val center: SceneVec3 = SceneVec3(0f, 0f, 0f),
    val up: SceneVec3 = SceneVec3(0f, 1f, 0f),
    val fovYDegrees: Float = 60f,
    val near: Float = 0.1f,
    val far: Float = 100f,
    val primary: Boolean = true,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (near <= 0f) {
            add(SceneValidationIssue(path, "camera.near must be > 0"))
        }
        if (far <= near) {
            add(SceneValidationIssue(path, "camera.far must be greater than camera.near"))
        }
        if (fovYDegrees <= 0f || fovYDegrees >= 180f) {
            add(SceneValidationIssue(path, "camera.fovYDegrees must be between 0 and 180"))
        }
    }
}
