/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.tonemapping

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable tone mapping component for scene documents.
 *
 * @property exposure What lit radiance is multiplied by before tone mapping, finite and above 0.
 */
@Serializable
@SerialName("tone_mapping")
data class SceneToneMapping(
    val exposure: Float = 1f,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!exposure.isFinite() || exposure <= 0f) {
            add(SceneValidationIssue(path, "tone_mapping.exposure must be finite and > 0; was $exposure"))
        }
    }
}
