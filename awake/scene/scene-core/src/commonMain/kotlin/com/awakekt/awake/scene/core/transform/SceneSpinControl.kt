/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable continuous spin rotation control component.
 *
 * @property radians Initial rotation angle in radians.
 * @property speed Rotation speed multiplier.
 */
@Serializable
@SerialName("spin_control")
data class SceneSpinControl(
    val radians: Float = 0f,
    val speed: Float = 1f,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed < 0f) {
            add(SceneValidationIssue(path, "spinControl.speed must not be negative"))
        }
    }
}
